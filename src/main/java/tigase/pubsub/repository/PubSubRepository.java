package tigase.pubsub.repository;

import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.pubsub.NodeConfig;
import tigase.pubsub.PubSubConfig;

public class PubSubRepository {

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

	public void addSubscriberJid(String nodeName, String jid) throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/subscribers", jid, "subscribe");
		} catch (Exception e) {
			throw new RepositoryException("Subscriber adding error", e);
		}

	}

	public void createNode(String nodeName, String ownerJid, NodeConfig nodeConfig) throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName, "owner", ownerJid);
			nodeConfig.write(repository, config, NODES_KEY + nodeName + "/configuration");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Node creation error", e);
		}

	}

	public String getOwnerJid(String nodeName) throws RepositoryException {
		try {
			return repository.getData(config.getServiceName(), NODES_KEY + nodeName, "owner");
		} catch (Exception e) {
			throw new RepositoryException("Owner getting error", e);
		}
	}

	private static final String NODES_KEY = "nodes/";

	public String[] getSubscribersJid(String nodeName) throws RepositoryException {
		try {
			return repository.getKeys(config.getServiceName(), NODES_KEY + nodeName + "/subscribers");
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

}
