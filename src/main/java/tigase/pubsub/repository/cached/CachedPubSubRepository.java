package tigase.pubsub.repository.cached;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
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
import tigase.stats.StatRecord;

public class CachedPubSubRepository implements IPubSubRepository {

	public final static long MAX_WRITE_DELAY = 1000l * 15l;

	private final PubSubDAO dao;

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private final Integer maxCacheSize;

//	private final Object mutex = new Object();

	private final Map<String, Node> nodes;

	private final ConcurrentSkipListSet<Node> nodesToSave =
					new ConcurrentSkipListSet<Node>(new NodeComparator());

	private final Set<String> rootCollection = new HashSet<String>();

	private LazyWriteThread tlazyWriteThread;

	//private final Object writeThreadMutex = new Object();

	public CachedPubSubRepository(final PubSubDAO dao, final Integer maxCacheSize) {
		this.dao = dao;
		this.maxCacheSize = maxCacheSize;
		nodes = Collections.synchronizedMap(new SizedCache(this.maxCacheSize));
		//Runtime.getRuntime().addShutdownHook(makeLazyWriteThread(true));
		log.config("Initializing Cached Repository with cache size = " + (maxCacheSize == null ? "OFF" : maxCacheSize));
		tlazyWriteThread = makeLazyWriteThread(false);
		Thread x = new Thread(tlazyWriteThread);
		x.setName("PubSub-DataWriter");
		x.setDaemon(true);
		x.start();
		//Thread.dumpStack();
	}

	public void addStats(final String name, final List<StatRecord> stats) {
		if (this.nodes.size() > 0) {
			stats.add(new StatRecord(name, "Cached nodes", "long",
							this.nodes.size(), Level.INFO));
		} else {
			stats.add(new StatRecord(name, "Cached nodes", "long",
							this.nodes.size(), Level.FINEST));
		}
		if (this.nodesToSave.size() > 0) {
			stats.add(new StatRecord(name, "Unsaved nodes", "long",
							this.nodesToSave.size(), Level.INFO));
		} else {
			stats.add(new StatRecord(name, "Unsaved nodes", "long",
							this.nodesToSave.size(), Level.FINEST));
		}

		long subscriptionsCount = 0;
		long affiliationsCount = 0;

//		synchronized (mutex) {
		Map<String, Node> tmp = new LinkedHashMap<String, Node>(nodes);
		for (Node nd : tmp.values()) {
			subscriptionsCount +=
							nd.getNodeSubscriptions().getSubscriptionsMap().size();
			affiliationsCount += nd.getNodeAffiliations().getAffiliationsMap().size();
		}
//		}

		if (subscriptionsCount > 0) {
			stats.add(new StatRecord(name, "Subscriptions count (in cache)", "long",
							subscriptionsCount, Level.INFO));
		} else {
			stats.add(new StatRecord(name, "Subscriptions count (in cache)", "long",
							subscriptionsCount, Level.FINEST));
		}
		if (affiliationsCount > 0) {
			stats.add(new StatRecord(name, "Affiliations count (in cache)", "long",
							affiliationsCount, Level.INFO));
		} else {
			stats.add(new StatRecord(name, "Affiliations count (in cache)", "long",
							affiliationsCount, Level.FINEST));
		}
	}

	@Override
	public void addToRootCollection(String nodeName) throws RepositoryException {
		this.dao.addToRootCollection(nodeName);
		this.rootCollection.add(nodeName);
	}

	@Override
	public void createNode(String nodeName, String ownerJid,
					AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		this.dao.createNode(nodeName, ownerJid, nodeConfig, nodeType, collection);

		NodeAffiliations nodeAffiliations =
						new NodeAffiliations(NodeAffiliations.create(null));
		NodeSubscriptions nodeSubscriptions =
						new NodeSubscriptions(NodeSubscriptions.create());
		Node node = new Node(nodeConfig, nodeAffiliations, nodeSubscriptions);
		this.nodes.put(nodeName, node);

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

//	public void doLazyWrite() {
//		synchronized (writeThreadMutex) {
//			if (tlazyWriteThread == null) {
//				tlazyWriteThread = makeLazyWriteThread(false);
//				Thread x = new Thread(tlazyWriteThread);
//				x.setName("PubSub-DataWriter");
//				x.start();
//			}
//		}
//	}

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

//			if (maxCacheSize != null && this.nodes.size() > maxCacheSize) {
//				Iterator<Entry<String, Node>> it = this.nodes.entrySet().iterator();
//				int count = 0;
//				while (it.hasNext() && count < 10) {
//					Entry<String, Node> e = it.next();
//					if (nodesToSave.contains(e.getValue())) {
//						continue;
//					}
//					count++;
//					it.remove();
//				}
//
//			}

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
		return rootCollection.toArray(new String[rootCollection.size()]);
	}

	@Override
	public String[] getUserRoster(String owner) throws RepositoryException {
		return this.dao.getUserRoster(owner);
	}

	@Override
	public void init() {
		log.config("Cached PubSubRepository initialising...");
	}

	@Override
	public void destroy() {
		// No resources have been allocated by the init, but some resources
		// have been allocated in the contructor....
		tlazyWriteThread.stop();
	}

	private LazyWriteThread makeLazyWriteThread(final boolean immediatelly) {
		//Thread.dumpStack();
		return new LazyWriteThread();
	}

	@Override
	public void removeFromRootCollection(String nodeName) throws RepositoryException {
		dao.removeFromRootCollection(nodeName);
		rootCollection.remove(nodeName);
	}

	@Override
	public void update(String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException {
		Node node = getNode(nodeName);
		if (node != null) {
			node.configCopyFrom(nodeConfig);
//			node.setNodeConfigChangeTimestamp();
//			synchronized (mutex) {
				log.finest("Node '" + nodeName + "' added to lazy write queue (config)");
				nodesToSave.add(node);
			tlazyWriteThread.wakeup();
//			}
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
				node.affiliationsMerge();
//				node.setNodeAffiliationsChangeTimestamp();
//				synchronized (mutex) {
					log.finest("Node '" + nodeName + "' added to lazy write queue (affiliations)");
					nodesToSave.add(node);
				tlazyWriteThread.wakeup();
//				}
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
				node.subscriptionsMerge();
//				node.setNodeSubscriptionsChangeTimestamp();
//				synchronized (mutex) {
					log.finest("Node '" + nodeName + "' added to lazy write queue (subscriptions)");
					nodesToSave.add(node);
				tlazyWriteThread.wakeup();
//				}
			}
		} else {
			throw new RuntimeException("Wrong class");
		}
	}

	private class LazyWriteThread implements Runnable {

    private boolean stop = false;

		public LazyWriteThread() {}

		public void stop() {
			log.info("Stopping LazyWriteThread...");
			stop = true;
			wakeup();
		}

		public void wakeup() {
			synchronized (nodesToSave) {
				nodesToSave.notify();
			}
		}

		@Override
		public void run() {
			log.info("Started new LazyWriteThread.");
			while (!stop || nodesToSave.size() > 0) {
				Node node = nodesToSave.pollFirst();
				if (node != null) {
					// Prevent node modifications while it is being written to DB
					synchronized (node) {
						try {
							if (node.isDeleted())
								continue;
							if (node.affiliationsNeedsWriting()) {
								dao.updateAffiliations(node.getName(),
												node.getNodeAffiliations().serialize());
								node.affiliationsSaved();
							}
							if (node.subscriptionsNeedsWriting()) {
								FragmentedMap<String, UsersSubscription> fm =
												node.getNodeSubscriptions().getFragmentedMap();
								fm.defragment();
								for (Integer deletedIndex : fm.getRemovedFragmentIndexes()) {
									dao.removeSubscriptions(node.getName(), deletedIndex);
								}
								for (Integer changedIndex : fm.getChangedFragmentIndexes()) {
									Map<String, UsersSubscription> ft =
													fm.getFragment(changedIndex);
									dao.updateSubscriptions(node.getName(), changedIndex,
													node.getNodeSubscriptions().serialize(ft));
								}
								fm.cleanChangingLog();
								node.subscriptionsSaved();
							}
							if (node.configNeedsWriting()) {
								dao.updateNodeConfig(node.getName(),
												node.getNodeConfig().getFormElement().toString());
								node.configSaved();
							}
						} catch (Exception e) {
							log.log(Level.WARNING, "Problem saving pubsub data: ", e);
						}
						// If the node still needs writing to the database put it back to the collection
						if (node.needsWriting()) {
							nodesToSave.add(node);
						}
					}
				} else {
					if (!stop) {
						try {
							synchronized (nodesToSave) { nodesToSave.wait(); }
							// After awaking sleep for 1 more second to allow for building
							// up the buffer for saving. This improved performance.
							Thread.sleep(1000);
						} catch (InterruptedException ex) { }
					}
				}
			}
			log.info("Stopped LazyWriteThread...");
		}

	}

	private class NodeComparator implements Comparator<Node> {

		@Override
		public int compare(Node o1, Node o2) {
			if (o1.getCreationTime() < o2.getCreationTime()) {
				return -1;
			}
			if (o1.getCreationTime() > o2.getCreationTime()) {
				return 1;
			}
			return o1.getName().compareTo(o2.getName());
		}

	}

	private class SizedCache extends LinkedHashMap<String, Node> {

		private static final long serialVersionUID = 1L;

		private int maxCacheSize = 1000;

		public SizedCache(int maxSize) {
			super(maxSize, 0.1f, true);
			maxCacheSize = maxSize;
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Node> eldest) {
			return size() > maxCacheSize && !eldest.getValue().needsWriting();
		}

	}


}
