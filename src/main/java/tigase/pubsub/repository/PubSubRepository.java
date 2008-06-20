package tigase.pubsub.repository;

import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.pubsub.Affiliation;
import tigase.pubsub.NodeConfig;
import tigase.pubsub.PubSubConfig;

public class PubSubRepository {

	private static final String SUBSCRIBES_KEY = "subscribers";

	private final PubSubConfig config;

	private Logger log = Logger.getLogger(this.getClass().getName());

	private final UserRepository repository;

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

	public void addSubscriberJid(final String nodeName, final String jid, final Affiliation affiliation) throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY, jid, affiliation.name());
		} catch (Exception e) {
			throw new RepositoryException("Subscriber adding error", e);
		}

	}

	public final static String CREATION_DATE_KEY = "creation-date";

	public void createNode(String nodeName, String ownerJid, NodeConfig nodeConfig) throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName, CREATION_DATE_KEY,
					String.valueOf(System.currentTimeMillis()));
			nodeConfig.write(repository, config, NODES_KEY + nodeName + "/configuration");
			addSubscriberJid(nodeName, ownerJid, Affiliation.owner);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Node creation error", e);
		}

	}

	public Affiliation getSubscriberAffiliation(final String nodeName, final String jid) throws RepositoryException {
		try {
			String tmp = repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY, jid);
			if (tmp != null) {
				return Affiliation.valueOf(tmp);
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RepositoryException("Affiliation getting error", e);
		}

	}

	public String getCreationDate(String nodeName) throws RepositoryException {
		try {
			return repository.getData(config.getServiceName(), NODES_KEY + nodeName, CREATION_DATE_KEY);
		} catch (Exception e) {
			throw new RepositoryException("Owner getting error", e);
		}
	}

	private static final String NODES_KEY = "nodes/";

	public String[] getSubscribersJid(String nodeName) throws RepositoryException {
		try {
			return repository.getKeys(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY);
		} catch (Exception e) {
			throw new RepositoryException("Subscribers getting  error", e);
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

	public void deleteNode(String nodeName) throws RepositoryException {
		try {
			repository.removeSubnode(config.getServiceName(), NODES_KEY + nodeName);
		} catch (Exception e) {
			throw new RepositoryException("Node deleting error", e);
		}
	}

	public NodeConfig getNodeConfig(String nodeName) throws RepositoryException {
		try {
			NodeConfig result = new NodeConfig();
			result.read(repository, config, NODES_KEY + nodeName + "/configuration");
			return result;
		} catch (Exception e) {
			throw new RepositoryException("Node configuration reading error", e);
		}
	}

	public void update(final String nodeName, final NodeConfig nodeConfig) throws RepositoryException {
		try {
			nodeConfig.write(repository, config, NODES_KEY + nodeName + "/configuration");
		} catch (Exception e) {
			throw new RepositoryException("Node configuration writing error", e);
		}

	}

}
