/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
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
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */

package tigase.pubsub;

//~--- non-JDK imports --------------------------------------------------------

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import tigase.server.AbstractMessageReceiver;
import tigase.server.DisableDisco;
import tigase.server.Packet;
import tigase.stats.StatisticsList;
import tigase.util.DNSResolver;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.JID;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;

//~--- classes ----------------------------------------------------------------

/**
 * Class description
 * 
 * 
 * @version 5.1.0, 2010.11.02 at 01:05:02 MDT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PubSubComponent extends AbstractMessageReceiver implements XMPPService, Configurable, DisableDisco,
		DefaultNodeConfigListener {

	/** Field description */
	public static final String ADMINS_KEY = "admin";

	/** Field description */
	public static final String DEFAULT_LEAF_NODE_CONFIG_KEY = "default-node-config";
	private static final String MAX_CACHE_SIZE = "pubsub-repository-cache-size";
	protected static final String PUBSUB_REPO_CLASS_PROP_KEY = "pubsub-repo-class";
	protected static final String PUBSUB_REPO_POOL_SIZE_PROP_KEY = "pubsub-repo-pool-size";
	protected static final String PUBSUB_REPO_URL_PROP_KEY = "pubsub-repo-url";

	// ~--- fields
	// ---------------------------------------------------------------

	protected AdHocConfigCommandModule adHocCommandsModule;
	protected final PubSubConfig config = new PubSubConfig();
	protected DefaultConfigModule defaultConfigModule;
	protected LeafNodeConfig defaultNodeConfig;
	protected PubSubDAO directPubSubRepository;
	protected final ElementWriter elementWriter;
	/** Field description */
	public String[] HOSTNAMES_PROP_VAL = { "localhost", "hostname" };
	int lastNodeNo = -1;
	protected Logger log = Logger.getLogger(this.getClass().getName());
	protected ManageAffiliationsModule manageAffiliationsModule;
	protected ManageSubscriptionModule manageSubscriptionModule;
	private Integer maxRepositoryCacheSize;
	protected final ArrayList<Module> modules = new ArrayList<Module>();
	protected NodeConfigModule nodeConfigModule;
	protected NodeCreateModule nodeCreateModule;
	protected NodeDeleteModule nodeDeleteModule;
	protected PendingSubscriptionModule pendingSubscriptionModule;
	protected PresenceCollectorModule presenceCollectorModule;
	protected PublishItemModule publishNodeModule;
	protected CachedPubSubRepository pubsubRepository;
	protected PurgeItemsModule purgeItemsModule;
	protected RetractItemModule retractItemModule;
	protected RetrieveItemsModule retrirveItemsModule;
	protected ServiceEntity serviceEntity;
	protected AbstractModule subscribeNodeModule;
	protected UnsubscribeNodeModule unsubscribeNodeModule;
	protected UserRepository userRepository;
	protected XsltTool xslTransformer;

	// ~--- constructors
	// ---------------------------------------------------------

	/**
	 * Constructs ...
	 * 
	 */
	public PubSubComponent() {
		setName("pubsub");
		this.elementWriter = new ElementWriter() {
			@Override
			public void write(Collection<Element> elements) {
				if (elements != null) {
					for (Element element : elements) {
						if (element != null) {
							write(element);
						}
					}
				}
			}

			@Override
			public void write(final Element element) {
				if (element != null) {
					try {
						addOutPacket(Packet.packetInstance(element));
					} catch (TigaseStringprepException ex) {
						log.info("Packet addressing problem, stringprep failed: " + element);
					}
				}
			}
		};
	}

	// ~--- get methods
	// ----------------------------------------------------------

	protected CachedPubSubRepository createPubSubRepository(PubSubDAO directRepository) {

		// return new StatelessPubSubRepository(directRepository, this.config);
		return new CachedPubSubRepository(directRepository, maxRepositoryCacheSize);
	}

	// @Override
	// public void everySecond() {
	// super.everySecond();
	// if (this.pubsubRepository != null)
	// this.pubsubRepository.doLazyWrite();
	// }
	protected String extractNodeName(Element element) {
		if (element == null) {
			return null;
		}

		Element ps = element.getChild("pubsub");
		Element query = element.getChild("query");

		if (ps != null) {
			List<Element> children = ps.getChildren();

			if (children != null) {
				for (Element e : children) {
					String n = e.getAttribute("node");

					if (n != null) {
						return n;
					}
				}
			}
		} else {
			if (query != null) {
				String n = query.getAttribute("node");

				return n;
			}
		}

		return null;
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

		if (params.get(GEN_VIRT_HOSTS) != null) {
			HOSTNAMES_PROP_VAL = ((String) params.get(GEN_VIRT_HOSTS)).split(",");
		} else {
			HOSTNAMES_PROP_VAL = DNSResolver.getDefHostNames();
		}

		String[] hostnames = new String[HOSTNAMES_PROP_VAL.length];
		int i = 0;

		for (String host : HOSTNAMES_PROP_VAL) {
			hostnames[i++] = getName() + "." + host;
		}

		props.put(HOSTNAMES_PROP_KEY, hostnames);

		// By default use the same repository as all other components:
		String repo_class = DERBY_REPO_CLASS_PROP_VAL;
		String repo_uri = DERBY_REPO_URL_PROP_VAL;
		String conf_db = null;

		if (params.get(GEN_USER_DB) != null) {
			conf_db = (String) params.get(GEN_USER_DB);
		} // end of if (params.get(GEN_USER_DB) != null)

		if (conf_db != null) {
			if (conf_db.equals("mysql")) {
				repo_class = MYSQL_REPO_CLASS_PROP_VAL;
				repo_uri = MYSQL_REPO_URL_PROP_VAL;
			}

			if (conf_db.equals("pgsql")) {
				repo_class = PGSQL_REPO_CLASS_PROP_VAL;
				repo_uri = PGSQL_REPO_URL_PROP_VAL;
			}
		} // end of if (conf_db != null)

		if (params.get(GEN_USER_DB_URI) != null) {
			repo_uri = (String) params.get(GEN_USER_DB_URI);
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

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public List<Element> getDiscoFeatures() {

		// TODO Auto-generated method stub
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

	// ~--- methods
	// --------------------------------------------------------------

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
		List<Element> children = packet.getElemChildren("/iq/pubsub");

		if (children != null) {
			for (Element elem : children) {
				String node_name = elem.getAttribute("node");

				if (node_name != null) {
					return node_name.hashCode();
				}
			}
		}

		return packet.getFrom().hashCode();
	}

	protected void init() {
		this.xslTransformer = new XsltTool();
		this.presenceCollectorModule = registerModule(new PresenceCollectorModule());
		this.publishNodeModule = registerModule(new PublishItemModule(this.config, this.pubsubRepository, this.xslTransformer,
				this.presenceCollectorModule));
		this.retractItemModule = registerModule(new RetractItemModule(this.config, this.pubsubRepository,
				this.publishNodeModule));
		this.pendingSubscriptionModule = registerModule(new PendingSubscriptionModule(this.config, this.pubsubRepository));
		this.manageSubscriptionModule = registerModule(new ManageSubscriptionModule(this.config, this.pubsubRepository));
		this.subscribeNodeModule = registerModule(new SubscribeNodeModule(this.config, this.pubsubRepository,
				this.pendingSubscriptionModule));
		this.nodeCreateModule = registerModule(new NodeCreateModule(this.config, this.pubsubRepository, this.defaultNodeConfig,
				this.publishNodeModule));
		this.nodeDeleteModule = registerModule(new NodeDeleteModule(this.config, this.pubsubRepository, this.publishNodeModule));
		this.defaultConfigModule = registerModule(new DefaultConfigModule(this.config, this.pubsubRepository,
				this.defaultNodeConfig));
		this.nodeConfigModule = registerModule(new NodeConfigModule(this.config, this.pubsubRepository, this.defaultNodeConfig,
				this.publishNodeModule));
		this.unsubscribeNodeModule = registerModule(new UnsubscribeNodeModule(this.config, this.pubsubRepository));
		this.manageAffiliationsModule = registerModule(new ManageAffiliationsModule(this.config, this.pubsubRepository));
		this.retrirveItemsModule = registerModule(new RetrieveItemsModule(this.config, this.pubsubRepository));
		this.purgeItemsModule = registerModule(new PurgeItemsModule(this.config, this.pubsubRepository, this.publishNodeModule));
		registerModule(new JabberVersionModule());
		this.adHocCommandsModule = registerModule(new AdHocConfigCommandModule(this.config, this.pubsubRepository));
		registerModule(new DiscoverInfoModule(this.config, this.pubsubRepository, this.modules));
		registerModule(new DiscoverItemsModule(this.config, this.pubsubRepository, this.adHocCommandsModule));
		registerModule(new RetrieveAffiliationsModule(this.config, this.pubsubRepository));
		registerModule(new RetrieveSubscriptionsModule(this.config, this.pubsubRepository));
		registerModule(new XmppPingModule());
		this.pubsubRepository.init();
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
		serviceEntity = new ServiceEntity(getName(), null, "Publish-Subscribe");
		serviceEntity.addIdentities(new ServiceIdentity("pubsub", "service", "Publish-Subscribe"));
		serviceEntity.addFeatures("http://jabber.org/protocol/pubsub");
		this.config.setAdmins(admins);
		this.config.setServiceName("tigase-pubsub");

		// XXX remove ASAP
		if (pubSubDAO != null) {
			pubSubDAO.init();
		}

		this.directPubSubRepository = pubSubDAO;
		this.pubsubRepository = createPubSubRepository(pubSubDAO);
		this.defaultNodeConfig = defaultNodeConfig;
		this.defaultNodeConfig.read(userRepository, config, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
		this.defaultNodeConfig.write(userRepository, config, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
		init();

		final DefaultConfigCommand configCommand = new DefaultConfigCommand(this.config, this.userRepository);

		configCommand.addListener(this);
		this.adHocCommandsModule.register(new RebuildDatabaseCommand(this.config, this.directPubSubRepository));
		this.adHocCommandsModule.register(configCommand);
		this.adHocCommandsModule.register(new DeleteAllNodesCommand(this.config, this.directPubSubRepository,
				this.userRepository));
		this.adHocCommandsModule.register(new ReadAllNodesCommand(this.config, this.directPubSubRepository,
				this.pubsubRepository));
	}

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
			this.defaultNodeConfig.read(userRepository, config, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
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
	public void process(final Element element, final ElementWriter writer) throws PacketErrorTypeException {
		try {
			boolean handled = runModules(element, writer);

			if (!handled) {
				final String t = element.getAttribute("type");
				final StanzaType type = (t == null) ? null : StanzaType.valueof(t);

				if (type != StanzaType.error) {
					throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED);
				} else {
					log.finer(element.getName() + " stanza with type='error' ignored");
				}
			}
		} catch (PubSubException e) {
			log.log(Level.INFO, "Exception thrown for " + element.toString(), e);

			Element result = e.makeElement(element);

			log.log(Level.INFO, "Sending back: " + result.toString());
			writer.write(result);
		}
	}

	// ~--- set methods
	// ----------------------------------------------------------

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

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 */
	@Override
	public void processPacket(final Packet packet) {
		try {
			process(packet.getElement(), this.elementWriter);
		} catch (Exception e) {
			log.log(Level.WARNING, "Unexpected exception: internal-server-error", e);
			e.printStackTrace();

			try {
				addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, e.getMessage(), true));
			} catch (PacketErrorTypeException e1) {
				e1.printStackTrace();
				log.throwing("PubSub Service", "processPacket (sending internal-server-error)", e);
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

	protected boolean runModules(final Element element, final ElementWriter writer) throws PubSubException {
		boolean handled = false;

		if (log.isLoggable(Level.FINER)) {
			log.finest("Processing packet: " + element.toString());
		}

		for (Module module : this.modules) {
			Criteria criteria = module.getModuleCriteria();

			if ((criteria != null) && criteria.match(element)) {
				handled = true;

				if (log.isLoggable(Level.FINER)) {
					log.finest("Handled by module " + module.getClass());
				}

				List<Element> result = module.process(element, writer);

				if (result != null) {
					for (Element element2 : result) {
						writer.write(element2);
					}

					return true;
				}
			}
		}

		return handled;
	}

	;

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
			// and this component does not support single property change for the rest
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

			initialize((String[]) props.get(ADMINS_KEY), dao, null, new LeafNodeConfig("default"));
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
}

// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com
