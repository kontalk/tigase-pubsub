/*
 * PubSubComponent.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.pubsub;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;

import javax.script.Bindings;

import tigase.adhoc.AdHocScriptCommandManager;
import tigase.component.AbstractComponent;
import tigase.component.PacketWriter;
import tigase.conf.Configurable;
import tigase.db.DataRepository;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.pubsub.modules.AdHocConfigCommandModule;
import tigase.pubsub.modules.DefaultConfigModule;
import tigase.pubsub.modules.DiscoverInfoModule;
import tigase.pubsub.modules.DiscoverItemsModule;
import tigase.pubsub.modules.JabberVersionModule;
import tigase.pubsub.modules.ManageAffiliationsModule;
import tigase.pubsub.modules.ManageSubscriptionModule;
import tigase.pubsub.modules.NodeConfigModule;
import tigase.pubsub.modules.NodeCreateModule;
import tigase.pubsub.modules.NodeDeleteModule;
import tigase.pubsub.modules.PendingSubscriptionModule;
import tigase.pubsub.modules.PresenceCollectorModule;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.modules.PurgeItemsModule;
import tigase.pubsub.modules.RetractItemModule;
import tigase.pubsub.modules.RetrieveAffiliationsModule;
import tigase.pubsub.modules.RetrieveItemsModule;
import tigase.pubsub.modules.RetrieveSubscriptionsModule;
import tigase.pubsub.modules.SubscribeNodeModule;
import tigase.pubsub.modules.UnsubscribeNodeModule;
import tigase.pubsub.modules.XmppPingModule;
import tigase.pubsub.modules.XsltTool;
import tigase.pubsub.modules.commands.DefaultConfigCommand;
import tigase.pubsub.modules.commands.DeleteAllNodesCommand;
import tigase.pubsub.modules.commands.ReadAllNodesCommand;
import tigase.pubsub.modules.commands.RebuildDatabaseCommand;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.PubSubDAOJDBC;
import tigase.pubsub.repository.PubSubDAOPool;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.server.Command;
import tigase.server.DisableDisco;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * Class description
 * 
 * 
 * @version 5.1.0, 2010.11.02 at 01:05:02 MDT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PubSubComponent extends AbstractComponent<PubSubConfig> implements Configurable, DisableDisco,
		DefaultNodeConfigListener {

	private class AdHocScriptCommandManagerImpl implements AdHocScriptCommandManager {

		private final PubSubComponent component;

		public AdHocScriptCommandManagerImpl(PubSubComponent component) {
			this.component = component;
		}

		@Override
		public List<Element> getCommandListItems(JID senderJid, JID toJid) {
			return component.getScriptItems(Command.XMLNS, toJid, senderJid);
		}

		@Override
		public List<Packet> process(Packet packet) {
			Queue<Packet> results = new ArrayDeque<Packet>();

			if (component.processScriptCommand(packet, results)) {
				return new ArrayList<Packet>(results);
			}

			return null;
		}

	}

	public static final String ADMINS_KEY = "admin";

	private static final String COMPONENT = "component";
	/** Field description */
	public static final String DEFAULT_LEAF_NODE_CONFIG_KEY = "default-node-config";
	private static final String MAX_CACHE_SIZE = "pubsub-repository-cache-size";
	/**
	 * Field description
	 */
	protected static final String PUBSUB_REPO_CLASS_PROP_KEY = "pubsub-repo-class";

	/**
	 * Field description
	 */
	protected static final String PUBSUB_REPO_POOL_SIZE_PROP_KEY = "pubsub-repo-pool-size";
	/**
	 * Field description
	 */
	protected static final String PUBSUB_REPO_URL_PROP_KEY = "pubsub-repo-url";
	private AdHocConfigCommandModule adHocCommandsModule;
	private DefaultConfigModule defaultConfigModule;
	protected LeafNodeConfig defaultNodeConfig;
	private PubSubDAO directPubSubRepository;
	private ManageAffiliationsModule manageAffiliationsModule;
	private ManageSubscriptionModule manageSubscriptionModule;
	private Integer maxRepositoryCacheSize;
	private NodeConfigModule nodeConfigModule;
	private NodeCreateModule nodeCreateModule;
	private NodeDeleteModule nodeDeleteModule;
	private PendingSubscriptionModule pendingSubscriptionModule;
	private PresenceCollectorModule presenceCollectorModule;
	private PublishItemModule publishNodeModule;
	protected CachedPubSubRepository pubsubRepository;
	private PurgeItemsModule purgeItemsModule;
	private RetractItemModule retractItemModule;

	private RetrieveItemsModule retrirveItemsModule;
	private AdHocScriptCommandManager scriptCommandManager;

	private SubscribeNodeModule subscribeNodeModule;
	private UnsubscribeNodeModule unsubscribeNodeModule;
	protected UserRepository userRepository;

	// ~--- constructors
	// ---------------------------------------------------------

	private XsltTool xslTransformer;

	/**
	 * Constructs ...
	 * 
	 */
	public PubSubComponent() {
		this.scriptCommandManager = new AdHocScriptCommandManagerImpl(this);
	}

	@Override
	protected PubSubConfig createComponentConfigInstance(AbstractComponent<?> abstractComponent) {
		return new PubSubConfig(abstractComponent);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param directRepository
	 * 
	 * @return
	 */
	protected CachedPubSubRepository createPubSubRepository(PubSubDAO directRepository) {
		// return new StatelessPubSubRepository(directRepository, this.config);
		return new CachedPubSubRepository(directRepository, maxRepositoryCacheSize);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param params
	 * 
	 * @return
	 */
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = super.getDefaults(params);

		// By default use the same repository as all other components:
		String repo_class = RepositoryFactory.DERBY_REPO_CLASS_PROP_VAL;
		String repo_uri = RepositoryFactory.DERBY_REPO_URL_PROP_VAL;
		String conf_db = null;

		if (params.get(RepositoryFactory.GEN_USER_DB) != null) {
			conf_db = (String) params.get(RepositoryFactory.GEN_USER_DB);
		} // end of if (params.get(GEN_USER_DB) != null)
		if (conf_db != null) {
			if (conf_db.equals("mysql")) {
				repo_class = RepositoryFactory.MYSQL_REPO_CLASS_PROP_VAL;
				repo_uri = RepositoryFactory.MYSQL_REPO_URL_PROP_VAL;
			}
			if (conf_db.equals("pgsql")) {
				repo_class = RepositoryFactory.PGSQL_REPO_CLASS_PROP_VAL;
				repo_uri = RepositoryFactory.PGSQL_REPO_URL_PROP_VAL;
			}
			if (conf_db.equals("sqlserver")) {
				repo_class = RepositoryFactory.SQLSERVER_REPO_CLASS_PROP_VAL;
				repo_uri = RepositoryFactory.SQLSERVER_REPO_URL_PROP_VAL;
			}
		} // end of if (conf_db != null)
		if (params.get(RepositoryFactory.GEN_USER_DB_URI) != null) {
			repo_uri = (String) params.get(RepositoryFactory.GEN_USER_DB_URI);
		} // end of if (params.get(GEN_USER_DB_URI) != null)
		props.put(PUBSUB_REPO_CLASS_PROP_KEY, repo_class);
		props.put(PUBSUB_REPO_URL_PROP_KEY, repo_uri);
		props.put(MAX_CACHE_SIZE, "2000");

		String[] admins;

		if (params.get(GEN_ADMINS) != null) {
			admins = ((String) params.get(GEN_ADMINS)).split(",");
		} else {
			admins = new String[] { "admin@" + getDefHostName() };
		}
		props.put(ADMINS_KEY, admins);

		return props;
	}

	// ~--- set methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 */
	protected void init() {
		final PacketWriter writer = getWriter();
		this.xslTransformer = new XsltTool();
		this.presenceCollectorModule = registerModule(new PresenceCollectorModule(componentConfig, eventBus, pubsubRepository,
				writer));
		this.publishNodeModule = registerModule(new PublishItemModule(componentConfig, this.pubsubRepository, writer,
				this.xslTransformer, this.presenceCollectorModule));
		this.retractItemModule = registerModule(new RetractItemModule(componentConfig, this.pubsubRepository, writer,
				this.publishNodeModule));
		this.pendingSubscriptionModule = registerModule(new PendingSubscriptionModule(componentConfig, this.pubsubRepository,
				writer));
		this.manageSubscriptionModule = registerModule(new ManageSubscriptionModule(componentConfig, this.pubsubRepository,
				writer));
		this.subscribeNodeModule = registerModule(new SubscribeNodeModule(componentConfig, this.pubsubRepository, writer,
				this.pendingSubscriptionModule));
		this.nodeCreateModule = registerModule(new NodeCreateModule(componentConfig, this.pubsubRepository, writer,
				this.defaultNodeConfig, this.publishNodeModule));
		this.nodeDeleteModule = registerModule(new NodeDeleteModule(componentConfig, this.pubsubRepository, writer,
				this.publishNodeModule));
		this.defaultConfigModule = registerModule(new DefaultConfigModule(componentConfig, this.pubsubRepository,
				this.defaultNodeConfig, writer));
		this.nodeConfigModule = registerModule(new NodeConfigModule(componentConfig, this.pubsubRepository, writer,
				this.defaultNodeConfig, this.publishNodeModule));
		this.unsubscribeNodeModule = registerModule(new UnsubscribeNodeModule(componentConfig, this.pubsubRepository, writer));
		this.manageAffiliationsModule = registerModule(new ManageAffiliationsModule(componentConfig, this.pubsubRepository,
				writer));
		this.retrirveItemsModule = registerModule(new RetrieveItemsModule(componentConfig, this.pubsubRepository, writer));
		this.purgeItemsModule = registerModule(new PurgeItemsModule(componentConfig, this.pubsubRepository, writer,
				this.publishNodeModule));
		registerModule(new JabberVersionModule(componentConfig, pubsubRepository, writer));
		this.adHocCommandsModule = registerModule(new AdHocConfigCommandModule(componentConfig, this.pubsubRepository, writer,
				scriptCommandManager));
		registerModule(new DiscoverInfoModule(componentConfig, this.pubsubRepository, writer, modulesManager));
		registerModule(new DiscoverItemsModule(componentConfig, this.pubsubRepository, writer, this.adHocCommandsModule));
		registerModule(new RetrieveAffiliationsModule(componentConfig, this.pubsubRepository, writer));
		registerModule(new RetrieveSubscriptionsModule(componentConfig, this.pubsubRepository, writer));
		registerModule(new XmppPingModule(componentConfig, pubsubRepository, writer));
		this.pubsubRepository.init();
	}

	@Override
	public void initBindings(Bindings binds) {
		super.initBindings(binds); // To change body of generated methods,
									// choose Tools | Templates.
		binds.put(COMPONENT, this);
	}

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param admins
	 * @param pubSubDAO
	 * @param createPubSubRepository
	 * @param defaultNodeConfig
	 * 
	 * @throws RepositoryException
	 * @throws TigaseDBException
	 * @throws UserNotFoundException
	 */
	public void initialize(String[] admins, PubSubDAO pubSubDAO, IPubSubRepository createPubSubRepository,
			LeafNodeConfig defaultNodeConfig) throws UserNotFoundException, TigaseDBException, RepositoryException {
		this.componentConfig.setAdmins(admins);
		// this.componentConfig.setServiceName("tigase-pubsub");

		// XXX remove ASAP
		if (pubSubDAO != null) {
			pubSubDAO.init();
		}
		this.directPubSubRepository = pubSubDAO;
		this.pubsubRepository = createPubSubRepository(pubSubDAO);
		this.defaultNodeConfig = defaultNodeConfig;
		this.defaultNodeConfig.read(userRepository, componentConfig, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
		this.defaultNodeConfig.write(userRepository, componentConfig, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
		init();

		final DefaultConfigCommand configCommand = new DefaultConfigCommand(this.componentConfig, this.userRepository);

		configCommand.addListener(this);
		this.adHocCommandsModule.register(new RebuildDatabaseCommand(this.componentConfig, this.directPubSubRepository));
		this.adHocCommandsModule.register(configCommand);
		this.adHocCommandsModule.register(new DeleteAllNodesCommand(this.componentConfig, this.directPubSubRepository,
				this.userRepository));
		this.adHocCommandsModule.register(new ReadAllNodesCommand(this.componentConfig, this.directPubSubRepository,
				this.pubsubRepository));
	}

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public boolean isSubdomain() {
		return true;
	}

	@Override
	public void onChangeDefaultNodeConfig() {
		try {
			this.defaultNodeConfig.read(userRepository, componentConfig, DEFAULT_LEAF_NODE_CONFIG_KEY);
			log.info("Node " + getComponentId() + " read default node configuration.");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Reading default config error", e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param props
	 */
	@Override
	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);
		if (props.size() == 1) {

			// If props.size() == 1, it means this is a single property update
			// and this component does not support single property change for
			// the rest
			// of it's settings
			return;
		}

		// Release old resources....
		if (pubsubRepository != null) {
			pubsubRepository.destroy();
		}
		if (directPubSubRepository != null) {
			directPubSubRepository.destroy();
		}

		// String[] hostnames = (String[]) props.get(HOSTNAMES_PROP_KEY);
		// if (hostnames == null || hostnames.length == 0) {
		// log.warning("Hostnames definition is empty, setting 'localhost'");
		// hostnames = new String[] { getName() + ".localhost" };
		// }
		// clearRoutings();
		// for (String host : hostnames) {
		// addRouting(host);
		// }
		String maxCache = (String) props.get(MAX_CACHE_SIZE);

		if (maxCache != null) {
			try {
				maxRepositoryCacheSize = Integer.valueOf(maxCache);
				props.put(MAX_CACHE_SIZE, maxRepositoryCacheSize.toString());
			} catch (Exception e) {
				maxRepositoryCacheSize = null;
				props.put(MAX_CACHE_SIZE, "off");
			}
		}

		// Is there a shared user repository pool? If so I want to use it:
		userRepository = (UserRepository) props.get(SHARED_USER_REPO_PROP_KEY);
		if (userRepository == null) {

			// Is there shared user repository instance? If so I want to use it:
			userRepository = (UserRepository) props.get(SHARED_USER_REPO_PROP_KEY);
		}
		try {
			PubSubDAO dao;
			String cls_name = (String) props.get(PUBSUB_REPO_CLASS_PROP_KEY);
			String res_uri = (String) props.get(PUBSUB_REPO_URL_PROP_KEY);

			if (userRepository == null) {

				// if (!res_uri.contains("autoCreateUser=true")) {
				// res_uri += "&autoCreateUser=true";
				// }
				userRepository = RepositoryFactory.getUserRepository(cls_name, res_uri, null);
				userRepository.initRepository(res_uri, null);
				log.config("Initialized " + cls_name + " as pubsub repository: " + res_uri);
			}

			int dao_pool_size = 1;

			try {
				dao_pool_size = Integer.parseInt((String) props.get(PUBSUB_REPO_POOL_SIZE_PROP_KEY));
			} catch (Exception e) {
				dao_pool_size = 1;
			}
			if (log.isLoggable(Level.FINE)) {
				log.fine("PubSubDAO pool size: " + dao_pool_size);
			}
			if (dao_pool_size > 1) {
				PubSubDAOPool dao_pool = new PubSubDAOPool(userRepository, this.componentConfig);

				for (int i = 0; i < dao_pool_size; i++) {
					if (cls_name.equals("tigase.pubsub.repository.PubSubDAOJDBC")) {
						dao_pool.addDao(new PubSubDAOJDBC(userRepository, this.componentConfig, res_uri));
					} else {
						dao_pool.addDao(new PubSubDAO(userRepository, this.componentConfig));
					}
				}
				dao = dao_pool;
			} else {
				if (cls_name.equals("tigase.pubsub.repository.PubSubDAOJDBC")) {
					dao = new PubSubDAOJDBC(userRepository, this.componentConfig, res_uri);
				} else {
					dao = new PubSubDAO(userRepository, this.componentConfig);
				}
			}
			initialize((String[]) props.get(ADMINS_KEY), dao, null, new LeafNodeConfig("default"));
		} catch (Exception e) {
			log.severe("Can't initialize pubsub repository: " + e);
			e.printStackTrace();
		}
	}
}
