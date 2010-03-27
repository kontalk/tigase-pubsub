package tigase.pubsub.repository.cached;

//~--- non-JDK imports --------------------------------------------------------

import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.repository.stateless.UsersSubscription;

import tigase.util.JIDUtils;

//~--- JDK imports ------------------------------------------------------------

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//~--- classes ----------------------------------------------------------------

class NodeSubscriptions extends tigase.pubsub.repository.NodeSubscriptions {
	protected final Map<String, UsersSubscription> changedSubs = new HashMap<String,
		UsersSubscription>();

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param nodeSubscriptions
	 */
	public NodeSubscriptions(tigase.pubsub.repository.NodeSubscriptions nodeSubscriptions) {
		subs.putAll(nodeSubscriptions.getSubscriptionsMap());
	}

	private NodeSubscriptions() {}

	//~--- methods --------------------------------------------------------------

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
	public String addSubscriberJid(String jid, Subscription subscription) {
		final String subid = Utils.createUID(jid);
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = new UsersSubscription(bareJid, subid, subscription);

		synchronized (changedSubs) {
			changedSubs.put(bareJid, s);
		}

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
		UsersSubscription s = subs.get(bareJid);

		if (s != null) {
			s.setSubscription(subscription);

			synchronized (changedSubs) {
				changedSubs.put(s.getJid().toString(), s);
			}
		}
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public UsersSubscription[] getSubscriptions() {
		final Set<UsersSubscription> result = new HashSet<UsersSubscription>();

		result.addAll(this.subs.getAllValues());

		synchronized (changedSubs) {
			result.addAll(this.changedSubs.values());
		}

		return result.toArray(new UsersSubscription[] {});
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public boolean isChanged() {
		synchronized (changedSubs) {
			return this.changedSubs.size() > 0;
		}
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 */
	public void merge() {
		synchronized (changedSubs) {
			subs.putAll(changedSubs);
			changedSubs.clear();
		}
	}

	/**
	 * Method description
	 *
	 */
	@Override
	public void resetChangedFlag() {
		synchronized (changedSubs) {
			this.changedSubs.clear();
		}
	}

	//~--- get methods ----------------------------------------------------------

	@Override
	protected UsersSubscription get(final String bareJid) {
		UsersSubscription us = null;

		synchronized (changedSubs) {
			us = changedSubs.get(bareJid);
		}

		if (us == null) {
			us = subs.get(bareJid);

			if (us != null) {
				try {
					return us.clone();
				} catch (Exception e) {
					e.printStackTrace();

					return null;
				}
			}
		}

		return us;
	}
}


//~ Formatted in Sun Code Convention


//~ Formatted by Jindent --- http://www.jindent.com
