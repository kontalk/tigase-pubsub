package tigase.pubsub.repository.stateless;

import java.util.ArrayList;
import java.util.List;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.PubSubRepositoryListener;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.inmemory.NodeAffiliation;
import tigase.pubsub.repository.inmemory.Subscriber;
import tigase.xml.Element;

public class StatelessPubSubRepository implements IPubSubRepository {

	private final IPubSubDAO dao;

	private final List<PubSubRepositoryListener> listeners = new ArrayList<PubSubRepositoryListener>();

	public StatelessPubSubRepository(IPubSubDAO pubSubDB, PubSubConfig pubSubConfig) {
		this.dao = pubSubDB;
	}

	@Override
	public void addListener(PubSubRepositoryListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public String addSubscriberJid(String nodeName, String jid, Affiliation affiliation, Subscription subscription)
			throws RepositoryException {
		return this.dao.addSubscriberJid(nodeName, jid, affiliation, subscription);
	}

	@Override
	public void changeAffiliation(String nodeName, String jid, Affiliation affiliation) throws RepositoryException {
		this.dao.changeAffiliation(nodeName, jid, affiliation);
	}

	@Override
	public void changeSubscription(String nodeName, String jid, Subscription subscription) throws RepositoryException {
		this.dao.changeSubscription(nodeName, jid, subscription);
	}

	@Override
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		this.dao.createNode(nodeName, ownerJid, nodeConfig, nodeType, collection);
		if (!"".equals(nodeConfig.getCollection())) {
			fireNewNodeCollection(nodeName, null, collection);
		}
	}

	@Override
	public void deleteItem(String nodeName, String id) throws RepositoryException {
		this.dao.deleteItem(nodeName, id);
	}

	@Override
	public void deleteNode(String nodeName) throws RepositoryException {
		this.dao.deleteNode(nodeName);
	}

	private void fireNewNodeCollection(String nodeName, String oldCollectionName, String newCollectionName) {
		for (PubSubRepositoryListener listener : this.listeners) {
			listener.onChangeCollection(nodeName, oldCollectionName, newCollectionName);
		}
	}

	@Override
	public void forgetConfiguration(String nodeName) throws RepositoryException {
	}

	@Override
	public NodeAffiliation[] getAffiliations(String nodeName) throws RepositoryException {
		String[] jids = this.dao.getAffiliations(nodeName);
		List<NodeAffiliation> result = new ArrayList<NodeAffiliation>();
		if (jids != null) {
			for (String jid : jids) {
				Affiliation affiliation = this.dao.getSubscriberAffiliation(nodeName, jid);
				NodeAffiliation na = new NodeAffiliation(jid, affiliation);
				result.add(na);
			}
		}
		return result.toArray(new NodeAffiliation[] {});
	}

	@Override
	public String[] getBuddyGroups(String owner, String bareJid) throws RepositoryException {
		return this.dao.getBuddyGroups(owner, bareJid);
	}

	@Override
	public String getBuddySubscription(String owner, String buddy) throws RepositoryException {
		return this.dao.getBuddySubscription(owner, buddy);
	}

	@Override
	public Element getItem(String nodeName, String id) throws RepositoryException {
		return this.dao.getItem(nodeName, id);
	}

	@Override
	public String getItemCreationDate(String nodeName, String id) throws RepositoryException {
		return this.dao.getItemCreationDate(nodeName, id);
	}

	@Override
	public String[] getItemsIds(String nodeName) throws RepositoryException {
		return this.dao.getItemsIds(nodeName);
	}

	@Override
	public AbstractNodeConfig getNodeConfig(String nodeName) throws RepositoryException {
		return this.dao.getNodeConfig(nodeName);
	}

	@Override
	public String[] getNodesList() throws RepositoryException {
		return this.dao.getNodesList();
	}

	@Override
	public IPubSubDAO getPubSubDAO() {
		return this.dao;
	}

	@Override
	public NodeAffiliation getSubscriberAffiliation(String nodeName, String jid) throws RepositoryException {
		Affiliation affiliation = this.dao.getSubscriberAffiliation(nodeName, jid);
		NodeAffiliation na = new NodeAffiliation(jid, affiliation);
		return na;
	}

	@Override
	public Subscription getSubscription(String nodeName, String jid) throws RepositoryException {
		return this.dao.getSubscription(nodeName, jid);
	}

	@Override
	public String getSubscriptionId(String nodeName, String jid) throws RepositoryException {
		return this.dao.getSubscriptionId(nodeName, jid);
	}

	@Override
	public Subscriber[] getSubscriptions(String nodeName) throws RepositoryException {
		String[] jids = this.dao.getSubscriptions(nodeName);
		List<Subscriber> result = new ArrayList<Subscriber>();
		if (jids != null)
			for (String jid : jids) {
				String subId = this.dao.getSubscriptionId(nodeName, jid);
				Subscription subscription = this.dao.getSubscription(nodeName, jid);
				Subscriber s = new Subscriber(jid, subId, subscription);
				result.add(s);
			}
		return result.toArray(new Subscriber[] {});
	}

	@Override
	public String[] getUserRoster(String owner) throws RepositoryException {
		return this.dao.getUserRoster(owner);
	}

	@Override
	public void init() {
	}

	@Override
	public void removeListener(PubSubRepositoryListener listener) {
		this.listeners.remove(listener);
	}

	@Override
	public void removeSubscriber(String nodeName, String jid) throws RepositoryException {
		this.dao.removeSubscriber(nodeName, jid);
	}

	@Override
	public void setNewNodeCollection(String nodeName, String collectionNew) throws RepositoryException {
		final String oldCollectionName = dao.getCollectionOf(nodeName);
		this.dao.setNewNodeCollection(nodeName, collectionNew);
		fireNewNodeCollection(nodeName, oldCollectionName, collectionNew);
	}

	@Override
	public void update(String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException {
		final String oldCollectionName = dao.getCollectionOf(nodeName);
		this.dao.update(nodeName, nodeConfig);
		final String newCollectionName = dao.getCollectionOf(nodeName);
		if (!oldCollectionName.equals(newCollectionName)) {
			fireNewNodeCollection(nodeName, oldCollectionName, newCollectionName);
		}
	}

	@Override
	public void writeItem(String nodeName, long timeInMilis, String id, String publisher, Element item) throws RepositoryException {
		this.dao.writeItem(nodeName, timeInMilis, id, publisher, item);
	}

}
