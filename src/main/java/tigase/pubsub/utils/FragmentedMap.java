package tigase.pubsub.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
		Map<String, String> x;

		FragmentedMap<String, String> fm = new FragmentedMap<String, String>(10000 - 1);
		for (int p = 0; p < 1000; p++) {
			x = new XMap<String, String>();
			for (int i = 0; i < 100; i++) {
				x.put("key-" + p + "." + i, "value-" + p + "." + i);
			}
			fm.addFragment(x);
		}

		System.out.println(fm.getLoadFactor());
		fm.defragment();
		System.out.println(fm.getLoadFactor());
		fm.defragment();
		System.out.println(fm.getLoadFactor());
		System.out.println(fm.getAllValues().size());

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
		intDefragment();
		float factor = getLoadFactor();
		if (factor < 0.49999) {
			optimize();
			intDefragment();
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
		return new HashMap<KEY, VALUE>(this.fragments.get(index));
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

	private float getLoadFactor() {
		float sum = 0;
		float area = 0;
		if (this.fragments.size() > 1) {
			for (Map<KEY, VALUE> iterable_element : this.fragments) {
				sum += iterable_element.size();
				area += maxFragmentSize;
			}

			return sum / area;
		} else {
			return 1;
		}
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

	private void intDefragment() {
		final int size = this.fragments.size();
		Iterator<Map<KEY, VALUE>> iterator = this.fragments.iterator();
		while (iterator.hasNext()) {
			Map<KEY, VALUE> f = iterator.next();
			if (f.size() == 0) {
				iterator.remove();
			}
		}
		for (int i = this.fragments.size(); i < size; i++) {
			this.removedFragmentsIndexes.add(i);
		}
	}

	private void optimize() {
		Iterator<Entry<KEY, VALUE>> iterator = getMap().entrySet().iterator();
		Set<Entry<KEY, VALUE>> set = new HashSet<Entry<KEY, VALUE>>();
		while (iterator.hasNext()) {
			Entry<KEY, VALUE> p = iterator.next();
			set.add(p);
			remove(p.getKey());
		}

		for (Entry<KEY, VALUE> entry : set) {
			put(entry.getKey(), entry.getValue());
		}
	}

	public synchronized VALUE put(KEY key, VALUE value) {
		Map<KEY, VALUE> fragment = getFragmentWithKey(key);
		if (fragment == null) {
			fragment = getFragmentToNewData();
			if (fragment == null) {
				fragment = new XMap<KEY, VALUE>();
				this.fragments.add(fragment);
				int index = this.fragments.indexOf(fragment);
				this.removedFragmentsIndexes.remove(index);
			}
		}

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
