package info.mudbourn.mmsuproot.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The touches one scanning thread has found, by node key. Merged into the run's table once the thread is done. */
final class Touches {
	final Map<String, List<Touch>> byNode = new HashMap<>();

	/** Every touch counts: two injectors of one mod into one method both add weight. */
	void add(String node, Touch t) {
		byNode.computeIfAbsent(node, k -> new ArrayList<>(2)).add(t);
	}

	/** A node of content: a mod that mentions it in several files still touches it once. */
	void addOnce(String node, Touch t) {
		List<Touch> list = byNode.computeIfAbsent(node, k -> new ArrayList<>(2));
		for (Touch o : list) if (o.mod().equals(t.mod())) return;
		list.add(t);
	}

	void merge(Touches other) {
		other.byNode.forEach((k, v) -> byNode.computeIfAbsent(k, x -> new ArrayList<>(v.size())).addAll(v));
	}
}
