package tigase.pubsub.repository;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.NodeType;
import tigase.pubsub.Subscription;
import tigase.pubsub.repository.inmemory.NodeAffiliation;
import tigase.pubsub.repository.inmemory.Subscriber;
import tigase.xml.Element;

public interface IPubSubRepository {

	public abstract void addListener(PubSubRepositoryListener listener);

	public abstract String addSubscriberJid(String nodeName, String jid, Affiliation affiliation, Subscription subscription)
			throws RepositoryException;

	public abstract void changeAffiliation(String nodeName, String jid, Affiliation affiliation) throws RepositoryException;

	public abstract void changeSubscription(String nodeName, String jid, Subscription subscription) throws RepositoryException;

	public abstract void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType,
			String collection) throws RepositoryException;

	public abstract void deleteItem(String nodeName, String id) throws RepositoryException;

	public abstract void deleteNode(String nodeName) throws RepositoryException;

	public abstract void forgetConfiguration(String nodeName) throws RepositoryException;

	public abstract NodeAffiliation[] getAffiliations(final String nodeName) throws RepositoryException;

	public abstract String[] getBuddyGroups(String owner, String bareJid) throws RepositoryException;

	public abstract String getBuddySubscription(String owner, String buddy) throws RepositoryException;

	public abstract Element getItem(String nodeName, String id) throws RepositoryException;

	public abstract String getItemCreationDate(String nodeName, String id) throws RepositoryException;

	public abstract String[] getItemsIds(String nodeName) throws RepositoryException;

	public abstract AbstractNodeConfig getNodeConfig(String nodeName) throws RepositoryException;

	public abstract String[] getNodesList() throws RepositoryException;

	public abstract IPubSubDAO getPubSubDAO();

	public abstract NodeAffiliation getSubscriberAffiliation(String nodeName, String jid) throws RepositoryException;

	public abstract Subscription getSubscription(String nodeName, String jid) throws RepositoryException;

	public abstract String getSubscriptionId(String nodeName, String jid) throws RepositoryException;

	public abstract Subscriber[] getSubscriptions(String nodeName) throws RepositoryException;

	public abstract String[] getUserRoster(String owner) throws RepositoryException;

	public abstract void init();

	public abstract void removeListener(PubSubRepositoryListener listener);

	public abstract void removeSubscriber(String nodeName, String jid) throws RepositoryException;

	public abstract void setNewNodeCollection(String nodeName, String collectionNew) throws RepositoryException;

	public abstract void update(String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException;

	public abstract void writeItem(String nodeName, long timeInMilis, String id, String publisher, Element item)
			throws RepositoryException;

}