package tigase.pubsub.modules.ext.presence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import tigase.pubsub.Subscription;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.Packet;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

public class PresenceNodeSubscriptions implements ISubscriptions {

	private final PresencePerNodeExtension extension;
	private final String nodeName;
	private final BareJID serviceJID;
	private final ISubscriptions subscriptions;

	public PresenceNodeSubscriptions(BareJID serviceJid, String nodeName, ISubscriptions subscriptions,
			PresenceNotifierModule presenceExtensionModule) {
		this.serviceJID = serviceJid;
		this.nodeName = nodeName;
		this.subscriptions = subscriptions;
		this.extension = presenceExtensionModule.getPresencePerNodeExtension();
	}

	@Override
	public String addSubscriberJid(BareJID jid, Subscription subscription) {
		return subscriptions.addSubscriberJid(jid, subscription);
	}

	@Override
	public void changeSubscription(BareJID jid, Subscription subscription) {
		subscriptions.changeSubscription(jid, subscription);
	}

	@Override
	public Subscription getSubscription(BareJID jid) {
		Subscription s = subscriptions.getSubscription(jid);

		if (s == Subscription.none) {
			Collection<Packet> ocs = extension.getPresence(serviceJID, nodeName, jid);
			for (Packet packet : ocs) {
				if (packet.getType() == null || packet.getType() == StanzaType.available) {
					return Subscription.subscribed;
				}
			}
		}
		return s;
	}

	@Override
	public String getSubscriptionId(BareJID jid) {
		String id = subscriptions.getSubscriptionId(jid);

		if (id == null) {
			Collection<Packet> ocs = extension.getPresence(serviceJID, nodeName, jid);
			for (Packet packet : ocs) {
				if (packet.getType() == null || packet.getType() == StanzaType.available) {
					return "pr:" + packet.getStanzaFrom().getBareJID().hashCode();
				}
			}
		}

		return id;
	}

	@Override
	public UsersSubscription[] getSubscriptions() {
		final Map<BareJID, UsersSubscription> result = new HashMap<BareJID, UsersSubscription>();

		UsersSubscription[] o = subscriptions.getSubscriptions();
		if (o != null) {
			for (UsersSubscription usersSubscription : o) {
				result.put(usersSubscription.getJid(), usersSubscription);
			}
		}

		Collection<JID> occupants = extension.getNodeOccupants(serviceJID, nodeName);
		for (JID jid : occupants) {
			if (!result.containsKey(jid.getBareJID())) {
				Packet pr = extension.getPresence(serviceJID, nodeName, jid);
				if (pr.getType() == null || pr.getType() == StanzaType.available)
					result.put(jid.getBareJID(), new UsersSubscription(jid.getBareJID(), "pr:" + jid.getBareJID().hashCode(),
							Subscription.subscribed));
			}
		}

		return result.values().toArray(new UsersSubscription[] {});
	}

	@Override
	public UsersSubscription[] getSubscriptionsForPublish() {
		return getSubscriptions();
	}
	
	@Override
	public boolean isChanged() {
		return subscriptions.isChanged();
	}

	@Override
	public String serialize(Map<BareJID, UsersSubscription> fragment) {
		return subscriptions.serialize(fragment);
	}

}
