package tigase.pubsub.repository.cached;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.pubsub.utils.FragmentedMap;

public class CachedPubSubRepository implements IPubSubRepository {

	public final static long MAX_WRITE_DELAY = 1000l * 15l;

	private final PubSubDAO dao;

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private final Object mutex = new Object();

	private final Map<String, Node> nodes = new HashMap<String, Node>();

	private final Set<Node> nodesToSave = new HashSet<Node>();

	private final Set<String> rootCollection = new HashSet<String>();

	private Thread tlazyWriteThread;

	private final Object writeThreadMutex = new Object();

	private final Integer maxCacheSize;

	public CachedPubSubRepository(final PubSubDAO dao, final Integer maxCacheSize) {
		this.dao = dao;
		this.maxCacheSize = maxCacheSize;
		Runtime.getRuntime().addShutdownHook(makeLazyWriteThread(true));

	}

	@Override
	public void addToRootCollection(String nodeName) throws RepositoryException {
		this.dao.addToRootCollection(nodeName);
		this.rootCollection.add(nodeName);
	}

	@Override
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		this.dao.createNode(nodeName, ownerJid, nodeConfig, nodeType, collection);
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

	public void doLazyWrite() {
		synchronized (writeThreadMutex) {
			if (tlazyWriteThread == null) {
				tlazyWriteThread = makeLazyWriteThread(false);
				tlazyWriteThread.start();
			}
		}
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
			NodeAffiliations nodeAffiliations = new NodeAffiliations(this.dao.getNodeAffiliations(nodeName));
			NodeSubscriptions nodeSubscriptions = new NodeSubscriptions(this.dao.getNodeSubscriptions(nodeName));

			node = new Node(nodeConfig, nodeAffiliations, nodeSubscriptions);

			if (maxCacheSize != null && this.nodes.size() > maxCacheSize) {
				Iterator<Entry<String, Node>> it = this.nodes.entrySet().iterator();
				int count = 0;
				while (it.hasNext() && count < 10) {
					Entry<String, Node> e = it.next();
					if (nodesToSave.contains(e.getValue())) {
						continue;
					}
					count++;
					it.remove();
				}

			}

			this.nodes.put(nodeName, node);
		}

		return node;
	}

	@Override
	public IAffiliations getNodeAffiliations(String nodeName) throws RepositoryException {
		Node node = getNode(nodeName);
		return node == null ? null : node.getNodeAffiliations();
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
		return node == null ? null : node.getNodeSubscriptions();
	}

	@Override
	public IPubSubDAO getPubSubDAO() {
		return this.dao;
	}

	@Override
	public String[] getRootCollection() throws RepositoryException {
		if (rootCollection.size() == 0) {
			String[] x = dao.getRootNodes();
			if (x != null)
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
		log.config("Cached PubSubRepository initialising...");
	}

	private Thread makeLazyWriteThread(final boolean immediatelly) {
		Thread x = new Thread() {
			@Override
			public void run() {
				super.run();
				System.out.print(". ");
				// synchronized (mutex) {
				if (immediatelly) {
					while (tlazyWriteThread != null)
						;
				}
				System.out.println("writing");
				final long border = System.currentTimeMillis() - (!immediatelly ? MAX_WRITE_DELAY : -10);
				log.fine("Lazy write thread running... (immediatelly:" + immediatelly + ")");
				try {
					Iterator<Node> nodes = nodesToSave.iterator();
					while (nodes.hasNext()) {
						Node node = nodes.next();
						System.out.println("->" + node.getName());
						if (node.isDeleted())
							continue;
						if (node.getNodeAffiliationsChangeTimestamp() != null && node.getNodeAffiliationsChangeTimestamp() < border) {
							node.resetNodeAffiliationsChangeTimestamp();

							dao.updateAffiliations(node.getName(), node.getNodeAffiliations().serialize());

						}
						if (node.getNodeSubscriptionsChangeTimestamp() != null && node.getNodeSubscriptionsChangeTimestamp() < border) {
							node.resetNodeSubscriptionsChangeTimestamp();
							System.out.println("save subscription");
							final FragmentedMap<String, UsersSubscription> fm = node.getNodeSubscriptions().getFragmentedMap();
							System.out.println("defragment");
							fm.defragment();

							for (Integer deletedIndex : fm.getRemovedFragmentIndexes()) {
								dao.removeSubscriptions(node.getName(), deletedIndex);
							}
							for (Integer changedIndex : fm.getChangedFragmentIndexes()) {
								System.out.println("changed: " + changedIndex);
								final Map<String, UsersSubscription> ft = fm.getFragment(changedIndex);
								dao.updateSubscriptions(node.getName(), changedIndex, node.getNodeSubscriptions().serialize(ft));
							}
							fm.cleanChangingLog();
						}
						if (node.getNodeConfigChangeTimestamp() != null && node.getNodeConfigChangeTimestamp() < border) {
							node.resetNodeConfigChangeTimestamp();
							dao.updateNodeConfig(node.getName(), node.getNodeConfig().getFormElement().toString());
						}
						if (node.getNodeConfigChangeTimestamp() == null && node.getNodeSubscriptionsChangeTimestamp() == null
								&& node.getNodeAffiliationsChangeTimestamp() == null) {
							nodes.remove();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					// TODO: handle exception
				}
				// }

				synchronized (writeThreadMutex) {
					tlazyWriteThread = null;
				}
			}
		};
		return x;
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
				log.finest("Node '" + nodeName + "' added to lazy write queue (config)");
				this.nodesToSave.add(node);
			}
		}
	}

	@Override
	public void update(String nodeName, IAffiliations nodeAffiliations) throws RepositoryException {
		if (nodeAffiliations instanceof NodeAffiliations) {
			NodeAffiliations affiliations = (NodeAffiliations) nodeAffiliations;

			Node node = getNode(nodeName);
			if (node != null) {
				if (node.getNodeAffiliations() != nodeAffiliations) {
					throw new RuntimeException("INCORRECT");
				}
				affiliations.merge();
				node.setNodeAffiliationsChangeTimestamp();
				synchronized (mutex) {
					log.finest("Node '" + nodeName + "' added to lazy write queue (affiliations)");
					this.nodesToSave.add(node);
				}
			}
		} else {
			throw new RuntimeException("Wrong class");
		}
	}

	@Override
	public void update(String nodeName, ISubscriptions nodeSubscriptions) throws RepositoryException {
		if (nodeSubscriptions instanceof NodeSubscriptions) {
			NodeSubscriptions subscriptions = (NodeSubscriptions) nodeSubscriptions;

			Node node = getNode(nodeName);
			if (node != null) {
				if (node.getNodeSubscriptions() != nodeSubscriptions) {
					throw new RuntimeException("INCORRECT");
				}
				subscriptions.merge();
				node.setNodeSubscriptionsChangeTimestamp();
				synchronized (mutex) {
					log.finest("Node '" + nodeName + "' added to lazy write queue (subscriptions)");
					this.nodesToSave.add(node);
				}
			}
		} else {
			throw new RuntimeException("Wrong class");
		}
	}
}
