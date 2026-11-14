package info.mudbourn.mmsuproot.shim.compat;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.FastEntrySet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import it.unimi.dsi.fastutil.objects.ObjectCollections;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteraction.Dispatcher;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.world.item.Item;

/** Cauldron interaction tables: 1.21.11 kept a named map from item to interaction; 26.3 keeps a dispatcher by item and tag. */
public final class LegacyCauldron {
	private LegacyCauldron() { }

	/** {@code CauldronInteraction.newInteractionMap(name)}: a table registered under the name its codec writes. */
	public static Dispatcher newInteractionMap(String name) {
		return CauldronInteractions.newDispatcher(name);
	}

	/** {@code InteractionMap.map()}: the item table of the dispatcher, as a fastutil map because a mod sets its default return value when it is one. */
	public static Map<Item, CauldronInteraction> map(Dispatcher dispatcher) {
		return new ItemTable(dispatcher.items);
	}

	/**
	 * Writes through to the dispatcher (what a mod puts is what the cauldron answers with); the old default for an item with no entry is
	 * {@code CauldronInteraction.DEFAULT} there already. Every way of changing the map ends in {@link #mirror}, since fastutil's bulk and
	 * compute methods do not go through {@code put}.
	 */
	private static final class ItemTable extends Object2ObjectOpenHashMap<Item, CauldronInteraction> {
		private final Map<Item, CauldronInteraction> items;

		ItemTable(Map<Item, CauldronInteraction> items) {
			this.items = items;
		}

		/** Makes the dispatcher's entry for the item what this map holds for it. */
		private void mirror(Object key) {
			if (!(key instanceof Item item)) return;
			if (super.containsKey(item)) items.put(item, super.get(item));
			else items.remove(item);
		}

		@Override
		public CauldronInteraction put(Item item, CauldronInteraction interaction) {
			CauldronInteraction before = super.put(item, interaction);
			mirror(item);
			return before;
		}

		@Override
		public void putAll(Map<? extends Item, ? extends CauldronInteraction> m) {
			super.putAll(m);
			for (Item item : m.keySet()) mirror(item);
		}

		@Override
		public CauldronInteraction putIfAbsent(Item item, CauldronInteraction interaction) {
			CauldronInteraction before = super.putIfAbsent(item, interaction);
			mirror(item);
			return before;
		}

		@Override
		public CauldronInteraction computeIfAbsent(Item item, Function<? super Item, ? extends CauldronInteraction> mapping) {
			CauldronInteraction now = super.computeIfAbsent(item, mapping);
			mirror(item);
			return now;
		}

		@Override
		public CauldronInteraction computeIfPresent(Item item, BiFunction<? super Item, ? super CauldronInteraction, ? extends CauldronInteraction> mapping) {
			CauldronInteraction now = super.computeIfPresent(item, mapping);
			mirror(item);
			return now;
		}

		@Override
		public CauldronInteraction compute(Item item, BiFunction<? super Item, ? super CauldronInteraction, ? extends CauldronInteraction> mapping) {
			CauldronInteraction now = super.compute(item, mapping);
			mirror(item);
			return now;
		}

		@Override
		public CauldronInteraction merge(Item item, CauldronInteraction value, BiFunction<? super CauldronInteraction, ? super CauldronInteraction, ? extends CauldronInteraction> mapping) {
			CauldronInteraction now = super.merge(item, value, mapping);
			mirror(item);
			return now;
		}

		@Override
		public CauldronInteraction replace(Item item, CauldronInteraction interaction) {
			CauldronInteraction before = super.replace(item, interaction);
			mirror(item);
			return before;
		}

		@Override
		public boolean replace(Item item, CauldronInteraction expected, CauldronInteraction interaction) {
			boolean replaced = super.replace(item, expected, interaction);
			mirror(item);
			return replaced;
		}

		@Override
		public CauldronInteraction remove(Object item) {
			CauldronInteraction before = super.remove(item);
			mirror(item);
			return before;
		}

		@Override
		public boolean remove(Object item, Object value) {
			boolean removed = super.remove(item, value);
			mirror(item);
			return removed;
		}

		@Override
		public void replaceAll(BiFunction<? super Item, ? super CauldronInteraction, ? extends CauldronInteraction> mapping) {
			super.replaceAll(mapping);
			for (Item item : super.keySet().toArray(new Item[0])) mirror(item);
		}

		// the views would change the map without going through mirror, so they are read-only
		@Override
		public ObjectSet<Item> keySet() {
			return ObjectSets.unmodifiable(super.keySet());
		}

		@Override
		public ObjectCollection<CauldronInteraction> values() {
			return ObjectCollections.unmodifiable(super.values());
		}

		@Override
		public FastEntrySet<Item, CauldronInteraction> object2ObjectEntrySet() {
			FastEntrySet<Item, CauldronInteraction> entries = super.object2ObjectEntrySet();
			return new FastEntrySet<>() {
				@Override
				public ObjectIterator<Object2ObjectMap.Entry<Item, CauldronInteraction>> iterator() {
					return ObjectIterators.unmodifiable(entries.iterator());
				}

				@Override
				public ObjectIterator<Object2ObjectMap.Entry<Item, CauldronInteraction>> fastIterator() {
					return ObjectIterators.unmodifiable(entries.fastIterator());
				}

				@Override
				public int size() {
					return entries.size();
				}

				@Override
				public boolean isEmpty() {
					return entries.isEmpty();
				}

				@Override
				public boolean contains(Object o) {
					return entries.contains(o);
				}

				@Override
				public Object[] toArray() {
					return entries.toArray();
				}

				@Override
				public <T> T[] toArray(T[] a) {
					return entries.toArray(a);
				}

				@Override
				public boolean add(Object2ObjectMap.Entry<Item, CauldronInteraction> e) {
					throw new UnsupportedOperationException();
				}

				@Override
				public boolean remove(Object o) {
					throw new UnsupportedOperationException();
				}

				@Override
				public boolean containsAll(Collection<?> c) {
					return entries.containsAll(c);
				}

				@Override
				public boolean addAll(Collection<? extends Object2ObjectMap.Entry<Item, CauldronInteraction>> c) {
					throw new UnsupportedOperationException();
				}

				@Override
				public boolean retainAll(Collection<?> c) {
					throw new UnsupportedOperationException();
				}

				@Override
				public boolean removeAll(Collection<?> c) {
					throw new UnsupportedOperationException();
				}

				@Override
				public void clear() {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public ObjectSet<Map.Entry<Item, CauldronInteraction>> entrySet() {
			return (ObjectSet) object2ObjectEntrySet();
		}

		@Override
		public void clear() {
			items.keySet().removeAll(super.keySet());
			super.clear();
		}
	}
}
