package tigase.pubsub.utils;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

public class FragmentedMapTest extends TestCase {

	private Map<String, String> f1;

	private Map<String, String> f2;

	private Map<String, String> f3;

	private FragmentedMap<String, String> fm;

	@Override
	@Before
	public void setUp() {
		f1 = new HashMap<String, String>();
		f1.put("k-1-1", "v-1-1");
		f1.put("k-1-2", "v-1-2");
		f1.put("k-1-3", "v-1-3");
		f1.put("k-1-4", "v-1-4");
		f2 = new HashMap<String, String>();
		f2.put("k-2-1", "v-2-1");
		f2.put("k-2-2", "v-2-2");
		f2.put("k-2-3", "v-2-3");
		f2.put("k-2-4", "v-2-4");
		f3 = new HashMap<String, String>();
		f3.put("k-3-1", "v-3-1");
		f3.put("k-3-2", "v-3-2");
		f3.put("k-3-3", "v-3-3");
		f3.put("k-3-4", "v-3-4");

		fm = new FragmentedMap<String, String>(4);
	}

	@Test
	public void test01() {
		Assert.assertEquals(0, fm.getFragmentsCount());
		fm.addFragment(f1);
		Assert.assertEquals(1, fm.getFragmentsCount());
		fm.addFragment(f2);
		Assert.assertEquals(2, fm.getFragmentsCount());
		fm.addFragment(f3);

		Assert.assertEquals(3, fm.getFragmentsCount());
		Assert.assertEquals(0, fm.getChangedFragmentIndexes().size());
		Assert.assertEquals(0, fm.getRemovedFragmentIndexes().size());

		fm.remove("k-2-2");
		Assert.assertEquals(1, fm.getChangedFragmentIndexes().size());

	}

	@Test
	public void test02() {
		HashMap<String, String> f = new HashMap<String, String>();
		for (int i = 0; i < 12; i++) {
			f.put("k-1-" + i, "v-1-" + i);
		}
		fm.addFragment(f);
		Assert.assertEquals(3, fm.getFragmentsCount());

	}

}
