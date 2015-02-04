package tigase.pubsub.repository.cached;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.pubsub.utils.FragmentedMap;
import tigase.stats.StatisticsList;
import tigase.xmpp.BareJID;
import tigase.xmpp.impl.roster.RosterElement;

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:20:46 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class CachedPubSubRepository<T> implements IPubSubRepository {

	private class NodeSaver {

		public void save(Node<T> node) throws RepositoryException {
			save(node, 0);
		}
		
		public void save(Node<T> node, int iteration) throws RepositoryException {
			long start = System.currentTimeMillis();

			++repo_writes;

			// Prevent node modifications while it is being written to DB
			// From 3.0.0 this should not be needed as we keep changes to the node per thread
//			synchronized (node) {
				try {
					if (node.isDeleted()) {
						return;
					}

					if (node.configNeedsWriting()) {
						String collection = node.getNodeConfig().getCollection();
						T collectionId = null;
						if (collection != null && !collection.equals("")) {
							collectionId = dao.getNodeId(node.getServiceJid(), collection);
							if (collectionId == null) {
								throw new RepositoryException("Parent collection does not exists yet!");
							}							
						}					
						dao.updateNodeConfig(node.getServiceJid(), node.getNodeId(),
								node.getNodeConfig().getFormElement().toString(), 
								collectionId);
						node.configSaved();
					}
					
					if (node.affiliationsNeedsWriting()) {
						Map<BareJID,UsersAffiliation> changedAffiliations = node.getNodeAffiliations().getChanged();
						for (Map.Entry<BareJID,UsersAffiliation> entry : changedAffiliations.entrySet()) {
							dao.updateNodeAffiliation(node.getServiceJid(), node.getNodeId(), node.getName(), entry.getValue());
						}
						node.affiliationsSaved();
					}

					if (node.subscriptionsNeedsWriting()) {
//						for (Integer deletedIndex : fm.getRemovedFragmentIndexes()) {
//							dao.removeSubscriptions(node.getServiceJid(), node.getName(), deletedIndex);
//						}
//
//						for (Integer changedIndex : fm.getChangedFragmentIndexes()) {
//							Map<BareJID, UsersSubscription> ft = fm.getFragment(changedIndex);
//
//							dao.updateSubscriptions(node.getServiceJid(), node.getName(), changedIndex,
//									node.getNodeSubscriptions().serialize(ft));
//						}
						Map<BareJID,UsersSubscription> changedSubscriptions = node.getNodeSubscriptions().getChanged();
						for (Map.Entry<BareJID,UsersSubscription> entry : changedSubscriptions.entrySet()) {
							UsersSubscription subscription = entry.getValue();
							if (subscription.getSubscription() == Subscription.none) {
								dao.removeNodeSubscription(node.getServiceJid(), node.getNodeId(), subscription.getJid());
							}
							else {
								dao.updateNodeSubscription(node.getServiceJid(), node.getNodeId(), node.getName(), subscription);
							}
						}
						node.subscriptionsSaved();
					}
				} catch (Exception e) {					
					log.log(Level.WARNING, "Problem saving pubsub data: ", e);
					// if we receive an exception here, I think we should clear any unsaved
					// changes (at least for affiliations and subscriptions) and propagate 
					// this exception to higher layer to return proper error response 
					//
					// should we do the same for configuration?
					node.resetChanges(); 
					throw new RepositoryException("Problem saving pubsub data", e);
				}

				// If the node still needs writing to the database put
				// it back to the collection
				if (node.needsWriting()) {
					if (iteration >= 10) {
						String msg = "Was not able to save data for node " + node.getName() 
								+ " on " + iteration + " iteration"
								+ ", config saved = " + (!node.configNeedsWriting())
								+ ", affiliations saved = " + (!node.affiliationsNeedsWriting())
								+ ", subscriptions saved = " + (!node.subscriptionsNeedsWriting());
						log.log(Level.WARNING, msg);
						throw new RepositoryException("Problem saving pubsub data");
					}
					save(node, iteration++);
				}
//			}

			long end = System.currentTimeMillis();

			writingTime += (end - start);						
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
			return (size() > maxCacheSize) && !eldest.getValue().needsWriting();
		}
	}

	/** Field description */
	public final static long MAX_WRITE_DELAY = 1000l * 15l;
	protected final IPubSubDAO<T> dao;
	protected Logger log = Logger.getLogger(this.getClass().getName());
	private final Integer maxCacheSize;
	// private final Object mutex = new Object();
	protected final Map<String, Node> nodes;
	private long nodes_added = 0;
	private final ConcurrentSkipListSet<Node> nodesToSave = new ConcurrentSkipListSet<Node>(new NodeComparator());

	private long repo_writes = 0;

	private final ConcurrentHashMap<BareJID,Set<String>> rootCollection = new ConcurrentHashMap<BareJID,Set<String>>();
	private NodeSaver nodeSaver;

	// private final Object writeThreadMutex = new Object();

	private long updateSubscriptionsCalled = 0;

	private long writingTime = 0;

	public CachedPubSubRepository(final PubSubDAO dao, final Integer maxCacheSize) {	
		this.dao = dao;
		this.maxCacheSize = maxCacheSize;
		nodes = Collections.synchronizedMap(new SizedCache(this.maxCacheSize));

		// Runtime.getRuntime().addShutdownHook(makeLazyWriteThread(true));
		log.config("Initializing Cached Repository with cache size = " + ((maxCacheSize == null) ? "OFF" : maxCacheSize));
		
 		nodeSaver = new NodeSaver();

		// Thread.dumpStack();
	}

	public void addStats(final String name, final StatisticsList stats) {
		if (this.nodes.size() > 0) {
			stats.add(name, "Cached nodes", this.nodes.size(), Level.FINE);
		} else {
			stats.add(name, "Cached nodes", this.nodes.size(), Level.FINEST);
		}

		if (this.nodesToSave.size() > 0) {
			stats.add(name, "Unsaved nodes", this.nodesToSave.size(), Level.INFO);
		} else {
			stats.add(name, "Unsaved nodes", this.nodesToSave.size(), Level.FINEST);
		}

		long subscriptionsCount = 0;
		long affiliationsCount = 0;

		// synchronized (mutex) {
		Map<String, Node> tmp = null;

		synchronized (nodes) {
			tmp = new LinkedHashMap<String, Node>(nodes);
		}

		for (Node nd : tmp.values()) {
			subscriptionsCount += nd.getNodeSubscriptions().getSubscriptionsMap().size();
			affiliationsCount += nd.getNodeAffiliations().getAffiliationsMap().size();
		}

		// }

		if (updateSubscriptionsCalled > 0) {
			stats.add(name, "Update subscriptions calls", updateSubscriptionsCalled, Level.FINE);
		} else {
			stats.add(name, "Update subscriptions calls", updateSubscriptionsCalled, Level.FINEST);
		}

		if (subscriptionsCount > 0) {
			stats.add(name, "Subscriptions count (in cache)", subscriptionsCount, Level.FINE);
		} else {
			stats.add(name, "Subscriptions count (in cache)", subscriptionsCount, Level.FINEST);
		}

		if (affiliationsCount > 0) {
			stats.add(name, "Affiliations count (in cache)", affiliationsCount, Level.FINE);
		} else {
			stats.add(name, "Affiliations count (in cache)", affiliationsCount, Level.FINEST);
		}

		if (repo_writes > 0) {
			stats.add(name, "Repository writes", repo_writes, Level.FINE);
		} else {
			stats.add(name, "Repository writes", repo_writes, Level.FINEST);
		}

		if (nodes_added > 0) {
			stats.add(name, "Added new nodes", nodes_added, Level.INFO);
		} else {
			stats.add(name, "Added new nodes", nodes_added, Level.FINEST);
		}

		if (nodes_added > 0) {
			stats.add(name, "Total writing time", Utils.longToTime(writingTime), Level.INFO);
		} else {
			stats.add(name, "Total writing time", Utils.longToTime(writingTime), Level.FINEST);
		}

		if (nodes_added + repo_writes > 0) {
			if (nodes_added > 0) {
				stats.add(name, "Average DB write time [ms]", (writingTime / (nodes_added + repo_writes)), Level.INFO);
			} else {
				stats.add(name, "Average DB write time [ms]", (writingTime / (nodes_added + repo_writes)), Level.FINEST);
			}
		}
	}

	@Override
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "Addint to root collection, serviceJid: {0}, nodeName: {1}",
							 new Object[] { serviceJid, nodeName } );
		}
		this.dao.addToRootCollection(serviceJid, nodeName);
		
		this.getRootCollectionSet(serviceJid).add(nodeName);
	}

	protected String createKey(BareJID serviceJid, String nodeName) {
		return serviceJid.toString() + "/" + nodeName;
	}

	@Override
	public void createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
			NodeType nodeType, String collection) throws RepositoryException {

		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "Creating node, serviceJid: {0}, nodeName: {1}, ownerJid: {2}, nodeConfig: {3}, nodeType: {4}, collection: {5}",
							 new Object[] { serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collection } );
		}
		long start = System.currentTimeMillis();
		T collectionId = null;
		if (collection != null && !collection.equals("")) {
			collectionId = this.dao.getNodeId(serviceJid, collection);
			if (collectionId == null) {
				throw new RepositoryException("Parent collection does not exists yet!");
			}
		}
		
		T nodeId = this.dao.createNode(serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collectionId);

		NodeAffiliations nodeAffiliations = tigase.pubsub.repository.NodeAffiliations.create((Queue<UsersAffiliation>) null);
		NodeSubscriptions nodeSubscriptions = tigase.pubsub.repository.NodeSubscriptions.create();
		Node node = new Node(nodeId, serviceJid, nodeConfig, nodeAffiliations, nodeSubscriptions);

		String key = createKey(serviceJid, nodeName);
		this.nodes.put(key, node);

		long end = System.currentTimeMillis();

		++nodes_added;
		writingTime += (end - start);
	}

	protected NodeSubscriptions wrapNodeSubscriptions(tigase.pubsub.repository.NodeSubscriptions nodeSubscriptions) {
		return new NodeSubscriptions(nodeSubscriptions);
	}
	
	@Override
	public void deleteNode(BareJID serviceJid, String nodeName) throws RepositoryException {
		String key = createKey(serviceJid, nodeName);
		Node<T> node = this.nodes.get(key);
		T nodeId = node != null ? node.getNodeId() : dao.getNodeId(serviceJid, nodeName);

		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "Getting node items, serviceJid: {0}, nodeName: {1}, key: {2}, node: {3}, nodeId: {4}",
							 new Object[] { serviceJid, nodeName, key, node, nodeId } );
		}

		this.dao.deleteNode(serviceJid, nodeId);

		if (node != null) {
			node.setDeleted(true);
		}

		this.nodes.remove(key);
	}

	@Override
	public void destroy() {

		// No resources have been allocated by the init, but some resources
		// have been allocated in the contructor....
	}

	@Override
	public void forgetConfiguration(BareJID serviceJid, String nodeName) throws RepositoryException {
		String key = createKey(serviceJid, nodeName);
		this.nodes.remove(key);
	}

	public Collection<Node> getAllNodes() {
		return Collections.unmodifiableCollection(nodes.values());
	}

	@Override
	public String[] getBuddyGroups(BareJID owner, BareJID bareJid) throws RepositoryException {
		return this.dao.getBuddyGroups(owner, bareJid);
	}

	@Override
	public String getBuddySubscription(BareJID owner, BareJID buddy) throws RepositoryException {
		return this.dao.getBuddySubscription(owner, buddy);
	}

	protected Node getNode(BareJID serviceJid, String nodeName) throws RepositoryException {
		String key = createKey(serviceJid, nodeName);
		Node<T> node = this.nodes.get(key);

		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "Getting node, serviceJid: {0}, nodeName: {1}, key: {2}, node: {3}",
							 new Object[] { serviceJid, nodeName, key, node } );
		}

		if (node == null) {
			T nodeId = this.dao.getNodeId(serviceJid, nodeName);
			if (nodeId == null) {
				return null;
			}
			String cfgData = this.dao.getNodeConfig(serviceJid, nodeId);
			AbstractNodeConfig nodeConfig = this.dao.parseConfig(nodeName, cfgData);

			if (nodeConfig == null) {
				return null;
			}

			NodeAffiliations nodeAffiliations = new NodeAffiliations(this.dao.getNodeAffiliations(serviceJid, nodeId));
			NodeSubscriptions nodeSubscriptions = wrapNodeSubscriptions(this.dao.getNodeSubscriptions(serviceJid, nodeId));

			node = new Node(nodeId, serviceJid, nodeConfig, nodeAffiliations, nodeSubscriptions);

			// if (maxCacheSize != null && this.nodes.size() > maxCacheSize) {
			// Iterator<Entry<String, Node>> it =
			// this.nodes.entrySet().iterator();
			// int count = 0;
			// while (it.hasNext() && count < 10) {
			// Entry<String, Node> e = it.next();
			// if (nodesToSave.contains(e.getValue())) {
			// continue;
			// }
			// count++;
			// it.remove();
			// }
			//
			// }
			this.nodes.put(key, node);
		}

		return node;
	}
	
	@Override
	public IAffiliations getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException {
		Node node = getNode(serviceJid, nodeName);

		return (node == null) ? null : node.getNodeAffiliations();
	}

	@Override
	public AbstractNodeConfig getNodeConfig(BareJID serviceJid, String nodeName) throws RepositoryException {
		Node node = getNode(serviceJid, nodeName);

		try {
			return (node == null) ? null : node.getNodeConfig().clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();

			return null;
		}
	}

	@Override
	public IItems getNodeItems(BareJID serviceJid, String nodeName) throws RepositoryException {
		String key = createKey(serviceJid, nodeName);
		Node<T> node = this.nodes.get(key);		
		T nodeId = node != null ? node.getNodeId() : dao.getNodeId(serviceJid, nodeName);
		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "Getting node items, serviceJid: {0}, nodeName: {1}, key: {2}, node: {3}, nodeId: {4}",
							 new Object[] { serviceJid, nodeName, key, node, nodeId } );
		}
		return new Items(nodeId, serviceJid, nodeName, this.dao);
	}

	@Override
	public ISubscriptions getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException {
		Node node = getNode(serviceJid, nodeName);

		return (node == null) ? null : node.getNodeSubscriptions();
	}

	@Override
	public IPubSubDAO getPubSubDAO() {
		return this.dao;
	}

	@Override
	public String[] getRootCollection(BareJID serviceJid) throws RepositoryException {
		Set<String> rootCollection = getRootCollectionSet(serviceJid);
		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "Getting root collection, serviceJid: {0}",
							 new Object[] { serviceJid } );
		}
		if (rootCollection == null)
			return null;
		return rootCollection.toArray(new String[rootCollection.size()]);
	}
	
	protected Set<String> getRootCollectionSet(BareJID serviceJid) throws RepositoryException {
		Set<String> rootCollection = this.rootCollection.get(serviceJid);
		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "Getting root collection, serviceJid: {0}",
							 new Object[] { serviceJid } );
		}
		if (rootCollection == null || rootCollection.isEmpty()) {
			if (rootCollection == null) {
				Set<String> oldRootCollection = this.rootCollection.putIfAbsent(serviceJid, Collections.synchronizedSet(new HashSet<String>()));				
				if (oldRootCollection != null) {
					rootCollection = oldRootCollection;
				}
			}
			String[] x = dao.getChildNodes(serviceJid, null);

			if (rootCollection == null) {
				rootCollection = Collections.synchronizedSet(new HashSet<String>());
			}
			if (x != null) {
				for (String string : x) {
					rootCollection.add(string);
				}
			}
		}
		return rootCollection;
	}

	@Override
	public Map<BareJID,RosterElement> getUserRoster(BareJID owner) throws RepositoryException {
		return this.dao.getUserRoster(owner);
	}

	@Override
	public Map<String,UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID userJid) throws RepositoryException {
		return this.dao.getUserSubscriptions(serviceJid, userJid);
	}
	
	@Override
	public void init() {
		log.config("Cached PubSubRepository initialising...");
	}

	@Override
	public void removeFromRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		String key = createKey(serviceJid, nodeName);
		Node<T> node = this.nodes.get(key);		
		T nodeId = node != null ? node.getNodeId() : dao.getNodeId(serviceJid, nodeName);
		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "Getting node items, serviceJid: {0}, nodeName: {1}, key: {2}, node: {3}, nodeId: {4}",
							 new Object[] { serviceJid, nodeName, key, node, nodeId } );
		}
		dao.removeFromRootCollection(serviceJid, nodeId);
		Set<String> nodes = rootCollection.get(serviceJid);
		if (nodes != null) {
			nodes.remove(nodeName);
		}
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException {
		Node node = getNode(serviceJid, nodeName);

		if (node != null) {
			node.configCopyFrom(nodeConfig);

			// node.setNodeConfigChangeTimestamp();
			// synchronized (mutex) {
			log.finest("Node '" + nodeName + "' added to lazy write queue (config)");
			nodeSaver.save(node);
			// }
		}
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, IAffiliations nodeAffiliations) throws RepositoryException {
		if (nodeAffiliations instanceof NodeAffiliations) {
			Node node = getNode(serviceJid, nodeName);

			if (node != null) {
				if (node.getNodeAffiliations() != nodeAffiliations) {
					throw new RuntimeException("INCORRECT");
				}

				// node.setNodeAffiliationsChangeTimestamp();
				// synchronized (mutex) {
				log.finest("Node '" + nodeName + "' added to lazy write queue (affiliations)");
				nodeSaver.save(node);

				// }
			}
		} else {
			throw new RuntimeException("Wrong class");
		}
	}

	@Override
	public void update(BareJID serviceJid, String nodeName, ISubscriptions nodeSubscriptions) throws RepositoryException {
		++updateSubscriptionsCalled;
		Node node = getNode(serviceJid, nodeName);

		if (node != null) {
			// node.setNodeSubscriptionsChangeTimestamp();
			// synchronized (mutex) {
			log.finest("Node '" + nodeName + "' added to lazy write queue (subscriptions)");
			nodeSaver.save(node);
			// }
		}
	}
}
