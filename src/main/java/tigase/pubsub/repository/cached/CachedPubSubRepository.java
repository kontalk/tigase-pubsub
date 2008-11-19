package tigase.pubsub.repository.cached;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.NodeAffiliations;
import tigase.pubsub.repository.NodeSubscriptions;
import tigase.pubsub.repository.RepositoryException;

public class CachedPubSubRepository implements IPubSubRepository {

	private final IPubSubDAO dao;

	public CachedPubSubRepository(final IPubSubDAO dao) {
		this.dao = dao;
	}

	private final Set<Node> nodesToSave = new HashSet<Node>();

	private final Map<String, Node> nodes = new HashMap<String, Node>();

	private final Set<String> rootCollection = new HashSet<String>();

	@Override
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		this.dao.createNode(nodeName, ownerJid, nodeConfig, nodeType, collection);
	}

	@Override
	public void addToRootCollection(String nodeName) throws RepositoryException {
		this.dao.addToRootCollection(nodeName);
		this.rootCollection.add(nodeName);
	}

	@Override
	public void deleteNode(String nodeName) throws RepositoryException {
		Node node = this.nodes.get(nodeName);
		this.dao.deleteNode(nodeName);
		if (node != null) {
			node.setDeleted(true);
		}
		this.nodes.remove(nodeName);
	}

	@Override
	public void forgetConfiguration(String nodeName) throws RepositoryException {
		this.nodes.remove(nodeName);
	}

	@Override
	public String[] getBuddyGroups(String owner, String bareJid) throws RepositoryException {
		return this.dao.getBuddyGroups(owner, bareJid);
	}

	@Override
	public String getBuddySubscription(String owner, String buddy) throws RepositoryException {
		return this.dao.getBuddySubscription(owner, buddy);
	}

	private Node getNode(String nodeName) throws RepositoryException {
		Node node = this.nodes.get(nodeName);
		if (node == null) {
			AbstractNodeConfig nodeConfig = this.dao.getNodeConfig(nodeName);
			if (nodeConfig == null)
				return null;
			NodeAffiliations nodeAffiliations = this.dao.getNodeAffiliations(nodeName);
			NodeSubscriptions nodeSubscriptions = this.dao.getNodeSubscriptions(nodeName);

			node = new Node(nodeConfig, nodeAffiliations, nodeSubscriptions);
			this.nodes.put(nodeName, node);
		}

		return node;
	}

	@Override
	public IAffiliations getNodeAffiliations(String nodeName) throws RepositoryException {
		Node node = getNode(nodeName);
		try {
			return node == null ? null : node.getNodeAffiliations().clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public AbstractNodeConfig getNodeConfig(String nodeName) throws RepositoryException {
		Node node = getNode(nodeName);
		try {
			return node == null ? null : node.getNodeConfig().clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public IItems getNodeItems(String nodeName) throws RepositoryException {
		return new Items(nodeName, this.dao);
	}

	@Override
	public ISubscriptions getNodeSubscriptions(String nodeName) throws RepositoryException {
		Node node = getNode(nodeName);
		try {
			return node == null ? null : node.getNodeSubscriptions().clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public IPubSubDAO getPubSubDAO() {
		return this.dao;
	}

	@Override
	public String[] getRootCollection() throws RepositoryException {
		if (rootCollection.size() == 0) {
			String[] x = dao.getRootNodes();
			for (String string : x) {
				rootCollection.add(string);
			}
		}
		return this.rootCollection.toArray(new String[] {});
	}

	@Override
	public String[] getUserRoster(String owner) throws RepositoryException {
		return this.dao.getUserRoster(owner);
	}

	@Override
	public void init() {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeFromRootCollection(String nodeName) throws RepositoryException {
		this.dao.removeFromRootCollection(nodeName);
		this.rootCollection.remove(nodeName);
	}

	@Override
	public void update(String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException {
		Node node = getNode(nodeName);
		if (node != null) {
			node.getNodeConfig().copyFrom(nodeConfig);
			node.setNodeConfigChangeTimestamp();
			synchronized (mutex) {
				this.nodesToSave.add(node);
			}
		}
	}

	@Override
	public void update(String nodeName, IAffiliations affiliations) throws RepositoryException {
		Node node = getNode(nodeName);
		if (node != null) {
			node.getNodeAffiliations().parse(affiliations.serialize(true));
			node.setNodeAffiliationsChangeTimestamp();
			synchronized (mutex) {
				this.nodesToSave.add(node);
			}
		}
	}

	@Override
	public void update(String nodeName, ISubscriptions subscriptions) throws RepositoryException {
		Node node = getNode(nodeName);
		if (node != null) {
			node.getNodeSubscriptions().parse(subscriptions.serialize(true));
			node.setNodeSubscriptionsChangeTimestamp();
			synchronized (mutex) {
				this.nodesToSave.add(node);
			}
		}
	}

	private final Object mutex = new Object();

	public final static long MAX_WRITE_DELAY = 1000l * 15l;

	public void doLazyWrite() throws RepositoryException {
		final long border = System.currentTimeMillis() - MAX_WRITE_DELAY;
		synchronized (mutex) {
			for (Node node : this.nodesToSave) {
				if (node.isDeleted())
					continue;
				if (node.getNodeAffiliationsChangeTimestamp() != null && node.getNodeAffiliationsChangeTimestamp() < border) {
					node.resetNodeAffiliationsChangeTimestamp();
					this.dao.update(node.getName(), node.getNodeAffiliations());
				}
				if (node.getNodeSubscriptionsChangeTimestamp() != null && node.getNodeSubscriptionsChangeTimestamp() < border) {
					node.resetNodeSubscriptionsChangeTimestamp();
					this.dao.update(node.getName(), node.getNodeSubscriptions());
				}
				if (node.getNodeConfigChangeTimestamp() != null && node.getNodeConfigChangeTimestamp() < border) {
					node.resetNodeConfigChangeTimestamp();
					this.dao.update(node.getName(), node.getNodeConfig());
				}
				if (node.getNodeConfigChangeTimestamp() == null && node.getNodeSubscriptionsChangeTimestamp() == null
						&& node.getNodeAffiliationsChangeTimestamp() == null) {
					this.nodesToSave.remove(node);
				}
			}
		}
	}
}
