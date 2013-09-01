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

//~--- non-JDK imports --------------------------------------------------------

import java.util.ArrayDeque;
import tigase.conf.Configurable;

import tigase.criteria.Criteria;

import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;

import tigase.disco.ServiceEntity;
import tigase.disco.ServiceIdentity;
import tigase.disco.XMPPService;

import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.AdHocConfigCommandModule;
import tigase.pubsub.modules.commands.DefaultConfigCommand;
import tigase.pubsub.modules.commands.DeleteAllNodesCommand;
import tigase.pubsub.modules.commands.ReadAllNodesCommand;
import tigase.pubsub.modules.commands.RebuildDatabaseCommand;
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
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.pubsub.repository.cached.Node;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.PubSubDAOJDBC;
import tigase.pubsub.repository.PubSubDAOPool;
import tigase.pubsub.repository.RepositoryException;

import tigase.server.AbstractMessageReceiver;
import tigase.server.DisableDisco;
import tigase.server.Iq;
import tigase.server.Packet;

import tigase.stats.StatisticsList;

import tigase.util.DNSResolver;
import tigase.util.TigaseStringprepException;

import tigase.xml.Element;

import tigase.xmpp.Authorization;
import tigase.xmpp.JID;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;

//~--- JDK imports ------------------------------------------------------------

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.script.Bindings;
import tigase.adhoc.AdHocScriptCommandManager;
import tigase.server.Command;
import tigase.xmpp.BareJID;

/**
 * Class description
 *
 *
 * @version 5.1.0, 2010.11.02 at 01:05:02 MDT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PubSubComponent
				extends AbstractMessageReceiver
				implements XMPPService, Configurable, DisableDisco, DefaultNodeConfigListener {
	/** Field description */
	public static final String ADMINS_KEY = "admin";

	private static final String COMPONENT = "component";
	
	/** Field description */
	public static final String DEFAULT_LEAF_NODE_CONFIG_KEY = "default-node-config";

	/** Field description */
	protected static final String PUBSUB_REPO_CLASS_PROP_KEY = "pubsub-repo-class";

	/** Field description */
	protected static final String PUBSUB_REPO_POOL_SIZE_PROP_KEY = "pubsub-repo-pool-size";

	// ~--- fields
	// ---------------------------------------------------------------

	/** Field description */
	protected static final String PUBSUB_REPO_URL_PROP_KEY = "pubsub-repo-url";
	private static final Set<String> intReasons            = new HashSet<String>() {
		private static final long serialVersionUID = 1L;
		{
			add("gone");
			add("item-not-found");
			add("recipient-unavailable");
			add("redirect");
			add("remote-server-not-found");
			add("remote-server-timeout");
		}
	};
	private static final String MAX_CACHE_SIZE = "pubsub-repository-cache-size";

	/** Field description */
	public static final Set<String> R = Collections.unmodifiableSet(intReasons);

	//~--- fields ---------------------------------------------------------------

	/** Field description */
	public String[] HOSTNAMES_PROP_VAL = { "localhost", "hostname" };
	int lastNodeNo                     = -1;

	/** Field description */
	protected final PubSubConfig config = new PubSubConfig();

	/** Field description */
	protected Logger log = Logger.getLogger(this.getClass().getName());

	/** Field description */
	protected final ArrayList<Module> modules = new ArrayList<Module>();

	/** Field description */
	protected AdHocConfigCommandModule adHocCommandsModule;

	/** Field description */
	protected DefaultConfigModule defaultConfigModule;

	/** Field description */
	protected LeafNodeConfig defaultNodeConfig;

	/** Field description */
	protected PubSubDAO directPubSubRepository;

	/** Field description */
	protected final PacketWriter packetWriter;

	/** Field description */
	protected ManageAffiliationsModule manageAffiliationsModule;

	/** Field description */
	protected ManageSubscriptionModule manageSubscriptionModule;

	/** Field description */
	protected NodeConfigModule nodeConfigModule;

	/** Field description */
	protected NodeCreateModule nodeCreateModule;

	/** Field description */
	protected NodeDeleteModule nodeDeleteModule;

	/** Field description */
	protected PendingSubscriptionModule pendingSubscriptionModule;

	/** Field description */
	protected PresenceCollectorModule presenceCollectorModule;

	/** Field description */
	protected PublishItemModule publishNodeModule;

	/** Field description */
	protected CachedPubSubRepository pubsubRepository;

	/** Field description */
	protected PurgeItemsModule purgeItemsModule;

	/** Field description */
	protected RetractItemModule retractItemModule;

	/** Field description */
	protected RetrieveItemsModule retrirveItemsModule;

	protected AdHocScriptCommandManager scriptCommandManager;
	
	/** Field description */
	protected ServiceEntity serviceEntity;

	/** Field description */
	protected AbstractModule subscribeNodeModule;

	/** Field description */
	protected UnsubscribeNodeModule unsubscribeNodeModule;

	// ~--- constructors
	// ---------------------------------------------------------

	/** Field description */
	protected UserRepository userRepository;

	// ~--- get methods
	// ----------------------------------------------------------

	/** Field description */
	protected XsltTool xslTransformer;
	private Integer maxRepositoryCacheSize;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 */
	public PubSubComponent() {
		setName("pubsub");
		this.packetWriter = new PacketWriter() {
			@Override
			public void write(Collection<Packet> packets) {
				if (packets != null) {
					for (Packet packet : packets) {
						if (packet != null) {
							write(packet);
						}
					}
				}
			}
			@Override
			public void write(final Packet packet) {
				if (packet != null) {
					addOutPacket(packet);
				}
			}
		};
		this.scriptCommandManager = new AdHocScriptCommandManagerImpl(this);
	}

	//~--- methods --------------------------------------------------------------

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

	private void detectGhosts(Packet packet) {
		try {
			if (packet.getType() == StanzaType.error) {
				final String cond = packet.getErrorCondition();

				if ((cond != null) && R.contains(cond)) {
					dropGhost(packet.getStanzaTo().getBareJID(), packet.getStanzaFrom());
				}
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Problem on killing Ghost", e);
		}
	}

	private void dropGhost(BareJID toJid, JID stanzaFrom) throws RepositoryException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Processing ghost: " + stanzaFrom);
		}
		for (Node n : pubsubRepository.getAllNodes()) {
			if (n.getNodeConfig().isPresenceExpired() &&
					(n.getNodeSubscriptions().getSubscription(
						stanzaFrom.getBareJID().toString()) != Subscription.none)) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Found ghost: " + stanzaFrom + " in " + n.getName() +
										 ". Killing...");
				}

				ISubscriptions s = pubsubRepository.getNodeSubscriptions(toJid, n.getName());

				s.changeSubscription(stanzaFrom.getBareJID().toString(), Subscription.none);
				if (s.isChanged()) {
					this.pubsubRepository.update(toJid, n.getName(), s);
				}
			}
		}
	}

	// ~--- methods
	// --------------------------------------------------------------
	// @Override
	// public void everySecond() {
	// super.everySecond();
	// if (this.pubsubRepository != null)
	// this.pubsubRepository.doLazyWrite();
	// }

	/**
	 * Method description
	 *
	 *
	 * @param element
	 *
	 * @return
	 */
	protected String extractNodeName(Element element) {
		if (element == null) {
			return null;
		}

		Element ps    = element.getChild("pubsub");
		Element query = element.getChild("query");

		if (ps != null) {
			List<Element> children = ps.getChildren();

			if (children != null) {
				for (Element e : children) {
					String n = e.getAttributeStaticStr("node");

					if (n != null) {
						return n;
					}
				}
			}
		} else {
			if (query != null) {
				String n = query.getAttributeStaticStr("node");

				return n;
			}
		}

		return null;
	}

	//~--- get methods ----------------------------------------------------------

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

		if (params.get(GEN_VIRT_HOSTS) != null) {
			HOSTNAMES_PROP_VAL = ((String) params.get(GEN_VIRT_HOSTS)).split(",");
		} else {
			HOSTNAMES_PROP_VAL = DNSResolver.getDefHostNames();
		}

		String[] hostnames = new String[HOSTNAMES_PROP_VAL.length];
		int i              = 0;

		for (String host : HOSTNAMES_PROP_VAL) {
			hostnames[i++] = getName() + "." + host;
		}
		props.put(HOSTNAMES_PROP_KEY, hostnames);

		// By default use the same repository as all other components:
		String repo_class = DERBY_REPO_CLASS_PROP_VAL;
		String repo_uri   = DERBY_REPO_URL_PROP_VAL;
		String conf_db    = null;

		if (params.get(GEN_USER_DB) != null) {
			conf_db = (String) params.get(GEN_USER_DB);
		}    // end of if (params.get(GEN_USER_DB) != null)
		if (conf_db != null) {
			if (conf_db.equals("mysql")) {
				repo_class = MYSQL_REPO_CLASS_PROP_VAL;
				repo_uri   = MYSQL_REPO_URL_PROP_VAL;
			}
			if (conf_db.equals("pgsql")) {
				repo_class = PGSQL_REPO_CLASS_PROP_VAL;
				repo_uri   = PGSQL_REPO_URL_PROP_VAL;
			}
		}    // end of if (conf_db != null)
		if (params.get(GEN_USER_DB_URI) != null) {
			repo_uri = (String) params.get(GEN_USER_DB_URI);
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
	 * @return
	 */
	@Override
	public List<Element> getDiscoFeatures() {
		return null;
	}

	/**
	 * Method description
	 *
	 *
	 * @param node
	 * @param jid
	 *
	 * @return
	 */
	@Override
	public Element getDiscoInfo(String node, JID jid) {
		return null;
	}

	/**
	 * Method description
	 *
	 *
	 * @param node
	 * @param jid
	 *
	 * @return
	 */
	@Override
	public List<Element> getDiscoItems(String node, JID jid) {
		if (node == null) {
			Element result = serviceEntity.getDiscoItem(null, getName() + "." + jid);

			return Arrays.asList(result);
		} else {
			return null;
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param list
	 */
	@Override
	public void getStatistics(StatisticsList list) {
		super.getStatistics(list);
		this.pubsubRepository.addStats(getName(), list);
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * This method overwrites default packet hashCode calculation from a
	 * destination address to node name if possible so all packets for the same
	 * pubsub node are processed on the same thread. If there is no node name
	 * then the source address is used to get a better packets distribution to
	 * different threads.
	 *
	 * @param packet
	 * @return
	 */
	@Override
	public int hashCodeForPacket(Packet packet) {
		List<Element> children = packet.getElemChildrenStaticStr(Iq.IQ_PUBSUB_PATH);

		if (children != null) {
			for (Element elem : children) {
				String node_name = elem.getAttributeStaticStr("node");

				if (node_name != null) {
					return node_name.hashCode();
				}
			}
		}

		return packet.getFrom().hashCode();
	}

	/**
	 * Method description
	 *
	 */
	protected void init() {
		this.xslTransformer          = new XsltTool();
		this.presenceCollectorModule = registerModule(new PresenceCollectorModule());
		this.publishNodeModule       = registerModule(new PublishItemModule(this.config,
						this.pubsubRepository, this.xslTransformer, this.presenceCollectorModule));
		this.retractItemModule = registerModule(new RetractItemModule(this.config,
						this.pubsubRepository, this.publishNodeModule));
		this.pendingSubscriptionModule =
			registerModule(new PendingSubscriptionModule(this.config, this.pubsubRepository));
		this.manageSubscriptionModule =
			registerModule(new ManageSubscriptionModule(this.config, this.pubsubRepository));
		this.subscribeNodeModule = registerModule(new SubscribeNodeModule(this.config,
						this.pubsubRepository, this.pendingSubscriptionModule));
		this.nodeCreateModule = registerModule(new NodeCreateModule(this.config,
						this.pubsubRepository, this.defaultNodeConfig, this.publishNodeModule));
		this.nodeDeleteModule = registerModule(new NodeDeleteModule(this.config,
						this.pubsubRepository, this.publishNodeModule));
		this.defaultConfigModule = registerModule(new DefaultConfigModule(this.config,
						this.pubsubRepository, this.defaultNodeConfig));
		this.nodeConfigModule = registerModule(new NodeConfigModule(this.config,
						this.pubsubRepository, this.defaultNodeConfig, this.publishNodeModule));
		this.unsubscribeNodeModule = registerModule(new UnsubscribeNodeModule(this.config,
						this.pubsubRepository));
		this.manageAffiliationsModule =
			registerModule(new ManageAffiliationsModule(this.config, this.pubsubRepository));
		this.retrirveItemsModule = registerModule(new RetrieveItemsModule(this.config,
						this.pubsubRepository));
		this.purgeItemsModule = registerModule(new PurgeItemsModule(this.config,
						this.pubsubRepository, this.publishNodeModule));
		registerModule(new JabberVersionModule());
		this.adHocCommandsModule = registerModule(new AdHocConfigCommandModule(this.config,
						this.pubsubRepository, this.scriptCommandManager));		
		registerModule(new DiscoverInfoModule(this.config, this.pubsubRepository,
						this.modules));
		registerModule(new DiscoverItemsModule(this.config, this.pubsubRepository,
						this.adHocCommandsModule));
		registerModule(new RetrieveAffiliationsModule(this.config, this.pubsubRepository));
		registerModule(new RetrieveSubscriptionsModule(this.config, this.pubsubRepository));
		registerModule(new XmppPingModule());
		this.pubsubRepository.init();
	}

	// ~--- set methods
	// ----------------------------------------------------------

	@Override
	public void initBindings(Bindings binds) {
		super.initBindings(binds); //To change body of generated methods, choose Tools | Templates.
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
												 IPubSubRepository createPubSubRepository,
												 LeafNodeConfig defaultNodeConfig)
					throws UserNotFoundException, TigaseDBException, RepositoryException {
		serviceEntity = new ServiceEntity(getName(), null, "Publish-Subscribe");
		serviceEntity.addIdentities(new ServiceIdentity("pubsub", "service",
						"Publish-Subscribe"));
		serviceEntity.addFeatures("http://jabber.org/protocol/pubsub");
		this.config.setAdmins(admins);
		this.config.setServiceName("tigase-pubsub");

		// XXX remove ASAP
		if (pubSubDAO != null) {
			pubSubDAO.init();
		}
		this.directPubSubRepository = pubSubDAO;
		this.pubsubRepository       = createPubSubRepository(pubSubDAO);
		this.defaultNodeConfig      = defaultNodeConfig;
		this.defaultNodeConfig.read(userRepository, config,
																PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
		this.defaultNodeConfig.write(userRepository, config,
																 PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
		init();

		final DefaultConfigCommand configCommand = new DefaultConfigCommand(this.config,
																								 this.userRepository);

		configCommand.addListener(this);
		this.adHocCommandsModule.register(new RebuildDatabaseCommand(this.config,
						this.directPubSubRepository));
		this.adHocCommandsModule.register(configCommand);
		this.adHocCommandsModule.register(new DeleteAllNodesCommand(this.config,
						this.directPubSubRepository, this.userRepository));
		this.adHocCommandsModule.register(new ReadAllNodesCommand(this.config,
						this.directPubSubRepository, this.pubsubRepository));
	}

	//~--- get methods ----------------------------------------------------------

	// ~--- methods
	// --------------------------------------------------------------

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

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public String myDomain() {
		return getName() + "." + getDefHostName();
	}

	/**
	 * Method description
	 *
	 */
	@Override
	public void onChangeDefaultNodeConfig() {
		try {
			this.defaultNodeConfig.read(userRepository, config,
																	PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
			log.info("Node " + getComponentId() + " read default node configuration.");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Reading default config error", e);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param element
	 * @param writer
	 *
	 * @throws PacketErrorTypeException
	 */
	public void process(final Packet packet, final PacketWriter writer)
					throws PacketErrorTypeException {
		try {
			boolean handled = runModules(packet, writer);

			if (!handled) {
				final StanzaType type = packet.getType();

				if (type != StanzaType.error) {
					throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED);
				} else {
					log.finer(packet.getElemName() + " stanza with type='error' ignored");
				}
			}
		} catch (PubSubException e) {
			log.log(Level.INFO, "Exception thrown for " + packet.toString(), e);

			Packet result = e.getErrorCondition().getResponseMessage(packet, e.getMessage(), true);

			log.log(Level.INFO, "Sending back: " + result.toString());
			writer.write(result);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public int processingThreads() {

		// Does not support concurrency!!
		// Test it extensively before switching on, ask for more details if you
		// need
		// return 1 + (Runtime.getRuntime().availableProcessors() / 2);
		return 1;
	}

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 */
	@Override
	public void processPacket(final Packet packet) {
		detectGhosts(packet);
		try {
			process(packet, this.packetWriter);
		} catch (Exception e) {
			log.log(Level.WARNING, "Unexpected exception: internal-server-error", e);
			e.printStackTrace();
			try {
				addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
								e.getMessage(), true));
			} catch (PacketErrorTypeException e1) {
				e1.printStackTrace();
				log.throwing("PubSub Service", "processPacket (sending internal-server-error)",
										 e);
			}
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param module
	 * @param <T>
	 *
	 * @return
	 */
	public <T extends Module> T registerModule(final T module) {
		log.config("Register PubSub plugin: " + module.getClass().getCanonicalName());
		this.modules.add(module);

		return module;
	}

	/**
	 * Method description
	 *
	 *
	 * @param element
	 * @param writer
	 *
	 * @return
	 *
	 * @throws PubSubException
	 */
	protected boolean runModules(final Packet packet, final PacketWriter writer)
					throws PubSubException {
		boolean handled = false;

		if (log.isLoggable(Level.FINER)) {
			log.finest("Processing packet: " + packet.toString());
		}
		for (Module module : this.modules) {
			Criteria criteria = module.getModuleCriteria();

			if ((criteria != null) && criteria.match(packet.getElement())) {
				handled = true;
				if (log.isLoggable(Level.FINER)) {
					log.finest("Handled by module " + module.getClass());
				}

				List<Packet> result = module.process(packet, writer);

				if (result != null) {
					writer.write(result);
					
					return true;
				}
			}
		}

		return handled;
	}

	;

	//~--- set methods ----------------------------------------------------------

	// ~--- methods
	// --------------------------------------------------------------

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
		modules.clear();

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
			String res_uri  = (String) props.get(PUBSUB_REPO_URL_PROP_KEY);

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
				dao_pool_size =
					Integer.parseInt((String) props.get(PUBSUB_REPO_POOL_SIZE_PROP_KEY));
			} catch (Exception e) {
				dao_pool_size = 1;
			}
			if (log.isLoggable(Level.FINE)) {
				log.fine("PubSubDAO pool size: " + dao_pool_size);
			}
			if (dao_pool_size > 1) {
				PubSubDAOPool dao_pool = new PubSubDAOPool(userRepository, this.config);

				for (int i = 0; i < dao_pool_size; i++) {
					if (cls_name.equals("tigase.pubsub.repository.PubSubDAOJDBC")) {
						dao_pool.addDao(new PubSubDAOJDBC(userRepository, this.config, res_uri));
					} else {
						dao_pool.addDao(new PubSubDAO(userRepository, this.config));
					}
				}
				dao = dao_pool;
			} else {
				if (cls_name.equals("tigase.pubsub.repository.PubSubDAOJDBC")) {
					dao = new PubSubDAOJDBC(userRepository, this.config, res_uri);
				} else {
					dao = new PubSubDAO(userRepository, this.config);
				}
			}
			initialize((String[]) props.get(ADMINS_KEY), dao, null,
								 new LeafNodeConfig("default"));
		} catch (Exception e) {
			log.severe("Can't initialize pubsub repository: " + e);
			e.printStackTrace();
		}

		StringBuilder sb = new StringBuilder();

		for (Module module : this.modules) {
			String[] features = module.getFeatures();

			if (features != null) {
				for (String f : features) {
					sb.append(f);
					sb.append('\n');
					serviceEntity.addFeatures(f);
				}
			}
		}
		log.config("Supported features: " + sb.toString());
	}
	
	private class AdHocScriptCommandManagerImpl implements AdHocScriptCommandManager {

		private final PubSubComponent component;
		
		public AdHocScriptCommandManagerImpl(PubSubComponent component) {
			this.component = component;
		}
		
		@Override
		public List<Element> getCommandListItems(String senderJid, String toJid) {
			try {
				return component.getScriptItems(Command.XMLNS, JID.jidInstance(toJid), JID.jidInstance(senderJid));
			} catch (TigaseStringprepException ex) {
				log.warning("could not process jid, should not happend...");
				return null;
			}
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
}



// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com


//~ Formatted in Tigase Code Convention on 13/02/20
