package tigase.pubsub.repository.cached;

import org.junit.Test;
import tigase.db.DBInitException;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.repository.*;
import tigase.pubsub.repository.NodeAffiliations;
import tigase.pubsub.repository.NodeSubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Created by andrzej on 26.01.2017.
 */
public class CachedPubSubRepositoryTest {

	@Test
	public void test_lazyLoadingOfRootCollections() throws Exception {
		DummyPubSubDAO dao = new DummyPubSubDAO();
		dao.withDelay = true;
		CachedPubSubRepository cachedPubSubRepository = createCachedPubSubRepository(dao);
		cachedPubSubRepository.setDelayedRootCollectionLoading(true);

		BareJID serviceJid = BareJID.bareJIDInstanceNS("pubsub." + UUID.randomUUID() + ".local");
		String[] nodes = new String[10];
		for (int i=0; i<10; i++) {
			String node = "node-" + UUID.randomUUID().toString();
			nodes[i] = node;
			dao.addToRootCollection(serviceJid, node);
		}

		try {
			String[] result = cachedPubSubRepository.getRootCollection(serviceJid);
			assertFalse(true);
		} catch (CachedPubSubRepository.RootCollectionSet.IllegalStateException ex) {
			assertTrue(true);
		}

		for (int i=0; i<2; i++) {
			dao.rootCollections.get(serviceJid).remove(nodes[i]);
			cachedPubSubRepository.removeFromRootCollection(serviceJid, nodes[i]);
			String node = "node-" + UUID.randomUUID().toString();
			nodes[i] = node;
			cachedPubSubRepository.addToRootCollection(serviceJid, node);
		}
		Arrays.sort(nodes);

		Thread.sleep(1000);

		String[] result = cachedPubSubRepository.getRootCollection(serviceJid);
		Arrays.sort(result);

		assertArrayEquals(nodes, result);
	}


	@Test
	public void test_eagerLoadingOfRootCollections() throws Exception {
		DummyPubSubDAO dao = new DummyPubSubDAO();
		dao.withDelay = true;
		CachedPubSubRepository cachedPubSubRepository = createCachedPubSubRepository(dao);

		BareJID serviceJid = BareJID.bareJIDInstanceNS("pubsub." + UUID.randomUUID() + ".local");
		String[] nodes = new String[10];
		for (int i=0; i<10; i++) {
			String node = "node-" + UUID.randomUUID().toString();
			nodes[i] = node;
			dao.addToRootCollection(serviceJid, node);
		}

		Arrays.sort(nodes);

		new Thread(() -> {
			try {
				String[] result = cachedPubSubRepository.getRootCollection(serviceJid);
				assertArrayEquals(nodes, result);
			} catch (Exception ex) {
				assertFalse(true);
			}
		});
		
		String[] result = cachedPubSubRepository.getRootCollection(serviceJid);
		Arrays.sort(result);

		assertArrayEquals(nodes, result);
	}

	@Test
	public void test_userRemoved_lazy() throws Exception {
		DummyPubSubDAO dao = new DummyPubSubDAO();
		dao.withDelay = true;
		CachedPubSubRepository cachedPubSubRepository = createCachedPubSubRepository(dao);
		cachedPubSubRepository.setDelayedRootCollectionLoading(true);

		BareJID serviceJid = BareJID.bareJIDInstanceNS("pubsub." + UUID.randomUUID() + ".local");
		String[] nodes = new String[10];
		for (int i=0; i<10; i++) {
			String node = "node-" + UUID.randomUUID().toString();
			nodes[i] = node;
			dao.addToRootCollection(serviceJid, node);
		}

		try {
			cachedPubSubRepository.getRootCollection(serviceJid);
		} catch (Exception ex) {
		}
		Thread.sleep(1000);
		assertEquals(10, cachedPubSubRepository.getRootCollection(serviceJid).length);

		cachedPubSubRepository.onUserRemoved(serviceJid);

		try {
			cachedPubSubRepository.getRootCollection(serviceJid);
		} catch (Exception ex) {
		}
		Thread.sleep(1000);
		assertEquals(0, cachedPubSubRepository.getRootCollection(serviceJid).length);
		assertNull(dao.getChildNodes(serviceJid, null));
	}

	@Test
	public void test_userRemoved_eager() throws Exception {
		DummyPubSubDAO dao = new DummyPubSubDAO();
		dao.withDelay = true;
		CachedPubSubRepository cachedPubSubRepository = createCachedPubSubRepository(dao);

		BareJID serviceJid = BareJID.bareJIDInstanceNS("pubsub." + UUID.randomUUID() + ".local");
		String[] nodes = new String[10];
		for (int i=0; i<10; i++) {
			String node = "node-" + UUID.randomUUID().toString();
			nodes[i] = node;
			dao.addToRootCollection(serviceJid, node);
		}

		assertEquals(10, cachedPubSubRepository.getRootCollection(serviceJid).length);

		cachedPubSubRepository.onUserRemoved(serviceJid);

		assertEquals(0, cachedPubSubRepository.getRootCollection(serviceJid).length);
		assertNull(dao.getChildNodes(serviceJid, null));
	}

	protected CachedPubSubRepository createCachedPubSubRepository(PubSubDAO dao) {
		return new CachedPubSubRepository(dao, 2000);
	}

	public static class DummyPubSubDAO extends PubSubDAO {

		protected boolean withDelay;

		protected Map<BareJID,Set<String>> rootCollections = new ConcurrentHashMap<>();

		@Override
		public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
			synchronized (rootCollections) {
				Set<String> nodes = rootCollections.computeIfAbsent(serviceJid, bareJID -> new HashSet<String>());
				nodes.add(nodeName);
			}
		}

		@Override
		public Object createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
								 NodeType nodeType, Object collectionId) throws RepositoryException {
			return null;
		}

		@Override
		public void deleteItem(BareJID serviceJid, Object nodeId, String id) throws RepositoryException {

		}

		@Override
		public void deleteNode(BareJID serviceJid, Object nodeId) throws RepositoryException {

		}

		@Override
		public String[] getAllNodesList(BareJID serviceJid) throws RepositoryException {
			return new String[0];
		}

		@Override
		public Element getItem(BareJID serviceJid, Object nodeId, String id) throws RepositoryException {
			return null;
		}

		@Override
		public Date getItemCreationDate(BareJID serviceJid, Object nodeId, String id) throws RepositoryException {
			return null;
		}

		@Override
		public String[] getItemsIds(BareJID serviceJid, Object nodeId) throws RepositoryException {
			return new String[0];
		}

		@Override
		public String[] getItemsIdsSince(BareJID serviceJid, Object nodeId, Date since) throws RepositoryException {
			return new String[0];
		}

		@Override
		public List<IItems.ItemMeta> getItemsMeta(BareJID serviceJid, Object nodeId, String nodeName)
				throws RepositoryException {
			return null;
		}

		@Override
		public Date getItemUpdateDate(BareJID serviceJid, Object nodeId, String id) throws RepositoryException {
			return null;
		}

		@Override
		public NodeAffiliations getNodeAffiliations(BareJID serviceJid, Object nodeId) throws RepositoryException {
			return null;
		}

		@Override
		public String getNodeConfig(BareJID serviceJid, Object nodeId) throws RepositoryException {
			return null;
		}

		@Override
		public Object getNodeId(BareJID serviceJid, String nodeName) throws RepositoryException {
			return null;
		}

		@Override
		public INodeMeta getNodeMeta(BareJID serviceJid, String nodeName) throws RepositoryException {
			return null;
		}

		@Override
		public String[] getNodesList(BareJID serviceJid, String nodeName) throws RepositoryException {
			return new String[0];
		}

		@Override
		public NodeSubscriptions getNodeSubscriptions(BareJID serviceJid, Object nodeId) throws RepositoryException {
			return null;
		}

		@Override
		public String[] getChildNodes(BareJID serviceJid, String nodeName) throws RepositoryException {
			Set<String> nodes = rootCollections.get(serviceJid);
			sleep();
			return nodes == null ? null : nodes.toArray(new String[nodes.size()]);
		}

		@Override
		public Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid)
				throws RepositoryException {
			return null;
		}

		@Override
		public Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID jid)
				throws RepositoryException {
			return null;
		}

		@Override
		public void removeAllFromRootCollection(BareJID serviceJid) throws RepositoryException {

		}

		@Override
		public void removeService(BareJID serviceJid) throws RepositoryException {
			rootCollections.remove(serviceJid);
		}

		@Override
		public void removeFromRootCollection(BareJID serviceJid, Object nodeId) throws RepositoryException {
		}

		@Override
		public void removeNodeSubscription(BareJID serviceJid, Object nodeId, BareJID jid) throws RepositoryException {

		}

		@Override
		public void updateNodeConfig(BareJID serviceJid, Object nodeId, String serializedData, Object collectionId)
				throws RepositoryException {

		}

		@Override
		public void updateNodeAffiliation(BareJID serviceJid, Object nodeId, String nodeName,
										  UsersAffiliation userAffiliation) throws RepositoryException {

		}

		@Override
		public void updateNodeSubscription(BareJID serviceJid, Object nodeId, String nodeName,
										   UsersSubscription userSubscription) throws RepositoryException {

		}

		@Override
		public void writeItem(BareJID serviceJid, Object nodeId, long timeInMilis, String id, String publisher,
							  Element item) throws RepositoryException {

		}

		@Override
		public void initRepository(String s, Map<String, String> map) throws DBInitException {

		}

		protected void sleep() {
			if (!withDelay) {
				return;
			}

			try {
				Thread.sleep(400);
			} catch (InterruptedException ex) {
				assertFalse(true);
			}
		}
	}
}
