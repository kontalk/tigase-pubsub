package tigase.pubsub.repository;

//~--- non-JDK imports --------------------------------------------------------

import java.util.HashMap;
import java.util.Map;

import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.pubsub.utils.FragmentedMap;
import tigase.util.JIDUtils;

//~--- classes ----------------------------------------------------------------

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:27:46 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class NodeSubscriptions implements ISubscriptions {
	protected final static String DELIMITER = ";";

	/** Field description */
	public final static int MAX_FRAGMENT_SIZE = 10000;

	// ~--- fields
	// ---------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public static NodeSubscriptions create() {
		NodeSubscriptions s = new NodeSubscriptions();

		return s;
	}

	private boolean changed = false;

	// ~--- constructors
	// ---------------------------------------------------------

	protected final FragmentedMap<String, UsersSubscription> subs = new FragmentedMap<String, UsersSubscription>(
			MAX_FRAGMENT_SIZE);

	// ~--- methods
	// --------------------------------------------------------------

	protected NodeSubscriptions() {
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * @param subscription
	 * 
	 * @return
	 */
	@Override
	public String addSubscriberJid(final String jid, final Subscription subscription) {
		final String subid = Utils.createUID(jid);
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = new UsersSubscription(bareJid, subid, subscription);

		synchronized (this.subs) {
			subs.put(bareJid, s);
		}

		changed = true;

		return subid;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * @param subscription
	 */
	@Override
	public void changeSubscription(String jid, Subscription subscription) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = get(bareJid);

		if (s != null) {
			s.setSubscription(subscription);
			changed = true;
		}
	}

	// ~--- get methods
	// ----------------------------------------------------------

	protected UsersSubscription get(final String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);

		synchronized (this.subs) {
			UsersSubscription s = this.subs.get(bareJid);

			return s;
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public FragmentedMap<String, UsersSubscription> getFragmentedMap() {
		return subs;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * 
	 * @return
	 */
	@Override
	public Subscription getSubscription(String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = get(bareJid);

		if (s != null) {
			return s.getSubscription();
		}

		return Subscription.none;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * 
	 * @return
	 */
	@Override
	public String getSubscriptionId(String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = get(bareJid);

		if (s != null) {
			return s.getSubid();
		}

		return null;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public UsersSubscription[] getSubscriptions() {
		synchronized (this.subs) {
			return this.subs.getAllValues().toArray(new UsersSubscription[] {});
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public Map<String, UsersSubscription> getSubscriptionsMap() {
		return subs.getMap();
	}

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public boolean isChanged() {
		return changed;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param data
	 */
	public void parse(String data) {
		Map<String, UsersSubscription> parsed = new HashMap<String, UsersSubscription>();
		String[] tokens = data.split(DELIMITER);
		int c = 0;
		String jid = null;
		String subid = null;
		String state = null;

		for (String t : tokens) {
			if (c == 2) {
				state = t;
				++c;
			} else {
				if (c == 1) {
					subid = t;
					++c;
				} else {
					if (c == 0) {
						jid = t;
						++c;
					}
				}
			}

			if (c == 3) {
				UsersSubscription b = new UsersSubscription(jid, subid, Subscription.valueOf(state));

				parsed.put(jid, b);
				jid = null;
				subid = null;
				state = null;
				c = 0;
			}
		}

		synchronized (subs) {
			subs.addFragment(parsed);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeSubscriptions
	 */
	public void replaceBy(final ISubscriptions nodeSubscriptions) {
		synchronized (this.subs) {
			if (nodeSubscriptions instanceof NodeSubscriptions) {
				NodeSubscriptions ns = (NodeSubscriptions) nodeSubscriptions;

				this.changed = true;
				subs.clear();

				for (UsersSubscription a : ns.subs.getAllValues()) {
					subs.put(a.getJid().toString(), a);
				}
			} else {
				throw new RuntimeException("!!!!!!!!!!!!!!!!!!!" + nodeSubscriptions.getClass());
			}
		}
	}

	/**
	 * Method description
	 * 
	 */
	public void resetChangedFlag() {
		this.changed = false;
	}

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param fragment
	 * 
	 * @return
	 */
	@Override
	public String serialize(Map<String, UsersSubscription> fragment) {
		StringBuilder sb = new StringBuilder();

		for (UsersSubscription s : fragment.values()) {
			if (s.getSubscription() != Subscription.none) {
				sb.append(s.getJid());
				sb.append(DELIMITER);
				sb.append(s.getSubid());
				sb.append(DELIMITER);
				sb.append(s.getSubscription().name());
				sb.append(DELIMITER);
			}
		}

		return sb.toString();
	}
}

// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com
