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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.Bindings;

import tigase.adhoc.AdHocScriptCommandManager;
import tigase.component2.AbstractComponent;
import tigase.component2.PacketWriter;
import tigase.conf.Configurable;
import tigase.conf.ConfigurationException;
import tigase.db.DBInitException;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.osgi.ModulesManagerImpl;
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
import tigase.pubsub.modules.commands.LoadTestCommand;
import tigase.pubsub.modules.commands.ReadAllNodesCommand;
import tigase.pubsub.modules.commands.RebuildDatabaseCommand;
import tigase.pubsub.modules.commands.RetrieveItemsCommand;
import tigase.pubsub.modules.ext.presence.PresenceNodeSubscriptions;
import tigase.pubsub.modules.ext.presence.PresenceNotifierModule;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.PubSubDAOPool;
import tigase.pubsub.repository.PubSubRepositoryWrapper;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.server.Command;
import tigase.server.DisableDisco;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;

/**
 * Class description
 *
 *
 * @version 5.1.0, 2010.11.02 at 01:05:02 MDT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PubSubComponent extends AbstractComponent<PubSubConfig> implements Configurable, DisableDisco {
	private class AdHocScriptCommandManagerImpl implements AdHocScriptCommandManager {
		private final PubSubComponent component;

		// ~--- constructors
		// -------------------------------------------------------

		/**
		 * Constructs ...
		 *
		 *
		 * @param component
		 */
		public AdHocScriptCommandManagerImpl(PubSubComponent component) {
			this.component = component;
		}

		// ~--- methods
		// ------------------------------------------------------------

		/**
		 * Method description
		 *
		 *
		 * @param senderJid
		 *            is a <code>JID</code>
		 * @param toJid
		 *            is a <code>JID</code>
		 *
		 * @return a value of <code>List<Element></code>
		 */
		@Override
		public List<Element> getCommandListItems(JID senderJid, JID toJid) {
			return component.getScriptItems(Command.XMLNS, toJid, senderJid);
		}

		// ~--- get methods
		// --------------------------------------------------------

		/**
		 * Method description
		 *
		 *
		 * @param packet
		 *            is a <code>Packet</code>
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
	}

	/** Field description */
	public static final String ADMINS_KEY = "admin";
	private static final String COMPONENT = "component";
	/** Field description */
	public static final String DEFAULT_LEAF_NODE_CONFIG_KEY = "default-node-config";

	private static final String MAX_CACHE_SIZE = "pubsub-repository-cache-size";
	private static final Pattern PARAMETRIZED_PROPERTY_PATTERN = Pattern.compile("(.+)\\[(.*)\\]|(.+)");

	/**
	 * Field description
	 */
	protected static final String PUBSUB_REPO_CLASS_PROP_KEY = "pubsub-repo-class";
	/**
	 * Field description
	 */
	protected static final String PUBSUB_REPO_POOL_SIZE_PROP_KEY = "pubsub-repo-pool-size";

	// ~--- fields
	// ---------------------------------------------------------------

	/**
	 * Field description
	 */
	protected static final String PUBSUB_REPO_URL_PROP_KEY = "pubsub-repo-url";

	/**
	 * Method description
	 *
	 *
	 * @param key
	 *            is a <code>String</code>
	 * @param props
	 *            is a <code>Map<String,Object></code>
	 *
	 * @return a value of <code>Map<String,Object></code>
	 */
	public static Map<String, Object> getProperties(String key, Map<String, Object> props) {
		Map<String, Object> result = new HashMap<String, Object>();

		for (Entry<String, Object> entry : props.entrySet()) {
			Matcher matcher = PARAMETRIZED_PROPERTY_PATTERN.matcher(entry.getKey());

			if (matcher.find()) {
				String keyBaseName = (matcher.group(1) != null) ? matcher.group(1) : matcher.group(3);
				String keyMod = matcher.group(2);

				if (keyBaseName.equals(key)) {
					result.put(keyMod, entry.getValue());
				}
			}
		}

		return result;
	}

	private AdHocConfigCommandModule adHocCommandsModule;

	protected CapsModule capsModule;
	/** Field description */
	protected LeafNodeConfig defaultNodeConfig;
	private PubSubDAO directPubSubRepository;
	/** Field description */
	protected Integer maxRepositoryCacheSize;

	/* modules */
	protected PendingSubscriptionModule pendingSubscriptionModule;
	protected PresenceCollectorModule presenceCollectorModule;
	protected PresenceNotifierModule presenceNotifierModule;
	protected PublishItemModule publishNodeModule;

	/** Field description */
	protected IPubSubRepository pubsubRepository;
	// ~--- constructors
	// ---------------------------------------------------------
	private AdHocScriptCommandManager scriptCommandManager;

	/** Field description */
	protected UserRepository userRepository;

	// ~--- methods
	// --------------------------------------------------------------

	private XsltTool xslTransformer;

	/**
	 * Constructs ...
	 *
	 */
	public PubSubComponent() {
		this.scriptCommandManager = new AdHocScriptCommandManagerImpl(this);
	}

	/**
	 * Method description
	 *
	 *
	 * @param abstractComponent
	 *            is a <code>AbstractComponent<?></code>
	 *
	 * @return a value of <code>PubSubConfig</code>
	 */
	@Override
	protected PubSubConfig createComponentConfigInstance(AbstractComponent<?> abstractComponent) {
		PubSubConfig result = new PubSubConfig(abstractComponent);

		return result;
	}

	/**
	 * Method description
	 *
	 *
	 * @param props
	 *            is a <code>Map<String,Object></code>
	 *
	 * @return a value of <code>PubSubDAO</code>
	 */
	protected PubSubDAO createDAO(Map<String, Object> props) throws RepositoryException {
		final Map<String, Object> classNames = getProperties(PUBSUB_REPO_CLASS_PROP_KEY, props);
		final Map<String, Object> resUris = getProperties(PUBSUB_REPO_URL_PROP_KEY, props);
		final Map<String, Object> poolSizes = getProperties(PUBSUB_REPO_POOL_SIZE_PROP_KEY, props);
		final String default_cls_name = (String) classNames.get(null);

		PubSubDAOPool dao_pool = new PubSubDAOPool();
		dao_pool.init(null, null, userRepository);

		for (Entry<String, Object> e : resUris.entrySet()) {
			String domain = e.getKey();
			String resUri = (String) e.getValue();
			String className = classNames.containsKey(domain) ? (String) classNames.get(domain) : null;
			Class<? extends IPubSubDAO> repoClass = null;
			if (className == null) {
				try {
					repoClass = RepositoryFactory.getRepoClass(IPubSubDAO.class, resUri);
				} catch (DBInitException ex) {
					log.log(Level.FINE, "could not autodetect PubSubDAO implementation for domain = {0} for uri = {1}",
							new Object[] { (domain == null ? "default" : domain), resUri });
				}
			}
			if (repoClass == null) {
				if (className == null)
					className = default_cls_name;
				try {
					repoClass = (Class<? extends IPubSubDAO>) ModulesManagerImpl.getInstance().forName(className);
				} catch (ClassNotFoundException ex) {
					throw new RepositoryException("could not find class " + className + " to use as PubSubDAO"
							+ " implementation for domain " + (domain == null ? "default" : domain), ex);
				}
			}
			int dao_pool_size;
			Map<String, String> repoParams = new HashMap<String, String>();

			try {
				Object value = (poolSizes.containsKey(domain) ? poolSizes.get(domain) : poolSizes.get(null));
				dao_pool_size = (value instanceof Integer) ? ((Integer) value) : Integer.parseInt((String) value);
			} catch (Exception ex) {
				// we should set it at least to 10 to improve performace,
				// as previous value (1) was really not enought
				dao_pool_size = 10;
			}
			if (log.isLoggable(Level.FINER)) {
				log.finer("Creating DAO for domain=" + domain + "; class="
						+ (repoClass == null ? className : repoClass.getCanonicalName()) + "; uri=" + resUri + "; poolSize="
						+ dao_pool_size);
			}

			for (int i = 0; i < dao_pool_size; i++) {
				try {
					IPubSubDAO dao = repoClass.newInstance();
					dao.init(resUri, repoParams, userRepository);
					dao_pool.addDao(domain == null ? null : BareJID.bareJIDInstanceNS(domain), dao);
				} catch (InstantiationException ex) {
					throw new RepositoryException("Cound not create instance of " + repoClass.getCanonicalName(), ex);
				} catch (IllegalAccessException ex) {
					throw new RepositoryException("Cound not create instance of " + repoClass.getCanonicalName(), ex);
				}
			}

			if (log.isLoggable(Level.CONFIG)) {
				log.config("Registered DAO for " + ((domain == null) ? "default " : "") + "domain "
						+ ((domain == null) ? "" : domain));
			}
		}

		return dao_pool;
	}

	// ~--- get methods
	// ----------------------------------------------------------

	// ~--- methods
	// --------------------------------------------------------------

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
	 *
	 * @param params
	 *
	 * @return
	 */
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = super.getDefaults(params);

		// By default use the same repository as all other components:
		String repo_uri = null;

		if (params.get(RepositoryFactory.GEN_USER_DB_URI) != null) {
			repo_uri = (String) params.get(RepositoryFactory.GEN_USER_DB_URI);
		} // end of if (params.get(GEN_USER_DB_URI) != null)
		props.put(PUBSUB_REPO_URL_PROP_KEY, repo_uri);
		props.put(PUBSUB_REPO_POOL_SIZE_PROP_KEY, 10);
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

	@Override
	public String getDiscoDescription() {
		return "PubSub";
	}

	@Override
	public int hashCodeForPacket(Packet packet) {
		int hash = packet.hashCode();
		

		return hash;
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

	/**
	 * Method description
	 *
	 *
	 * @param binds
	 *            is a <code>Bindings</code>
	 */
	@Override
	public void initBindings(Bindings binds) {
		super.initBindings(binds); // To change body of generated methods,

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
	public void initialize(String[] admins, PubSubDAO pubSubDAO, IPubSubRepository createPubSubRepository,
			LeafNodeConfig defaultNodeConfig) throws UserNotFoundException, TigaseDBException, RepositoryException {
		this.componentConfig.setAdmins(admins);

		// this.componentConfig.setServiceName("tigase-pubsub");

		// create pubsub user if it doesn't exist
		if (!userRepository.userExists(componentConfig.getServiceBareJID())) {
			userRepository.addUser(componentConfig.getServiceBareJID());
		}
		this.directPubSubRepository = pubSubDAO;
		this.pubsubRepository = createPubSubRepository(pubSubDAO);
		this.defaultNodeConfig = defaultNodeConfig;
		this.defaultNodeConfig.read(userRepository, componentConfig, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
		this.defaultNodeConfig.write(userRepository, componentConfig, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
		this.componentConfig.setPubSubRepository(pubsubRepository);
		init();

		final DefaultConfigCommand configCommand = new DefaultConfigCommand(this.componentConfig, this.userRepository);

		configCommand.addDefaultNodeConfigurationChangedHandler(new DefaultNodeConfigurationChangedHandler() {
			@Override
			public void onDefaultConfigurationChanged(Packet packet, PubSubConfig config) {
				onChangeDefaultNodeConfig();
			}
		});
		this.adHocCommandsModule.register(new RebuildDatabaseCommand(this.componentConfig, this.directPubSubRepository));
		this.adHocCommandsModule.register(configCommand);
		this.adHocCommandsModule.register(new DeleteAllNodesCommand(this.componentConfig, this.directPubSubRepository,
				this.userRepository));
		this.adHocCommandsModule.register(new LoadTestCommand(this.componentConfig, this.pubsubRepository, this));
		this.adHocCommandsModule.register(new ReadAllNodesCommand(this.componentConfig, this.directPubSubRepository,
				this.pubsubRepository));
		this.adHocCommandsModule.register(new RetrieveItemsCommand(this.componentConfig, this.pubsubRepository,
				this.userRepository));
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
	 */
	public void onChangeDefaultNodeConfig() {
		try {
			this.defaultNodeConfig.read(userRepository, componentConfig, DEFAULT_LEAF_NODE_CONFIG_KEY);
			log.info("Node " + getComponentId() + " read default node configuration.");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Reading default config error", e);
		}
	}

	// ~--- set methods
	// ----------------------------------------------------------

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

	// ~--- methods
	// --------------------------------------------------------------

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

	// ~--- set methods
	// ----------------------------------------------------------

	@Override
	public void processPacket(Packet packet) {
		// if stanza is addressed to getName()@domain then we need to return
		// SERVICE_UNAVAILABLE error
		if (packet.getStanzaTo() != null && getName().equals(packet.getStanzaTo().getLocalpart()) && packet.getType() != StanzaType.result) {
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

	// ~--- inner classes
	// --------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param props
	 */
	@Override
	public void setProperties(Map<String, Object> props) throws ConfigurationException {
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
		userRepository = (UserRepository) props.get(RepositoryFactory.SHARED_USER_REPO_PROP_KEY);

		try {
			// I suppose that this code is useless as ConfiguratorAbstract will
			// pass proper instance
			// in props map under RepositoryFactory.SHARED_USER_REPO_PROP_KEY
			// key which is checked
			// already above. Moreover we should not relay on creation of
			// UserRepository here using
			// PubSub repository class property and PubSub repository URI - this
			// is wrong!!
			// String cls_name = (String) props.get(PUBSUB_REPO_CLASS_PROP_KEY);
			// String res_uri = (String) props.get(PUBSUB_REPO_URL_PROP_KEY);
			//
			// if (userRepository == null) {
			// userRepository = RepositoryFactory.getUserRepository(cls_name,
			// res_uri, null);
			// userRepository.initRepository(res_uri, null);
			// log.log(Level.CONFIG,
			// "Initialized {0} as pubsub repository: {1}", new
			// Object[]{cls_name, res_uri});
			// }
			PubSubDAO dao = createDAO(props);
			initialize((String[]) props.get(ADMINS_KEY), dao, null, new LeafNodeConfig("default"));
		} catch (Exception e) {
			log.severe("Can't initialize pubsub repository: " + e);
			e.printStackTrace();
		}
	}
}

// ~ Formatted in Tigase Code Convention on 13/10/16
