import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.NodeAffiliations;
import tigase.pubsub.repository.NodeSubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;

public class MockRepository implements IPubSubRepository {

	private static class Item {
		private String id;
		public Element data;
		public Date creationDate;
		public Date updateDate;
		public String publisher;

	}

	private static class Node {
		private String name;
		private AbstractNodeConfig nodeConfig;
		private NodeAffiliations nodeAffiliations = NodeAffiliations.create("");
		private NodeSubscriptions nodeSubscriptions = NodeSubscriptions.create("");
		private Map<String, Item> items = new HashMap<String, Item>();
	}

	private final Map<String, Node> nodes = new HashMap<String, Node>();

	private final Set<String> rootCollection = new HashSet<String>();

	@Override
	public void addToRootCollection(String nodeName) throws RepositoryException {
		this.rootCollection.add(nodeName);
	}

	@Override
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		Node node = new Node();
		node.name = nodeName;
		nodeConfig.setNodeType(nodeType);
		node.nodeConfig = nodeConfig;
		nodes.put(nodeName, node);
	}

	@Override
	public void deleteNode(String nodeName) throws RepositoryException {
		this.nodes.remove(nodeName);
	}

	@Override
	public void forgetConfiguration(String nodeName) throws RepositoryException {
	}

	@Override
	public String[] getBuddyGroups(String owner, String bareJid) throws RepositoryException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getBuddySubscription(String owner, String buddy) throws RepositoryException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IAffiliations getNodeAffiliations(String nodeName) throws RepositoryException {
		Node n = this.nodes.get(nodeName);
		return n != null ? NodeAffiliations.create(n.nodeAffiliations.serialize()) : null;
	}

	@Override
	public AbstractNodeConfig getNodeConfig(String nodeName) throws RepositoryException {
		Node n = this.nodes.get(nodeName);
		if (n != null) {
			if (n.nodeConfig.getNodeType() == NodeType.leaf) {
				LeafNodeConfig r = new LeafNodeConfig(nodeName, n.nodeConfig);
				return r;
			} else {
				CollectionNodeConfig r = new CollectionNodeConfig(nodeName);
				r.copyFrom(n.nodeConfig);
				return r;
			}
		}
		return null;
	}

	@Override
	public ISubscriptions getNodeSubscriptions(String nodeName) throws RepositoryException {
		Node n = this.nodes.get(nodeName);
		return n != null ? NodeSubscriptions.create(n.nodeSubscriptions.serialize()) : null;
	}

	@Override
	public IPubSubDAO getPubSubDAO() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getRootCollection() throws RepositoryException {
		return this.rootCollection.toArray(new String[] {});
	}

	@Override
	public String[] getUserRoster(String owner) throws RepositoryException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init() {
	}

	@Override
	public void removeFromRootCollection(String nodeName) throws RepositoryException {
		this.rootCollection.remove(nodeName);
	}

	@Override
	public void update(String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException {
		Node n = this.nodes.get(nodeName);
		if (n != null) {
			n.nodeConfig.copyFrom(nodeConfig);
		}
	}

	@Override
	public void update(String nodeName, IAffiliations affiliations) throws RepositoryException {
		Node n = this.nodes.get(nodeName);
		if (n != null) {
			n.nodeAffiliations = NodeAffiliations.create(affiliations.serialize(true));
		}
	}

	@Override
	public void update(String nodeName, ISubscriptions subscriptions) throws RepositoryException {
		Node n = this.nodes.get(nodeName);
		if (n != null) {
			n.nodeSubscriptions = NodeSubscriptions.create(subscriptions.serialize(true));
		}
	}

	@Override
	public IItems getNodeItems(String nodeName) throws RepositoryException {
		// TODO Auto-generated method stub
		return null;
	}

}
