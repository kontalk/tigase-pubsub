package tigase.pubsub.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.pubsub.utils.FragmentedMap;
import tigase.xmpp.BareJID;

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:27:46 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public abstract class NodeSubscriptions implements ISubscriptions {

	protected final static String DELIMITER = ";";

	/** Field description */
//	public final static int MAX_FRAGMENT_SIZE = 10000;

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public static tigase.pubsub.repository.cached.NodeSubscriptions create() {
		tigase.pubsub.repository.cached.NodeSubscriptions s = new tigase.pubsub.repository.cached.NodeSubscriptions();

		return s;
	}

	private boolean changed = false;

//	protected final FragmentedMap<BareJID, UsersSubscription> subs = new FragmentedMap<BareJID, UsersSubscription>(
//			MAX_FRAGMENT_SIZE);
	protected final ConcurrentMap<BareJID, UsersSubscription> subs = new ConcurrentHashMap<BareJID, UsersSubscription>();

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
	public String addSubscriberJid(final BareJID bareJid, final Subscription subscription) {
		final String subid = Utils.createUID(bareJid);
		UsersSubscription s = new UsersSubscription(bareJid, subid, subscription);

		subs.put(bareJid, s);

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
	public void changeSubscription(BareJID bareJid, Subscription subscription) {
		UsersSubscription s = get(bareJid);

		if (s != null) {
			s.setSubscription(subscription);
			changed = true;
		}
	}

	protected UsersSubscription get(final BareJID bareJid) {
		return this.subs.get(bareJid);
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
	public Subscription getSubscription(BareJID bareJid) {
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
	public String getSubscriptionId(BareJID bareJid) {
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
			return this.subs.values().toArray(new UsersSubscription[] {});
		}
	}

	@Override
	public UsersSubscription[] getSubscriptionsForPublish() {
		return getSubscriptions();
	}	
	
	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public Map<BareJID, UsersSubscription> getSubscriptionsMap() {
		return subs;
	}

	public void init(Queue<UsersSubscription> data) {
		UsersSubscription s = null;
		while ((s = data.poll()) != null) {
			subs.put(s.getJid(), s);
		}
	}
	
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
		Map<BareJID, UsersSubscription> parsed = new HashMap<BareJID, UsersSubscription>();
		String[] tokens = data.split(DELIMITER);
		int c = 0;
		BareJID jid = null;
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
						jid = BareJID.bareJIDInstanceNS(t);
						++c;
					}
				}
			}

			if (c == 3) {				
				UsersSubscription b = new UsersSubscription(jid, subid, Subscription.valueOf(state));

				subs.put(jid, b);
				jid = null;
				subid = null;
				state = null;
				c = 0;
			}
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

				for (UsersSubscription a : ns.subs.values()) {
					subs.put(a.getJid(), a);
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

	/**
	 * Method description
	 * 
	 * 
	 * @param fragment
	 * 
	 * @return
	 */
	@Override
	public String serialize(Map<BareJID, UsersSubscription> fragment) {
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
