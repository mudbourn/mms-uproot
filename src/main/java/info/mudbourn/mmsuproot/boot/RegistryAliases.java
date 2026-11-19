package info.mudbourn.mmsuproot.boot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import net.fabricmc.fabric.api.event.registry.FabricRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies {@code uproot/aliases.txt}: an old mod id resolves to a vanilla entry 26.3 now has. An alias is only added when the mod that
 * owned the old id is loaded, the old id is free (the mod registers under another id) and the new id exists.
 */
final class RegistryAliases {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms_uproot");

	private RegistryAliases() { }

	static void register() {
		try (InputStream in = RegistryAliases.class.getResourceAsStream("/uproot/aliases.txt")) {
			if (in == null) return;
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			for (String line; (line = reader.readLine()) != null; ) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] p = line.split("\\s+");
				if (p.length != 3) {
					LOGGER.warn("Bad alias line: {}", line);
					continue;
				}
				Registry<?> registry = switch (p[0]) {
					case "block" -> BuiltInRegistries.BLOCK;
					case "item" -> BuiltInRegistries.ITEM;
					default -> null;
				};
				Identifier from = Identifier.parse(p[1]);
				Identifier to = Identifier.parse(p[2]);
				if (registry == null || !FabricLoader.getInstance().isModLoaded(from.getNamespace())) continue;
				if (registry.containsKey(from) || !registry.containsKey(to)) {
					LOGGER.warn("Not aliasing {} {} to {}: the old id is still registered or the new one is missing", p[0], from, to);
					continue;
				}
				((FabricRegistry) registry).addAlias(from, to);
			}
		} catch (IOException e) {
			LOGGER.warn("Could not read the registry aliases", e);
		}
	}
}
