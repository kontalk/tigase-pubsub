/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.pubsub.repository.migration;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.conf.ConfiguratorAbstract;
import tigase.db.RepositoryFactory;
import tigase.db.UserRepository;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xmpp.BareJID;

/**
 *
 * @author andrzej
 */
public class Converter {

	private static final Logger log  = Logger.getLogger(Converter.class.getCanonicalName());
	
	private IPubSubOldDAO oldRepo;
	private PubSubNewDAOJDBC newRepo;
	
	public static void main(String[] argv) throws RepositoryException, IOException {
		initLogger();
		
		if (argv == null || argv.length == 0) {
			System.out.println("\nConverter paramters:\n");
			System.out.println(" -in-repo-class tigase.pubsub.PubSubDAO                                     -		class of source repository");
			System.out.println(" -in 'jdbc:xxxx://localhost/tigasedb?user=tigase&password=tigase_pass'      -		uri of source database");
			System.out.println(" -out 'jdbc:xxxx://localhost/tigasedb?user=tigase&password=tigase_pass'     -		uri of destination database");
			return;
		}
		
		Converter converter = new Converter();
		
		log.config("parsing configuration parameters");
		String repoClass = null;
		String oldRepoUri = null;
		String newRepoUri = null;
		for (int i=0; i<argv.length; i++) {
			String arg = argv[i];
			if ("-in".equals(arg)) {
				i++;
				oldRepoUri = argv[i];
			}
			else if ("-out".equals(arg)) {
				i++;
				newRepoUri = argv[i];		
			}
			else if ("-in-repo-class".equals(arg)) {
				i++;
				repoClass = argv[i];
			}				
		}
				
		log.config("initializing converter");
		converter.init(repoClass, oldRepoUri, newRepoUri);
		
		log.info("starting migration");
		converter.convert();
		log.info("migration finished");
	}
	
	public void init(String repoClass, String oldRepoUri, String newRepoUri) throws RepositoryException {
		try {
			String repoCls = null;
			if (oldRepoUri.contains(":mysql:")) repoCls = "mysql";
			else if (oldRepoUri.contains(":postgresql:")) repoCls = "pgsql";
			else if (oldRepoUri.contains(":derby:")) repoCls = "derby";
			else if (oldRepoUri.contains(":sqlserver:")) repoCls = "sqlserver";
			
			if (repoClass == null || repoClass.endsWith("PubSubDAO")) {				
				UserRepository userRepository = RepositoryFactory.getUserRepository(repoCls, oldRepoUri, null);
				oldRepo = new PubSubOldDAO(userRepository);
			}
			else {
				oldRepo = new PubSubOldDAOJDBC(repoCls, oldRepoUri);
			}
			log.log(Level.FINE, "initializing source repository {0} for uri {1}", 
					new Object[] { oldRepo.getClass().getCanonicalName(), oldRepoUri });
			oldRepo.init();
			
			newRepo = new PubSubNewDAOJDBC();
			log.log(Level.INFO, "initializing destination repository {0} for uri {1}",
					new Object[] { newRepo.getClass().getCanonicalName(), newRepoUri });
			newRepo.init(newRepoUri, null, null);
		} catch (Exception ex) {
			throw new RepositoryException("could not initialize converter", ex);
		}
	}

	public static void initLogger() {
		String initial_config
				= "tigase.level=ALL\n" + "tigase.db.jdbc.level=INFO\n" + "tigase.xml.level=INFO\n"
				+ "tigase.form.level=INFO\n"
				+ "handlers=java.util.logging.ConsoleHandler java.util.logging.FileHandler\n"
				+ "java.util.logging.ConsoleHandler.level=ALL\n"
				+ "java.util.logging.ConsoleHandler.formatter=tigase.util.LogFormatter\n"
				+ "java.util.logging.FileHandler.formatter=tigase.util.LogFormatter\n"
				+ "java.util.logging.FileHandler.pattern=pubsub_db_migration.log\n"
				+ "tigase.useParentHandlers=true\n";

		ConfiguratorAbstract.loadLogManagerConfig( initial_config );		
	}
	
	public void convert() throws RepositoryException {
		BareJID[] serviceJids = oldRepo.getServiceJids();
		for (BareJID serviceJid : serviceJids) {
			String[] allNodesIds = oldRepo.getNodesList(serviceJid);
			if (allNodesIds != null && allNodesIds.length != 0) {
				log.log(Level.INFO, "starting migration for {0}", serviceJid.toString());
				convertNodesConfigurations(serviceJid, allNodesIds);
				convertNodesAffiliations(serviceJid, allNodesIds);
				convertNodesSubscriptions(serviceJid, allNodesIds);
				convertNodesItems(serviceJid, allNodesIds);
				log.log(Level.INFO, "migration for {0} finished", serviceJid.toString());
			}		
		}
	}
	
	private void convertNodesConfigurations(BareJID serviceJid, String[] allNodesIds) throws RepositoryException {		
		log.log(Level.INFO, "loading nodes configurations for {0}", serviceJid.toString());
		// load all nodes configurations		
		final Set<String> rootCollection = new HashSet<String>();
		final Map<String, AbstractNodeConfig> nodeConfigs = new HashMap<String, AbstractNodeConfig>();
		for (String nodeName : allNodesIds) {
			AbstractNodeConfig nodeConfig = oldRepo.getNodeConfig(serviceJid, nodeName);
			if (nodeConfig == null)
					continue;
			nodeConfigs.put(nodeName, nodeConfig);
			if (nodeConfig instanceof CollectionNodeConfig) {
				CollectionNodeConfig collectionNodeConfig = (CollectionNodeConfig) nodeConfig;
				collectionNodeConfig.setChildren(null);
			}
		}

		// rebuild nodes dependencies tree
		for (Map.Entry<String, AbstractNodeConfig> entry : nodeConfigs.entrySet()) {
			final AbstractNodeConfig nodeConfig = entry.getValue();
			final String nodeName = entry.getKey();
			final String collectionNodeName = nodeConfig.getCollection();
			if (collectionNodeName == null || collectionNodeName.equals("")) {
				nodeConfig.setCollection("");
				rootCollection.add(nodeName);
			} else {
				AbstractNodeConfig potentialParent = nodeConfigs.get(collectionNodeName);
				if (potentialParent != null && potentialParent instanceof CollectionNodeConfig) {
					CollectionNodeConfig collectionConfig = (CollectionNodeConfig) potentialParent;
					collectionConfig.addChildren(nodeName);
				} else {
					nodeConfig.setCollection("");
					rootCollection.add(nodeName);
				}

			}
		}

		log.log(Level.INFO, "creating nodes in new store for {0}", serviceJid.toString());
		Map<String,Long> nodeIds = new HashMap<String,Long>();
		// save nodes to database
		for (Map.Entry<String, AbstractNodeConfig> entry : nodeConfigs.entrySet()) {
			final AbstractNodeConfig nodeConfig = entry.getValue();
			final String nodeName = entry.getKey();
			long nodeId = newRepo.getNodeId(serviceJid, nodeName);
			if (nodeId != 0) {
				nodeIds.put(nodeName, nodeId);
				continue;
			}
			
			BareJID owner = oldRepo.getNodeCreator(serviceJid, nodeName);
//			UsersAffiliation[] affiliations = oldRepo.getNodeAffiliations(serviceJid, nodeName);
//			if (affiliations != null) {
//				for (UsersAffiliation affiliation : affiliations) {
//					if (affiliation.getAffiliation() == Affiliation.owner)
//						owner = affiliation.getJid();
//				}					
//			}
			long collectionId = newRepo.getNodeId(serviceJid, nodeConfig.getCollection());
			nodeId = newRepo.createNode(serviceJid, nodeName, owner, nodeConfig, nodeConfig.getNodeType(), 
					collectionId == 0 ? null : collectionId);
			nodeIds.put(nodeName, nodeId);
		}		
		
		log.log(Level.INFO, "fixing nodes metadata in new store for {0}", serviceJid.toString());
		// fix database references to collections
		for (Map.Entry<String, AbstractNodeConfig> entry : nodeConfigs.entrySet()) {
			final AbstractNodeConfig nodeConfig = entry.getValue();
			final String nodeName = entry.getKey();
			
			Long nodeId = nodeIds.get(nodeName);
			Long collectionId = nodeIds.get(nodeConfig.getCollection());
			newRepo.updateNodeConfig(serviceJid, nodeId, nodeConfig.getFormElement().toString(), collectionId);
			Date nodeCreationDate = oldRepo.getNodeCreationDate(serviceJid, nodeName);
			newRepo.fixNode(serviceJid, nodeId, nodeCreationDate);
		}
	}
	
	private void convertNodesAffiliations(BareJID serviceJid, String[] nodesIds) throws RepositoryException {
		log.log(Level.INFO, "migrating nodes affiliations for {0}", serviceJid.toString());
		for (String nodeName : nodesIds) {
			long nodeId = newRepo.getNodeId(serviceJid, nodeName);
			UsersAffiliation[] affiliations = oldRepo.getNodeAffiliations(serviceJid, nodeName);
			for (UsersAffiliation aff : affiliations) {
				newRepo.updateNodeAffiliation(serviceJid, nodeId, nodeName, aff);
			}
		}
	}
	
	private void convertNodesSubscriptions(BareJID serviceJid, String[] nodesIds) throws RepositoryException {
		log.log(Level.INFO, "migrating nodes subscriptions for {0}", serviceJid.toString());
		for (String nodeName : nodesIds) {
			long nodeId = newRepo.getNodeId(serviceJid, nodeName);
			UsersSubscription[] subscription = oldRepo.getNodeSubscriptions(serviceJid, nodeName);
			for (UsersSubscription subscr : subscription) {
				newRepo.updateNodeSubscription(serviceJid, nodeId, nodeName, subscr);
			}
		}		
	}
	
	private void convertNodesItems(BareJID serviceJid, String[] nodesIds) throws RepositoryException {
		log.log(Level.INFO, "migrating nodes items for {0}", serviceJid.toString());
		for (String nodeName : nodesIds) {
			String[] itemIds = oldRepo.getItemsIds(serviceJid, nodeName);
			if (itemIds == null || itemIds.length == 0)
				continue;
			
			long nodeId = newRepo.getNodeId(serviceJid, nodeName);
			for (String id : itemIds) {
				IPubSubOldDAO.Item item = oldRepo.getItem(serviceJid, nodeName, id);
				newRepo.writeItem(serviceJid, nodeId, 0, item.publisher, id, item.item);
				newRepo.fixItem(serviceJid, nodeId, item.id, item.creationDate, item.updateDate);
			}
		}
	}
}
