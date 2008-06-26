package tigase.pubsub.repository;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.pubsub.Affiliation;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;

public class PubSubRepository {

	private static final String SUBSCRIBES_KEY = "subscribers";

	private static SecureRandom numberGenerator;

	private final PubSubConfig config;

	private Logger log = Logger.getLogger(this.getClass().getName());

	private final UserRepository repository;

	public final static String CREATION_DATE_KEY = "creation-date";

	private static final String NODE_TYPE_KEY = "pubsub#node-type";

	private static final String NODES_KEY = "nodes/";

	private static final String ASSOCIATE_COLLECTION_KEY = "pubsub#collection";

	public PubSubRepository(UserRepository repository, PubSubConfig pubSubConfig) throws RepositoryException {
		this.repository = repository;
		this.config = pubSubConfig;

		try {
			this.repository.setData(this.config.getServiceName(), "last-start", String.valueOf(System.currentTimeMillis()));
		} catch (UserNotFoundException e) {
			try {
				this.repository.addUser(this.config.getServiceName());
				this.repository.setData(this.config.getServiceName(), "last-start", String.valueOf(System.currentTimeMillis()));
			} catch (Exception e1) {
				log.log(Level.SEVERE, "PubSub repository initialization problem", e1);
				throw new RepositoryException("Cannot initialize PubSUb repository", e);
			}
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "PubSub repository initialization problem", e);
			throw new RepositoryException("Cannot initialize PubSUb repository", e);
		}

	}

	public String addSubscriberJid(final String nodeName, final String jid, final Affiliation affiliation,
			final Subscription subscription) throws RepositoryException {
		try {
			String subid = createUID();
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY + "/" + jid, "affiliation",
					affiliation.name());
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY + "/" + jid, "subscription",
					subscription.name());
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY + "/" + jid, "subid", subid);
			return subid;
		} catch (Exception e) {
			throw new RepositoryException("Subscriber adding error", e);
		}

	}

	public void createNode(String nodeName, String ownerJid, LeafNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName, CREATION_DATE_KEY,
					String.valueOf(System.currentTimeMillis()));
			repository.setData(config.getServiceName(), NODES_KEY + nodeName, NODE_TYPE_KEY, nodeType.name());
			if (nodeConfig != null)
				nodeConfig.write(repository, config, NODES_KEY + nodeName + "/configuration");
			addSubscriberJid(nodeName, ownerJid, Affiliation.owner, Subscription.subscribed);
			setNewNodeCollection(nodeName, collection);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Node creation error", e);
		}

	}

	public void deleteNode(String nodeName) throws RepositoryException {
		try {
			repository.removeSubnode(config.getServiceName(), NODES_KEY + nodeName);
		} catch (Exception e) {
			throw new RepositoryException("Node deleting error", e);
		}
	}

	public String[] getNodeChildren(final String node) throws RepositoryException {
		List<String> result = new ArrayList<String>();
		final String tmpNode = node == null ? "" : node;
		for (String name : getNodesList()) {
			final String collection = getCollectionOf(name);
			if (tmpNode.equals(collection)) {
				result.add(name);
			}
		}
		return result.toArray(new String[] {});
	}

	public LeafNodeConfig getNodeConfig(String nodeName) throws RepositoryException {
		try {
			LeafNodeConfig result = new LeafNodeConfig();
			result.read(repository, config, NODES_KEY + nodeName + "/configuration");
			Field f = Field.fieldTextMulti("pubsub#children", getNodeChildren(nodeName), null);
			result.add(f);
			return result;
		} catch (Exception e) {
			throw new RepositoryException("Node configuration reading error", e);
		}
	}

	public String[] getNodesList() throws RepositoryException {
		try {
			String[] nodes = repository.getSubnodes(config.getServiceName(), NODES_KEY);
			return nodes;
		} catch (Exception e) {
			throw new RepositoryException("Nodes list getting error", e);
		}
	}

	public NodeType getNodeType(String nodeName) throws RepositoryException {
		try {
			String name = repository.getData(config.getServiceName(), NODES_KEY + nodeName, NODE_TYPE_KEY);
			if (name != null) {
				return NodeType.valueOf(name);
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RepositoryException("Owner getting error", e);
		}
	}

	public Affiliation getSubscriberAffiliation(final String nodeName, final String jid) throws RepositoryException {
		try {
			String tmp = repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY + "/" + jid,
					"affiliation");
			// NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY, jid);
			if (tmp != null) {
				return Affiliation.valueOf(tmp);
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RepositoryException("Affiliation getting error", e);
		}

	}

	public String[] getSubscribersJid(String nodeName) throws RepositoryException {
		try {
			return repository.getSubnodes(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY);
		} catch (Exception e) {
			throw new RepositoryException("Subscribers getting  error", e);
		}
	}

	public void update(final String nodeName, final LeafNodeConfig nodeConfig) throws RepositoryException {
		try {
			nodeConfig.write(repository, config, NODES_KEY + nodeName + "/configuration");
		} catch (Exception e) {
			throw new RepositoryException("Node configuration writing error", e);
		}

	}

	public String getCollectionOf(String nodeName) throws RepositoryException {
		try {
			return repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/configuration/", ASSOCIATE_COLLECTION_KEY);
		} catch (Exception e) {
			throw new RepositoryException("Node collection getting error", e);
		}
	}

	public void setNewNodeCollection(String nodeName, String collectionNew) throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/configuration/", ASSOCIATE_COLLECTION_KEY,
					collectionNew);
		} catch (Exception e) {
			throw new RepositoryException("Node collection writing error", e);
		}
	}

	public void changeSubscription(final String nodeName, final String jid, final Subscription subscription)
			throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY + "/" + jid, "subscription",
					subscription.name());
		} catch (Exception e) {
			throw new RepositoryException("Subscription writing error", e);
		}

	}

	public String getSubscriptionId(String nodeName, String jid) throws RepositoryException {
		try {
			return repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY + "/" + jid, "subid");
		} catch (Exception e) {
			throw new RepositoryException("SubID reading error", e);
		}
	}

	public static synchronized String createUID() {
		SecureRandom ng = numberGenerator;
		if (ng == null) {
			numberGenerator = ng = new SecureRandom();
		}
		byte[] rnd = new byte[20];
		ng.nextBytes(rnd);
		byte[] tmp = new byte[rnd.length + 1];
		System.arraycopy(rnd, 0, tmp, 1, rnd.length);
		tmp[0] = 0x00;
		BigInteger bi = new BigInteger(tmp);
		return bi.toString(36);
	}

	public Subscription getSubscription(String nodeName, String jid) throws RepositoryException {
		try {
			String tmp = repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY + "/" + jid,
					"subscription");
			if (tmp != null) {
				return Subscription.valueOf(tmp);
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RepositoryException("Subscription getting error", e);
		}
	}

	public void removeSubscriber(final String nodeName, final String jid) throws RepositoryException {
		try {
			repository.removeSubnode(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY + "/" + jid);
		} catch (Exception e) {
			throw new RepositoryException("Subscriber removing error", e);
		}
	}

	UserRepository getUserRepository() {
		return repository;
	}

}
