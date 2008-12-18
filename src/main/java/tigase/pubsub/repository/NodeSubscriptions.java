package tigase.pubsub.repository;

import java.util.HashMap;
import java.util.Map;

import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.util.JIDUtils;

public class NodeSubscriptions implements ISubscriptions {

	protected final static String DELIMITER = ";";

	public static NodeSubscriptions create(String data) {
		NodeSubscriptions s = new NodeSubscriptions();
		try {
			s.parse(data);
			return s;
		} catch (Exception e) {
			return new NodeSubscriptions();
		}
	}

	private boolean changed = false;

	protected final Map<String, UsersSubscription> subs = new HashMap<String, UsersSubscription>();

	protected NodeSubscriptions() {
	}

	public String addSubscriberJid(final String jid, final Subscription subscription) {
		final String subid = Utils.createUID();
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = new UsersSubscription(bareJid, subid, subscription);
		subs.put(bareJid, s);
		changed = true;
		return subid;
	}

	public void changeSubscription(String jid, Subscription subscription) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = get(bareJid);
		if (s != null) {
			s.setSubscription(subscription);
			changed = true;
		}
	}

	@Override
	public NodeSubscriptions clone() throws CloneNotSupportedException {
		NodeSubscriptions clone = new NodeSubscriptions();
		for (UsersSubscription a : this.subs.values()) {
			clone.subs.put(a.getJid(), a.clone());
		}
		clone.changed = changed;
		return clone;
	}

	protected UsersSubscription get(final String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = this.subs.get(bareJid);
		return s;
	}

	public Subscription getSubscription(String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = get(bareJid);
		if (s != null) {
			return s.getSubscription();
		}
		return Subscription.none;
	}

	public String getSubscriptionId(String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersSubscription s = get(bareJid);
		if (s != null) {
			return s.getSubid();
		}
		return null;
	}

	public UsersSubscription[] getSubscriptions() {
		return this.subs.values().toArray(new UsersSubscription[] {});
	}

	public Map<String, UsersSubscription> getSubscriptionsMap() {
		return subs;
	}

	public boolean isChanged() {
		return changed;
	}

	public void parse(String data) {
		String[] tokens = data.split(DELIMITER);
		subs.clear();
		int c = 0;
		String jid = null;
		String subid = null;
		String state = null;
		for (String t : tokens) {
			if (c == 2) {
				state = t;
				++c;
			} else if (c == 1) {
				subid = t;
				++c;
			} else if (c == 0) {
				jid = t;
				++c;
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

	public void replaceBy(final ISubscriptions nodeSubscriptions) {

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

	public void resetChangedFlag() {
		this.changed = false;
	}

	public String serialize() {
		StringBuilder sb = new StringBuilder();
		for (UsersSubscription s : this.subs.values()) {
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
