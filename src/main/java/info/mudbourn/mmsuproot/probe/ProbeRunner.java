package info.mudbourn.mmsuproot.probe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev-only: with {@code -Duproot.probe} the client runs every registered {@link Probe} once the test world has been joined
 * (see the {@code uprootProbe} Gradle task), one probe at a time, one step per client tick. Each result is one line
 * {@code PROBE PASS|FAIL|SKIP <name>: <detail>}, logged and appended to {@code run/mms-uproot/probe.txt}; the file starts with
 * {@code PROBE PLAN <n>} and ends with {@code PROBE DONE ...}, so a run that crashed is a file without that last line. Then the
 * client quits. Without the property this does nothing.
 */
public final class ProbeRunner implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot-probe");
	/** Ticks the player is in the world before the first probe, so chunks, entities and the first frames are settled. */
	private static final int SETTLE_TICKS = 100;
	/** The whole run gives up after this long, whatever the probes are doing. */
	private static final long WALL_LIMIT_MS = 4 * 60_000L;

	private final List<Probe> probes = new ArrayList<>();
	private final Probe.Ctx[] ctx = {null};
	private Path file;
	private int index = -1;
	private int probeTick;
	private int worldTicks;
	private int pass, fail, skip;
	private long startedAt;
	private boolean finished;

	@Override
	public void onInitializeClient() {
		if (System.getProperty("uproot.probe") == null) return;
		file = FabricLoader.getInstance().getGameDir().resolve("mms-uproot").resolve("probe.txt");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, "", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		probes.addAll(KeyProbes.all());
		probes.addAll(ShaderProbes.all());
		probes.addAll(EnchantProbes.all());
		probes.addAll(HwrProbes.all());
		probes.addAll(OwoProbes.all());
		probes.addAll(LootProbes.all());
		probes.addAll(ModProbes.all());
		probes.addAll(FrostifulProbes.all());
		append("PROBE PLAN " + probes.size());
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft mc) {
		if (finished || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
		// an unfocused window would open the pause menu over the probes
		mc.options.pauseOnLostFocus = false;
		if (startedAt == 0) startedAt = System.currentTimeMillis();
		if (index < 0) {
			if (mc.gui.screen() != null) mc.gui.setScreen(null);
			if (++worldTicks < SETTLE_TICKS) return;
			next(mc);
			return;
		}
		if (System.currentTimeMillis() - startedAt > WALL_LIMIT_MS) {
			for (int i = index; i < probes.size(); i++) {
				record("FAIL", probes.get(i).name(), "not run: the probe run hit its " + WALL_LIMIT_MS / 1000 + " s limit");
			}
			finish(mc);
			return;
		}
		Probe probe = probes.get(index);
		probeTick++;
		try {
			String detail = probe.body().step(ctx[0], probeTick);
			if (detail != null) {
				record("PASS", probe.name(), detail);
				next(mc);
			} else if (probeTick >= probe.timeoutTicks()) {
				record("FAIL", probe.name(), "timed out after " + probe.timeoutTicks() + " ticks");
				next(mc);
			}
		} catch (Probe.Skip s) {
			record("SKIP", probe.name(), s.getMessage());
			next(mc);
		} catch (Probe.Fail f) {
			record("FAIL", probe.name(), f.getMessage());
			next(mc);
		} catch (Throwable t) {
			record("FAIL", probe.name(), describe(t));
			next(mc);
		}
	}

	/** Moves on to the next probe, closing any screen the last one left open. */
	private void next(Minecraft mc) {
		try {
			if (mc.gui.screen() != null) mc.gui.setScreen(null);
		} catch (Throwable t) {
			LOGGER.warn("could not close the screen between probes", t);
		}
		index++;
		probeTick = 0;
		ctx[0] = new Probe.Ctx();
		if (index >= probes.size()) finish(mc);
	}

	private void finish(Minecraft mc) {
		finished = true;
		append("PROBE DONE total=" + (pass + fail + skip) + " pass=" + pass + " fail=" + fail + " skip=" + skip);
		LOGGER.info("probe run done: {} pass, {} fail, {} skip", pass, fail, skip);
		mc.stop();
	}

	private void record(String result, String name, String detail) {
		switch (result) {
			case "PASS" -> pass++;
			case "FAIL" -> fail++;
			default -> skip++;
		}
		String line = "PROBE " + result + " " + name + ": " + detail.replace('\n', ' ');
		LOGGER.info(line);
		append(line);
	}

	private static String describe(Throwable t) {
		StringBuilder sb = new StringBuilder(t.toString());
		StackTraceElement[] trace = t.getStackTrace();
		for (int i = 0; i < Math.min(4, trace.length); i++) sb.append(" at ").append(trace[i]);
		if (t.getCause() != null) sb.append(" caused by ").append(t.getCause());
		return sb.toString();
	}

	private void append(String line) {
		try {
			Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			LOGGER.error("cannot write {}", file, e);
		}
	}
}
