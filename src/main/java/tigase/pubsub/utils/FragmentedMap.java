package tigase.pubsub.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class FragmentedMap<KEY, VALUE> {

	private static class XMap<K, V> extends HashMap<K, V> {

		private static final long serialVersionUID = 1L;

		private final Object X = new Object();

		@Override
		public boolean equals(Object o) {
			if (o instanceof XMap) {
				return X == ((XMap<?, ?>) o).X;
			} else
				return super.equals(o);
		}

		@Override
		public int hashCode() {
			return X.hashCode();
		}

	}

	public static void main(String[] args) {
		Map<String, String> x = new XMap<String, String>();

		int z = 3;
		FragmentedMap<String, String> fm = new FragmentedMap<String, String>(z);
		for (int i = 0; i < 9; i++) {
			x.put("key-" + i, "value-" + i);
		}
		fm.addFragment(x);

		fm.showDebug();

		// fm.defragment();

		fm.showDebug();

		fm.put("key-10", "value-10");

		fm.showDebug();

	}

	private final Set<Map<KEY, VALUE>> changedFragments = new HashSet<Map<KEY, VALUE>>();

	private final ArrayList<Map<KEY, VALUE>> fragments = new ArrayList<Map<KEY, VALUE>>();

	private final int maxFragmentSize;

	private final Set<Integer> removedFragmentsIndexes = new HashSet<Integer>();

	public FragmentedMap(int maxFragmentSize) {
		this.maxFragmentSize = maxFragmentSize;
	}

	public synchronized void addFragment(Map<KEY, VALUE> fragment) {
		if (fragment.size() <= maxFragmentSize) {
			XMap<KEY, VALUE> f = new XMap<KEY, VALUE>();
			f.putAll(fragment);
			this.fragments.add(f);
		} else {
			for (Entry<KEY, VALUE> en : fragment.entrySet()) {
				put(en.getKey(), en.getValue());
			}
		}
	}

	public synchronized void cleanChangingLog() {
		this.changedFragments.clear();
		this.removedFragmentsIndexes.clear();
	}

	public synchronized void clear() {
		this.changedFragments.clear();
		this.fragments.clear();
		this.removedFragmentsIndexes.clear();
	}

	public synchronized void defragment() {
		final int size = this.fragments.size();
		Iterator<Map<KEY, VALUE>> iterator = this.fragments.iterator();
		while (iterator.hasNext()) {
			Map<KEY, VALUE> f = iterator.next();
			if (f.size() == 0) {
				System.out.println(this.changedFragments.size());
				System.out.println(">>" + this.changedFragments.remove(f));
				System.out.println(this.changedFragments.size());
				iterator.remove();
			}
		}
		for (int i = this.fragments.size(); i < size; i++) {
			this.removedFragmentsIndexes.add(i);
		}
	}

	public synchronized VALUE get(KEY key) {
		Map<KEY, VALUE> fragment = getFragmentWithKey(key);
		if (fragment != null) {
			return fragment.get(key);
		}
		return null;
	}

	public synchronized Collection<VALUE> getAllValues() {
		Set<VALUE> x = new HashSet<VALUE>();
		for (Map<KEY, VALUE> f : this.fragments) {
			x.addAll(f.values());
		}
		return Collections.unmodifiableCollection(x);
	}

	public synchronized Set<Integer> getChangedFragmentIndexes() {
		Set<Integer> r = new HashSet<Integer>();
		for (Map<KEY, VALUE> f : this.changedFragments) {
			r.add(this.fragments.indexOf(f));
		}
		return r;
	}

	public synchronized Map<KEY, VALUE> getFragment(int index) {
		return this.fragments.get(index);
	}

	public synchronized int getFragmentsCount() {
		return this.fragments.size();
	}

	protected Map<KEY, VALUE> getFragmentToNewData() {
		for (Map<KEY, VALUE> f : this.changedFragments) {
			if (f.size() < maxFragmentSize) {
				return f;
			}
		}
		for (Map<KEY, VALUE> f : this.fragments) {
			if (f.size() < maxFragmentSize) {
				return f;
			}
		}
		return null;
	}

	protected Map<KEY, VALUE> getFragmentWithKey(KEY key) {
		for (Map<KEY, VALUE> f : this.fragments) {
			if (f.containsKey(key)) {
				return f;
			}
		}
		return null;
	}

	public synchronized Map<KEY, VALUE> getMap() {
		Map<KEY, VALUE> result = new HashMap<KEY, VALUE>();
		for (Map<KEY, VALUE> f : this.fragments) {
			result.putAll(f);
		}
		return Collections.unmodifiableMap(result);
	}

	public synchronized Set<Integer> getRemovedFragmentIndexes() {
		return Collections.unmodifiableSet(this.removedFragmentsIndexes);
	}

	public synchronized VALUE put(KEY key, VALUE value) {
		System.out.println("adding: " + key + " :: " + value);
		Map<KEY, VALUE> fragment = getFragmentWithKey(key);
		if (fragment == null) {
			fragment = getFragmentToNewData();
			if (fragment == null) {
				fragment = new XMap<KEY, VALUE>();
				this.fragments.add(fragment);
				int index = this.fragments.indexOf(fragment);
				this.removedFragmentsIndexes.remove(index);
				System.out.println("Dodany fragment " + index);
			}
		}

		int index = this.fragments.indexOf(fragment);
		System.out.println("Zmieniono fragment " + index + "   (" + fragment.hashCode() + ")");
		if (!this.changedFragments.contains(fragment))
			this.changedFragments.add(fragment);
		return fragment.put(key, value);
	}

	public synchronized void putAll(Map<KEY, VALUE> fragment) {
		for (Entry<KEY, VALUE> e : fragment.entrySet()) {
			put(e.getKey(), e.getValue());
		}
	}

	public synchronized VALUE remove(KEY key) {
		Map<KEY, VALUE> f = getFragmentWithKey(key);
		if (f != null) {
			VALUE value = f.remove(key);
			this.changedFragments.add(f);
			return value;
		}
		return null;
	}

	private void showDebug() {
		for (int i = 0; i < getFragmentsCount(); i++) {
			System.out.println(i + ": " + getFragment(i));
		}
		System.out.println("C: " + getChangedFragmentIndexes());
		System.out.println("R: " + removedFragmentsIndexes);
		System.out.println();
	}

}
