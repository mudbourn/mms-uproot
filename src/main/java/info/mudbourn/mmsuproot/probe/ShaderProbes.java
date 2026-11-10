package info.mudbourn.mmsuproot.probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import info.mudbourn.mmsuproot.shim.client.LegacyShaders;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/** owo's pipelines and the 26.2 -> 26.3 GLSL upgrade of {@link LegacyShaders}. */
final class ShaderProbes {
	private ShaderProbes() { }

	/** A shader as a 26.2 mod wrote it: old version, no locations, a moj_import and a hand copy of a shared block. */
	private static final String OLD = """
		#version 150

		#moj_import <minecraft:dynamictransforms.glsl>

		layout(std140) uniform DynamicTransforms {
		    mat4 ModelViewMat;
		    vec4 ColorModulator;
		};

		in vec4 vertexColor;
		flat in int id;
		out vec4 fragColor;

		void main() {
		    vec4 inner;
		    fragColor = vertexColor;
		}
		""";

	static List<Probe> all() {
		return List.of(
			Probe.once("shaders.legacy-upgrade-sample", ShaderProbes::upgradeSample),
			Probe.once("shaders.legacy-upgrade-owo-sources", ShaderProbes::upgradeOwo),
			Probe.once("shaders.vanilla-untouched", ShaderProbes::vanillaUntouched),
			Probe.once("shaders.owo-pipelines-compiled", ShaderProbes::owoPipelines),
			Probe.once("shaders.no-shader-errors-in-log", ShaderProbes::logClean),
			Probe.eyes("shaders.owo-blur-look", "open an owo screen that blurs its background (Mod Menu list, a config screen) and confirm the blur is smooth, not banded or black"),
			Probe.eyes("shaders.owo-spectrum-look", "open an owo colour picker and confirm the hue/saturation gradient shows"));
	}

	private static String upgradeSample(Probe.Ctx c) {
		String out = LegacyShaders.shader(OLD);
		need(out.contains("#version 330\n#extension GL_ARB_separate_shader_objects : require"), "version 330 with separate shader objects", out);
		need(!out.contains("#moj_import") && out.contains("#include <minecraft:dynamictransforms.glsl>"), "#moj_import became #include", out);
		need(out.contains("layout(location = 0) in vec4 vertexColor;"), "first input got location 0", out);
		need(out.contains("layout(location = 1) flat in int id;"), "second input got location 1 and kept flat", out);
		need(out.contains("layout(location = 0) out vec4 fragColor;"), "output got location 0", out);
		need(!out.contains("layout(std140) uniform DynamicTransforms"), "the hand copy of DynamicTransforms was replaced by the include", out);
		need(out.contains("    vec4 inner;"), "a local inside main() was not given a location", out);
		need(LegacyShaders.shader(out).equals(out), "upgrading an upgraded source changes nothing", out);
		return "26.2-style sample: #version 330 + extension, #include, locations 0/1 in, 0 out, DynamicTransforms from include, idempotent";
	}

	private static String read(Probe.Ctx c, String id) throws Exception {
		Resource r = c.mc.getResourceManager().getResource(Identifier.parse(id)).orElseThrow(() -> new Probe.Fail("missing resource " + id));
		return r.readAllAsString();
	}

	private static String upgradeOwo(Probe.Ctx c) throws Exception {
		int n = 0;
		for (String id : new String[] {"owo:shaders/core/blur.fsh", "owo:shaders/core/blur.vsh", "owo:shaders/core/spectrum.fsh"}) {
			String src = read(c, id);
			// the shader as the jar ships it must be the old shape, or this probe says nothing
			need(!src.contains("#version 330"), id + " is already 330 in the jar", src);
			String out = LegacyShaders.shader(src);
			need(out.startsWith("#version 330\n#extension GL_ARB_separate_shader_objects : require"), id + ": version 330 + extension", out);
			need(!out.contains("#moj_import"), id + ": no #moj_import left", out);
			for (String line : out.split("\n")) {
				String t = line.strip();
				boolean io = (t.startsWith("in ") || t.startsWith("out ") || t.startsWith("flat in ") || t.startsWith("flat out ")) && t.endsWith(";") && !line.startsWith(" ") && !line.startsWith("\t");
				if (io) throw new Probe.Fail(id + ": file-level interface without a location: " + t);
			}
			n++;
		}
		return n + " real owo shader sources upgraded: 330, no #moj_import, every file-level in/out has a location";
	}

	private static String vanillaUntouched(Probe.Ctx c) {
		Map<Identifier, Resource> found = c.mc.getResourceManager().listResources("shaders", id ->
			id.getNamespace().equals("minecraft") && (id.getPath().endsWith(".fsh") || id.getPath().endsWith(".vsh")));
		int n = 0;
		List<String> changed = new ArrayList<>();
		for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
			String src;
			try {
				src = e.getValue().readAllAsString();
			} catch (java.io.IOException ex) {
				throw new Probe.Fail("cannot read " + e.getKey() + ": " + ex);
			}
			if (!LegacyShaders.shader(src).equals(src)) changed.add(e.getKey().toString());
			n++;
		}
		if (n == 0) throw new Probe.Fail("no vanilla shader sources found");
		if (!changed.isEmpty()) throw new Probe.Fail(changed.size() + " of " + n + " vanilla sources are rewritten: " + changed.stream().limit(5).collect(Collectors.joining(", ")));
		return n + " vanilla .fsh/.vsh sources come back byte-identical";
	}

	private static String owoPipelines(Probe.Ctx c) throws Exception {
		Class<?> pipelines = Class.forName("io.wispforest.owo.ui.core.OwoUIPipelines", true, ShaderProbes.class.getClassLoader());
		StringBuilder ok = new StringBuilder();
		for (String field : new String[] {"GUI_HSV", "GUI_BLUR", "GUI_TRIANGLE_FAN", "GUI_TRIANGLE_STRIP", "GUI_TEXTURED_NO_BLEND"}) {
			RenderPipeline p = (RenderPipeline) pipelines.getField(field).get(null);
			CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(p);
			if (compiled == null) throw new Probe.Fail(field + " (" + p.getLocation() + ") has no compiled pipeline");
			if (compiled.isClosed()) throw new Probe.Fail(field + " (" + p.getLocation() + ") compiled pipeline is closed");
			ok.append(' ').append(p.getLocation());
		}
		return "compiled:" + ok;
	}

	private static String logClean(Probe.Ctx c) throws Exception {
		Path log = FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
		List<String> bad = Files.readAllLines(log, java.nio.charset.StandardCharsets.UTF_8).stream()
			.filter(l -> l.contains("/ERROR]") || l.contains("/WARN]"))
			.filter(l -> {
				String s = l.toLowerCase(java.util.Locale.ROOT);
				return s.contains("shader") || s.contains("glsl") || s.contains("spir") || s.contains("pipeline");
			})
			.toList();
		if (!bad.isEmpty()) throw new Probe.Fail(bad.size() + " shader/pipeline warning or error lines, first: " + bad.get(0));
		return "no ERROR/WARN line mentioning shader, glsl, spir or pipeline in latest.log";
	}

	private static void need(boolean cond, String what, String source) {
		if (!cond) throw new Probe.Fail("expected " + what + "; got:\n" + source.lines().limit(14).collect(Collectors.joining(" | ")));
	}
}
