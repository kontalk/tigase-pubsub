package tigase.pubsub.repository;

import java.util.Map;

import tigase.pubsub.Subscription;
import tigase.pubsub.repository.stateless.UsersSubscription;

public interface ISubscriptions {

	public abstract String addSubscriberJid(String jid, Subscription subscription);

	public abstract void changeSubscription(String jid, Subscription subscription);

	public abstract Subscription getSubscription(String jid);

	public abstract String getSubscriptionId(String jid);

	public abstract UsersSubscription[] getSubscriptions();

	public boolean isChanged();

	public abstract String serialize(Map<String, UsersSubscription> fragment);

}
