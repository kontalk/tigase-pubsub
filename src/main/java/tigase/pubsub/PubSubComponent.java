/*
 * PubSubComponent.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
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

//~--- non-JDK imports --------------------------------------------------------

import tigase.adhoc.AdHocScriptCommandManager;
import tigase.component2.AbstractComponent;
import tigase.component2.PacketWriter;
import tigase.conf.Configurable;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.pubsub.modules.AdHocConfigCommandModule;
import tigase.pubsub.modules.CapsModule;
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
import tigase.pubsub.modules.commands.DefaultConfigCommand.DefaultNodeConfigurationChangedHandler;
import tigase.pubsub.modules.commands.DeleteAllNodesCommand;
import tigase.pubsub.modules.commands.ReadAllNodesCommand;
import tigase.pubsub.modules.commands.RebuildDatabaseCommand;
import tigase.pubsub.modules.ext.presence.PresenceNodeSubscriptions;
import tigase.pubsub.modules.ext.presence.PresenceNotifierModule;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.PubSubDAOJDBC;
import tigase.pubsub.repository.PubSubDAOPool;
import tigase.pubsub.repository.PubSubRepositoryWrapper;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.server.Command;
import tigase.server.DisableDisco;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

//~--- JDK imports ------------------------------------------------------------

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.Bindings;
import tigase.pubsub.modules.commands.RetrieveItemsCommand;
import tigase.xmpp.Authorization;
import tigase.xmpp.PacketErrorTypeException;

/**
 * Class description
 *
 *
 * @version 5.1.0, 2010.11.02 at 01:05:02 MDT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PubSubComponent
				extends AbstractComponent<PubSubConfig>
				implements Configurable, DisableDisco {
	/** Field description */
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
	private static final Pattern  PARAMETRIZED_PROPERTY_PATTERN = Pattern.compile(
			"(.+)\\[(.*)\\]|(.+)");

	//~--- fields ---------------------------------------------------------------

	/** Field description */
	protected LeafNodeConfig defaultNodeConfig;

	/** Field description */
	protected Integer maxRepositoryCacheSize;

	/** Field description */
	protected IPubSubRepository pubsubRepository;

	/** Field description */
	protected UserRepository          userRepository;
	private AdHocConfigCommandModule  adHocCommandsModule;
	private CapsModule                capsModule;
	private PubSubDAO                 directPubSubRepository;
	private PendingSubscriptionModule pendingSubscriptionModule;
	private PresenceCollectorModule   presenceCollectorModule;
	private PresenceNotifierModule    presenceNotifierModule;	
	private PublishItemModule         publishNodeModule;

	// ~--- constructors
	// ---------------------------------------------------------
	private AdHocScriptCommandManager scriptCommandManager;
	private XsltTool                  xslTransformer;

	/**
	 * Constructs ...
	 *
	 */
	public PubSubComponent() {
		this.scriptCommandManager = new AdHocScriptCommandManagerImpl(this);
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param binds is a <code>Bindings</code>
	 */
	@Override
	public void initBindings(Bindings binds) {
		super.initBindings(binds);    // To change body of generated methods,

		// choose Tools | Templates.
		binds.put(COMPONENT, this);
	}
	
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
	public void initialize(String[] admins, PubSubDAO pubSubDAO,
			IPubSubRepository createPubSubRepository, LeafNodeConfig defaultNodeConfig)
					throws UserNotFoundException, TigaseDBException, RepositoryException {
		this.componentConfig.setAdmins(admins);

		// this.componentConfig.setServiceName("tigase-pubsub");

		// XXX remove ASAP
		if (pubSubDAO != null) {
			pubSubDAO.init();
		}

		// create pubsub user if it doesn't exist
		if ( ! ( userRepository.getUserUID( componentConfig.getServiceBareJID() ) > 0 ) ){
			userRepository.addUser( componentConfig.getServiceBareJID() );
		}
		this.directPubSubRepository = pubSubDAO;
		this.pubsubRepository       = createPubSubRepository(pubSubDAO);
		this.defaultNodeConfig      = defaultNodeConfig;
		this.defaultNodeConfig.read(userRepository, componentConfig, PubSubComponent
				.DEFAULT_LEAF_NODE_CONFIG_KEY);
		this.defaultNodeConfig.write(userRepository, componentConfig, PubSubComponent
				.DEFAULT_LEAF_NODE_CONFIG_KEY);
		this.componentConfig.setPubSubRepository(pubsubRepository);
		init();

		final DefaultConfigCommand configCommand = new DefaultConfigCommand(this
				.componentConfig, this.userRepository);

		configCommand.addDefaultNodeConfigurationChangedHandler(
				new DefaultNodeConfigurationChangedHandler() {
			@Override
			public void onDefaultConfigurationChanged(Packet packet, PubSubConfig config) {
				onChangeDefaultNodeConfig();
			}
		});
		this.adHocCommandsModule.register(new RebuildDatabaseCommand(this.componentConfig,
				this.directPubSubRepository));
		this.adHocCommandsModule.register(configCommand);
		this.adHocCommandsModule.register(new DeleteAllNodesCommand(this.componentConfig, this
				.directPubSubRepository, this.userRepository));
		this.adHocCommandsModule.register(new ReadAllNodesCommand(this.componentConfig, this
				.directPubSubRepository, this.pubsubRepository));
		this.adHocCommandsModule.register(new RetrieveItemsCommand(this.componentConfig, this
				.pubsubRepository, this.userRepository));
	}

	protected IPubSubRepository createPubSubRepository(PubSubDAO directRepository) {
		IPubSubRepository wrapper = new PubSubRepositoryWrapper(new CachedPubSubRepository(directRepository,
				maxRepositoryCacheSize)) {
			@Override
			public ISubscriptions getNodeSubscriptions(final BareJID serviceJid, final String nodeName)
					throws RepositoryException {
				return new PresenceNodeSubscriptions(serviceJid, nodeName, super.getNodeSubscriptions(serviceJid, nodeName),
						presenceNotifierModule);
			}
		};

		return wrapper;
	}

	/**
	 * Method description
	 *
	 */
	public void onChangeDefaultNodeConfig() {
		try {
			this.defaultNodeConfig.read(userRepository, componentConfig,
					DEFAULT_LEAF_NODE_CONFIG_KEY);
			log.info("Node " + getComponentId() + " read default node configuration.");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Reading default config error", e);
		}
	}

	//~--- get methods ----------------------------------------------------------

	// ~--- methods
	// --------------------------------------------------------------

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
		String repo_uri   = RepositoryFactory.DERBY_REPO_URL_PROP_VAL;
		String conf_db    = null;

		if (params.get(RepositoryFactory.GEN_USER_DB) != null) {
			conf_db = (String) params.get(RepositoryFactory.GEN_USER_DB);
		}    // end of if (params.get(GEN_USER_DB) != null)
		if (conf_db != null) {
			if (conf_db.equals("mysql")) {
				repo_class = RepositoryFactory.MYSQL_REPO_CLASS_PROP_VAL;
				repo_uri   = RepositoryFactory.MYSQL_REPO_URL_PROP_VAL;
			}
			if (conf_db.equals("pgsql")) {
				repo_class = RepositoryFactory.PGSQL_REPO_CLASS_PROP_VAL;
				repo_uri   = RepositoryFactory.PGSQL_REPO_URL_PROP_VAL;
			}
			if (conf_db.equals("sqlserver")) {
				repo_class = RepositoryFactory.SQLSERVER_REPO_CLASS_PROP_VAL;
				repo_uri   = RepositoryFactory.SQLSERVER_REPO_URL_PROP_VAL;
			}
		}    // end of if (conf_db != null)
		if (params.get(RepositoryFactory.GEN_USER_DB_URI) != null) {
			repo_uri = (String) params.get(RepositoryFactory.GEN_USER_DB_URI);
		}    // end of if (params.get(GEN_USER_DB_URI) != null)
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

	/**
	 * Method description
	 *
	 *
	 * @return a value of <code>String</code>
	 */
	protected void init() {
		final PacketWriter writer = getWriter();
		this.xslTransformer = new XsltTool();
		// this.modulesManager.reset();
		// this.eventBus.reset();
		if (!isRegistered(CapsModule.class))
			this.capsModule = registerModule(new CapsModule(componentConfig, writer));
		if (!isRegistered(PresenceCollectorModule.class))
			this.presenceCollectorModule = registerModule(new PresenceCollectorModule(componentConfig, writer, capsModule));
		if (!isRegistered(PublishItemModule.class))
			this.publishNodeModule = registerModule(new PublishItemModule(componentConfig, writer, this.xslTransformer,
					this.presenceCollectorModule));
		if (!isRegistered(RetractItemModule.class))
			registerModule(new RetractItemModule(componentConfig, writer, this.publishNodeModule));
		if (!isRegistered(PendingSubscriptionModule.class))
			this.pendingSubscriptionModule = registerModule(new PendingSubscriptionModule(componentConfig, writer));
		if (!isRegistered(ManageSubscriptionModule.class))
			registerModule(new ManageSubscriptionModule(componentConfig, writer));
		if (!isRegistered(SubscribeNodeModule.class))
			registerModule(new SubscribeNodeModule(componentConfig, writer, this.pendingSubscriptionModule, publishNodeModule));
		if (!isRegistered(NodeCreateModule.class))
			registerModule(new NodeCreateModule(componentConfig, writer, this.defaultNodeConfig, this.publishNodeModule));
		if (!isRegistered(NodeDeleteModule.class))
			registerModule(new NodeDeleteModule(componentConfig, writer, this.publishNodeModule));
		if (!isRegistered(DefaultConfigModule.class))
			registerModule(new DefaultConfigModule(componentConfig, this.defaultNodeConfig, writer));
		if (!isRegistered(NodeConfigModule.class))
			registerModule(new NodeConfigModule(componentConfig, writer, this.defaultNodeConfig, this.publishNodeModule));
		if (!isRegistered(UnsubscribeNodeModule.class))
			registerModule(new UnsubscribeNodeModule(componentConfig, writer));
		if (!isRegistered(ManageAffiliationsModule.class))
			registerModule(new ManageAffiliationsModule(componentConfig, writer));
		if (!isRegistered(RetrieveItemsModule.class))
			registerModule(new RetrieveItemsModule(componentConfig, writer));
		if (!isRegistered(PurgeItemsModule.class))
			registerModule(new PurgeItemsModule(componentConfig, writer, this.publishNodeModule));
		if (!isRegistered(JabberVersionModule.class))
			registerModule(new JabberVersionModule(componentConfig, writer));
		if (!isRegistered(AdHocConfigCommandModule.class))
			this.adHocCommandsModule = registerModule(new AdHocConfigCommandModule(componentConfig, writer,
					scriptCommandManager));
		if (!isRegistered(DiscoverInfoModule.class))
			registerModule(new DiscoverInfoModule(componentConfig, writer, modulesManager));
		if (!isRegistered(DiscoverItemsModule.class))
			registerModule(new DiscoverItemsModule(componentConfig, writer, this.adHocCommandsModule));
		if (!isRegistered(RetrieveAffiliationsModule.class))
			registerModule(new RetrieveAffiliationsModule(componentConfig, writer));
		if (!isRegistered(RetrieveSubscriptionsModule.class))
			registerModule(new RetrieveSubscriptionsModule(componentConfig, writer));
		if (!isRegistered(XmppPingModule.class))
			registerModule(new XmppPingModule(componentConfig, writer));
		if (!isRegistered(PresenceNotifierModule.class))
			this.presenceNotifierModule = registerModule(new PresenceNotifierModule(componentConfig, writer, publishNodeModule));

		this.pubsubRepository.init();
	}

	@Override
	public String getDiscoDescription() {
		return "PubSub";
	}

	/**
	 * Method description
	 *
	 *
	 * @param key is a <code>String</code>
	 * @param props is a <code>Map<String,Object></code>
	 *
	 * @return a value of <code>Map<String,Object></code>
	 */
	public static Map<String, Object> getProperties(String key, Map<String, Object> props) {
		Map<String, Object> result = new HashMap<String, Object>();

		for (Entry<String, Object> entry : props.entrySet()) {
			Matcher matcher = PARAMETRIZED_PROPERTY_PATTERN.matcher(entry.getKey());

			if (matcher.find()) {
				String keyBaseName = (matcher.group(1) != null)
						? matcher.group(1)
						: matcher.group(3);
				String keyMod      = matcher.group(2);

				if (keyBaseName.equals(key)) {
					result.put(keyMod, entry.getValue());
				}
			}
		}

		return result;
	}

	@Override
	public int hashCodeForPacket(Packet packet) {
		if ((packet.getStanzaFrom() != null) && (packet.getPacketFrom() != null) 
				&& !getComponentId().equals(packet.getPacketFrom())) {
			return packet.getStanzaFrom().hashCode();
		}
		
		if (packet.getStanzaTo() != null) {
			return packet.getStanzaTo().hashCode();
		}
		
		return 1;
	}

	@Override
	public boolean isDiscoNonAdmin() {
		return true;
	}
	
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
	
	/**
	 * Method description
	 *
	 *
	 *
	 *
	 * @return a value of <code>int</code>
	 */
	@Override
	public int processingInThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}

	/**
	 * Method description
	 *
	 *
	 *
	 *
	 * @return a value of <code>int</code>
	 */
	@Override
	public int processingOutThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}
	
	@Override
	public void processPacket(Packet packet) {
		// if stanza is addressed to getName()@domain then we need to return SERVICE_UNAVAILABLE error
		if (packet.getStanzaTo() != null && getName().equals(packet.getStanzaTo().getLocalpart())) {
			try {
				Packet result = Authorization.SERVICE_UNAVAILABLE.getResponseMessage(packet, null, true);
				addOutPacket(result);
			} catch (PacketErrorTypeException ex) {
				log.log(Level.FINE, "Packet already of type=error, while preparing error response", ex);
			}
			return;
		}
			
		super.processPacket(packet);
	}
		
	//~--- set methods ----------------------------------------------------------

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
			String    cls_name = (String) props.get(PUBSUB_REPO_CLASS_PROP_KEY);
			String    res_uri  = (String) props.get(PUBSUB_REPO_URL_PROP_KEY);

			if (userRepository == null) {
				userRepository = RepositoryFactory.getUserRepository(cls_name, res_uri, null);
				userRepository.initRepository(res_uri, null);
				log.config("Initialized " + cls_name + " as pubsub repository: " + res_uri);
			}
			dao = createDAO(props);
			initialize((String[]) props.get(ADMINS_KEY), dao, null, new LeafNodeConfig(
					"default"));
		} catch (Exception e) {
			log.severe("Can't initialize pubsub repository: " + e);
			e.printStackTrace();
		}
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param abstractComponent is a <code>AbstractComponent<?></code>
	 *
	 * @return a value of <code>PubSubConfig</code>
	 */
	@Override
	protected PubSubConfig createComponentConfigInstance(
			AbstractComponent<?> abstractComponent) {
		PubSubConfig result = new PubSubConfig(abstractComponent);

		return result;
	}

	// ~--- set methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param props is a <code>Map<String,Object></code>
	 *
	 * @return a value of <code>PubSubDAO</code>
	 */
	protected PubSubDAO createDAO(Map<String, Object> props) throws RepositoryException {
		final Map<String, Object> classNames = getProperties(PUBSUB_REPO_CLASS_PROP_KEY,
				props);
		final Map<String, Object> resUris = getProperties(PUBSUB_REPO_URL_PROP_KEY, props);
		final Map<String, Object> poolSizes = getProperties(PUBSUB_REPO_POOL_SIZE_PROP_KEY,
				props);
		final String default_cls_name = (String) classNames.get(null);

		
		
//		if (resUris.size() > 1) {
		PubSubDAOPool dao_pool = new PubSubDAOPool(userRepository);

		for (Entry<String, Object> e : resUris.entrySet()) {
			String domain = e.getKey();
			String resUri = (String) e.getValue();
			String className = classNames.containsKey(domain)
					? (String) classNames.get(domain)
					: default_cls_name;
			int dao_pool_size;

			try {
				dao_pool_size = Integer.parseInt((String) (poolSizes.containsKey(domain)
						? poolSizes.get(domain)
						: poolSizes.get(null)));
			} catch (Exception ex) {
				// we should set it at least to 10 to improve performace, 
				// as previous value (1) was really not enought
				dao_pool_size = 10;
			}
			if (log.isLoggable(Level.FINER)) {
				log.finer("Creating DAO for domain=" + domain + "; class=" + className
						+ "; uri=" + resUri + "; poolSize=" + dao_pool_size);
			}

			for (int i = 0; i < dao_pool_size; i++) {
				dao_pool.addDao(null, new PubSubDAOJDBC(userRepository, this.componentConfig, resUri));
			}

			if (log.isLoggable(Level.CONFIG)) {
				log.config("Registered DAO for " + ((domain == null)
						? "default "
						: "") + "domain " + ((domain == null)
						? ""
						: domain));
			}
		}

		dao_pool.init();
		
		return dao_pool;
//		} else {
//			String domain    = null;
//			String resUri    = (String) resUris.get(null);
//			String className = default_cls_name;
//			int    dao_pool_size;
//
//			try {
//				dao_pool_size = Integer.parseInt((String) (poolSizes.containsKey(domain)
//						? poolSizes.get(domain)
//						: poolSizes.get(null)));
//			} catch (Exception ex) {
//				// we should set it at least to 10 to improve performace, 
//				// as previous value (1) was really not enought				
//				dao_pool_size = 10;
//			}
//
//			PubSubDAO dao;
//
//			if (dao_pool_size > 1) {
//				PubSubDAOPool dao_pool = new PubSubDAOPool(userRepository);
//
//				for (int i = 0; i < dao_pool_size; i++) {
//					dao_pool.addDao(null, new PubSubDAOJDBC(userRepository, this.componentConfig,
//							resUri));
//				}
//				dao = dao_pool;
//			} else {
//				dao = new PubSubDAOJDBC(userRepository, this.componentConfig, resUri);
//			}
//
//			return dao;
//		}
	}

	//~--- inner classes --------------------------------------------------------

	private class AdHocScriptCommandManagerImpl
					implements AdHocScriptCommandManager {
		private final PubSubComponent component;

		//~--- constructors -------------------------------------------------------

		/**
		 * Constructs ...
		 *
		 *
		 * @param component
		 */
		public AdHocScriptCommandManagerImpl(PubSubComponent component) {
			this.component = component;
		}

		//~--- methods ------------------------------------------------------------

		/**
		 * Method description
		 *
		 *
		 * @param packet is a <code>Packet</code>
		 *
		 * @return a value of <code>List<Packet></code>
		 */
		@Override
		public List<Packet> process(Packet packet) {
			Queue<Packet> results = new ArrayDeque<Packet>();

			if (component.processScriptCommand(packet, results)) {
				return new ArrayList<Packet>(results);
			}

			return null;
		}

		//~--- get methods --------------------------------------------------------

		/**
		 * Method description
		 *
		 *
		 * @param senderJid is a <code>JID</code>
		 * @param toJid is a <code>JID</code>
		 *
		 * @return a value of <code>List<Element></code>
		 */
		@Override
		public List<Element> getCommandListItems(JID senderJid, JID toJid) {
			return component.getScriptItems(Command.XMLNS, toJid, senderJid);
		}
	}
}


//~ Formatted in Tigase Code Convention on 13/10/16
