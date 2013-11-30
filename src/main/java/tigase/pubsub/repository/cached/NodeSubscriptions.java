package tigase.pubsub.repository.cached;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xmpp.BareJID;

public class NodeSubscriptions extends tigase.pubsub.repository.NodeSubscriptions {

	protected final Map<BareJID, UsersSubscription> changedSubs = new HashMap<BareJID, UsersSubscription>();

	public NodeSubscriptions() {
	}

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param nodeSubscriptions
	 */
	public NodeSubscriptions(tigase.pubsub.repository.NodeSubscriptions nodeSubscriptions) {
		subs.putAll(nodeSubscriptions.getSubscriptionsMap());
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
	public String addSubscriberJid(BareJID bareJid, Subscription subscription) {
		final String subid = Utils.createUID(bareJid);
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
	public void changeSubscription(BareJID bareJid, Subscription subscription) {
		UsersSubscription s = subs.get(bareJid);

		if (s != null) {
			s.setSubscription(subscription);

			synchronized (changedSubs) {
				changedSubs.put(s.getJid(), s);
			}
		}
	}

	@Override
	protected UsersSubscription get(final BareJID bareJid) {
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
}
