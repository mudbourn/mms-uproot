package info.mudbourn.mmsuproot.probe;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * One dev-only check run by {@link ProbeRunner}. {@link Body#step} runs once per client tick until it returns the detail line
 * (a pass); it throws {@link Fail} for a failure, {@link Skip} for a skip, and any other exception is a failure with the
 * exception as its detail. A probe that has not finished after {@code timeoutTicks} ticks fails.
 */
public record Probe(String name, int timeoutTicks, Body body) {
	/** One step of a probe: null while it still waits for something, else the detail of a pass. */
	@FunctionalInterface
	public interface Body {
		String step(Ctx c, int tick) throws Exception;
	}

	/** A probe that decides in a single tick. */
	@FunctionalInterface
	public interface Once {
		String run(Ctx c) throws Exception;
	}

	public static final class Skip extends RuntimeException {
		public Skip(String reason) {
			super(reason);
		}
	}

	public static final class Fail extends RuntimeException {
		public Fail(String detail) {
			super(detail);
		}
	}

	/** What a probe sees: the game, its integrated server, and a scratch map that lives as long as the probe. */
	public static final class Ctx {
		public final Minecraft mc = Minecraft.getInstance();
		public final Map<String, Object> state = new HashMap<>();

		public MinecraftServer server() {
			MinecraftServer server = mc.getSingleplayerServer();
			if (server == null) throw new Fail("no integrated server");
			return server;
		}

		public ServerPlayer serverPlayer() {
			return onServer(() -> {
				if (server().getPlayerList().getPlayers().isEmpty()) throw new Fail("no server player");
				return server().getPlayerList().getPlayers().get(0);
			});
		}

		/** Runs on the server thread and waits for the result (the integrated server needs nothing from the client thread to do so). */
		public <T> T onServer(Callable<T> task) {
			MinecraftServer server = server();
			Supplier<T> supplier = () -> {
				try {
					return task.call();
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			};
			CompletableFuture<T> future = server.submit(supplier);
			try {
				return future.get(20, TimeUnit.SECONDS);
			} catch (java.util.concurrent.ExecutionException e) {
				Throwable cause = e.getCause();
				if (cause instanceof RuntimeException r) throw r;
				throw new IllegalStateException(cause);
			} catch (java.util.concurrent.TimeoutException | InterruptedException e) {
				throw new Fail("server task did not finish: " + e);
			}
		}

		public void onServer(Runnable task) {
			onServer(() -> {
				task.run();
				return null;
			});
		}
	}

	public static Probe once(String name, Once once) {
		return new Probe(name, 100, (c, t) -> once.run(c));
	}

	public static Probe steps(String name, int timeoutTicks, Body body) {
		return new Probe(name, timeoutTicks, body);
	}

	/** A check that needs a person to look at the screen; it records what to look at. */
	public static Probe eyes(String name, String look) {
		return new Probe(name, 1, (c, t) -> {
			throw new Skip("needs-eyes: " + look);
		});
	}
}
