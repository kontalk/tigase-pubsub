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

	protected final ThreadLocal<Map<BareJID, UsersSubscription>> changedSubs = new ThreadLocal<Map<BareJID, UsersSubscription>>();

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

		changedSubs().put(bareJid, s);

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

			changedSubs().put(s.getJid(), s);
		}
	}

	@Override
	protected UsersSubscription get(final BareJID bareJid) {
		UsersSubscription us = null;

		us = changedSubs().get(bareJid);

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

		result.addAll(this.subs.values());

		result.addAll(this.changedSubs().values());

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
		return this.changedSubs().size() > 0;
	}

	/**
	 * Method description
	 * 
	 */
	public void merge() {
		Map<BareJID, UsersSubscription> changedSubs = changedSubs();
		for (Map.Entry<BareJID, UsersSubscription> entry : changedSubs.entrySet()) {
			if (entry.getValue().getSubscription() == Subscription.none) {
				subs.remove(entry.getKey());
			} else {
				subs.put(entry.getKey(), entry.getValue());
			}
		}
		//subs.putAll(changedSubs);
		changedSubs.clear();
	}

	public Map<BareJID,UsersSubscription> getChanged() {
		return changedSubs();
	}
	
	/**
	 * Method description
	 * 
	 */
	@Override
	public void resetChangedFlag() {
		changedSubs().clear();
	}
	
	private Map<BareJID, UsersSubscription> changedSubs() {
		Map<BareJID, UsersSubscription> changedSubs = this.changedSubs.get();
		if (changedSubs == null) {
			changedSubs = new HashMap<BareJID, UsersSubscription>();
			this.changedSubs.set(changedSubs);
		}
		return changedSubs;
	}
}
