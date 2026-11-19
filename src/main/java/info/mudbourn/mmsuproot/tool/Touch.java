package info.mudbourn.mmsuproot.tool;

/**
 * One mod touching one node of {@link IntersectTool}: the mod id, what kind of touch it is (an annotation, a file kind), a short
 * detail, its weight, whether the mod was remapped by Uproot, and whether the node's target is one that Uproot has a rule or shim for.
 */
record Touch(String mod, String kind, String detail, double weight, boolean legacy, boolean rule) {
	Touch withWeight(double w) {
		return new Touch(mod, kind, detail, w, legacy, rule);
	}
}
