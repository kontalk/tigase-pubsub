package tigase.pubsub.repository.cached;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.util.JIDUtils;

class NodeSubscriptions extends tigase.pubsub.repository.NodeSubscriptions {

	protected final Map<String, UsersSubscription> changedSubs = new HashMap<String, UsersSubscription>();

	private NodeSubscriptions() {
	}

	public NodeSubscriptions(tigase.pubsub.repository.NodeSubscriptions nodeSubscriptions) {
		subs.putAll(nodeSubscriptions.getSubscriptionsMap());
	}

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

	@Override
	public void changeSubscription(String jid, Subscription subscription) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = subs.get(bareJid);
		if (s != null) {
			s.setSubscription(subscription);
			synchronized (changedSubs) {
				changedSubs.put(s.getJid(), s);
			}
		}
	}

	@Override
	protected UsersSubscription get(final String bareJid) {
		UsersSubscription us = null;
		synchronized (changedSubs) {
			us = changedSubs.get(bareJid);
		}
		if (us == null) {
			us = subs.get(bareJid);
			if (us != null)
				try {
					return us.clone();
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
		}
		return us;
	}

	@Override
	public UsersSubscription[] getSubscriptions() {
		final Set<UsersSubscription> result = new HashSet<UsersSubscription>();
		result.addAll(this.subs.getAllValues());
		synchronized (changedSubs) {
			result.addAll(this.changedSubs.values());
		}
		return result.toArray(new UsersSubscription[] {});
	}

	@Override
	public boolean isChanged() {
		synchronized (changedSubs) {
			return this.changedSubs.size() > 0;
		}
	}

	public void merge() {
		synchronized (changedSubs) {
			subs.putAll(changedSubs);
			changedSubs.clear();
		}
	}

	@Override
	public void resetChangedFlag() {
		synchronized (changedSubs) {
			this.changedSubs.clear();
		}
	}

}