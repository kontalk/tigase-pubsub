package tigase.pubsub.repository;

import java.util.Date;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.xml.Element;

public interface IPubSubRepository {

	public void addToRootCollection(String nodeName) throws RepositoryException;

	public abstract void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType,
			String collection) throws RepositoryException;

	public abstract void deleteNode(String nodeName) throws RepositoryException;

	public abstract void forgetConfiguration(String nodeName) throws RepositoryException;

	public abstract String[] getBuddyGroups(String owner, String bareJid) throws RepositoryException;

	public abstract String getBuddySubscription(String owner, String buddy) throws RepositoryException;

	public IAffiliations getNodeAffiliations(String nodeName) throws RepositoryException;

	public IItems getNodeItems(String nodeName) throws RepositoryException;

	public abstract AbstractNodeConfig getNodeConfig(String nodeName) throws RepositoryException;

	public ISubscriptions getNodeSubscriptions(String nodeName) throws RepositoryException;

	public abstract IPubSubDAO getPubSubDAO();

	public abstract String[] getRootCollection() throws RepositoryException;

	public abstract String[] getUserRoster(String owner) throws RepositoryException;

	public abstract void init();

	public void removeFromRootCollection(String nodeName) throws RepositoryException;

	public abstract void update(String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException;

	public void update(String nodeName, IAffiliations affiliations) throws RepositoryException;

	public void update(String nodeName, ISubscriptions subscriptions) throws RepositoryException;

}