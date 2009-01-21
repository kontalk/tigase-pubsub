/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub;

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
import tigase.pubsub.modules.commands.RebuildDatabaseCommand;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.server.AbstractMessageReceiver;
import tigase.server.DisableDisco;
import tigase.server.Packet;
import tigase.stats.StatRecord;
import tigase.util.DNSResolver;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;

public class PubSubComponent extends AbstractMessageReceiver implements XMPPService, Configurable, DisableDisco, DefaultNodeConfigListener {

	public static final String ADMINS_KEY = "admin";

	public static final String DEFAULT_LEAF_NODE_CONFIG_KEY = "default-node-config";

	private static final String MAX_CACHE_SIZE = "pubsub-repository-cache-size";

	protected static final String PUBSUB_REPO_CLASS_PROP_KEY = "pubsub-repo-class";

	protected static final String PUBSUB_REPO_URL_PROP_KEY = "pubsub-repo-url";

	protected AdHocConfigCommandModule adHocCommandsModule;

	protected final PubSubConfig config = new PubSubConfig();

	protected DefaultConfigModule defaultConfigModule;

	protected LeafNodeConfig defaultNodeConfig;

	protected PubSubDAO directPubSubRepository;

	protected final ElementWriter elementWriter;

	public String[] HOSTNAMES_PROP_VAL = { "localhost", "hostname" };

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

	public PubSubComponent() {
		setName("pubsub");
		this.elementWriter = new ElementWriter() {

			public void write(Collection<Element> elements) {
				if (elements != null)
					for (Element element : elements) {
						if (element != null)
							write(element);
					}
			}

			public void write(final Element element) {
				if (element != null)
					addOutPacket(new Packet(element));
			}
		};
	}

	protected CachedPubSubRepository createPubSubRepository(PubSubDAO directRepository) {
		// return new StatelessPubSubRepository(directRepository, this.config);
		return new CachedPubSubRepository(directRepository, maxRepositoryCacheSize);
	}

	@Override
	public void everySecond() {
		super.everySecond();
		if (this.pubsubRepository != null)
			this.pubsubRepository.doLazyWrite();
	}

	protected String extractNodeName(Element element) {
		if (element == null)
			return null;
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
		} else if (query != null) {
			String n = query.getAttribute("node");
			return n;
		}
		return null;
	}

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

	@Override
	public List<Element> getDiscoFeatures() {

		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Element getDiscoInfo(String node, String jid) {
		return null;
	}

	@Override
	public List<Element> getDiscoItems(String node, String jid) {
		if (node == null) {
			Element result = serviceEntity.getDiscoItem(null, getName() + "." + jid);
			return Arrays.asList(result);
		} else {
			return null;
		}
	}

	@Override
	public List<StatRecord> getStatistics() {
		List<StatRecord> stats = super.getStatistics();

		this.pubsubRepository.addStats(getName(), stats);

		return stats;
	}

	protected void init() {
		this.xslTransformer = new XsltTool();

		this.presenceCollectorModule = registerModule(new PresenceCollectorModule());
		this.publishNodeModule = registerModule(new PublishItemModule(this.config, this.pubsubRepository, this.xslTransformer,
				this.presenceCollectorModule));
		this.retractItemModule = registerModule(new RetractItemModule(this.config, this.pubsubRepository, this.publishNodeModule));
		this.pendingSubscriptionModule = registerModule(new PendingSubscriptionModule(this.config, this.pubsubRepository));
		this.manageSubscriptionModule = registerModule(new ManageSubscriptionModule(this.config, this.pubsubRepository));
		this.subscribeNodeModule = registerModule(new SubscribeNodeModule(this.config, this.pubsubRepository,
				this.pendingSubscriptionModule));
		this.nodeCreateModule = registerModule(new NodeCreateModule(this.config, this.pubsubRepository, this.defaultNodeConfig,
				this.publishNodeModule));
		this.nodeDeleteModule = registerModule(new NodeDeleteModule(this.config, this.pubsubRepository, this.publishNodeModule));
		this.defaultConfigModule = registerModule(new DefaultConfigModule(this.config, this.pubsubRepository, this.defaultNodeConfig));
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

	public void initialize(String[] admins, PubSubDAO pubSubDAO, IPubSubRepository createPubSubRepository, LeafNodeConfig defaultNodeConfig)
			throws UserNotFoundException, TigaseDBException, RepositoryException {
		serviceEntity = new ServiceEntity(getName(), null, "Publish-Subscribe");
		serviceEntity.addIdentities(new ServiceIdentity("pubsub", "service", "Publish-Subscribe"));
		serviceEntity.addFeatures("http://jabber.org/protocol/pubsub");
		this.config.setAdmins(admins);
		this.config.setServiceName("tigase-pubsub");

		// XXX remove ASAP
		if (pubSubDAO != null)
			pubSubDAO.init();

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
		this.adHocCommandsModule.register(new DeleteAllNodesCommand(this.config, this.directPubSubRepository, this.userRepository));

	};

	public String myDomain() {
		return getName() + "." + getDefHostName();
	}

	@Override
	public void onChangeDefaultNodeConfig() {
		try {
			this.defaultNodeConfig.read(userRepository, config, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
			log.info("Node " + getComponentId() + " read default node configuration.");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Reading default config error", e);
		}
	}

	public void process(final Element element, final ElementWriter writer) throws PacketErrorTypeException {
		try {
			boolean handled = runModules(element, writer);

			if (!handled) {
				final String t = element.getAttribute("type");
				final StanzaType type = t == null ? null : StanzaType.valueof(t);
				if (type != StanzaType.error) {
					throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED);
				} else {
					log.finer(element.getName() + " stanza with type='error' ignored");
				}
			}
		} catch (PubSubException e) {
			log.finest("Exception '" + e.getErrorCondition() + "' throwed for request id=" + element.getAttribute("id"));
			writer.write(e.makeElement(element));
		}
	}

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

	public <T extends Module> T registerModule(final T module) {
		log.config("Register PubSub plugin: " + module.getClass().getCanonicalName());
		this.modules.add(module);
		return module;
	}

	protected boolean runModules(final Element element, final ElementWriter writer) throws PubSubException {
		boolean handled = false;
		log.finest("Processing packet: " + element.toString());

		for (Module module : this.modules) {
			Criteria criteria = module.getModuleCriteria();
			if (criteria != null && criteria.match(element)) {
				handled = true;
				log.finest("Handled by module " + module.getClass());
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

	@Override
	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);

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

		try {
			String cls_name = (String) props.get(PUBSUB_REPO_CLASS_PROP_KEY);
			String res_uri = (String) props.get(PUBSUB_REPO_URL_PROP_KEY);
			// if (!res_uri.contains("autoCreateUser=true")) {
			// res_uri += "&autoCreateUser=true";
			// }

			this.userRepository = RepositoryFactory.getUserRepository("pubsub", cls_name, res_uri, null);
			userRepository.initRepository(res_uri, null);

			PubSubDAO dao = new PubSubDAO(userRepository, this.config);

			initialize((String[]) props.get(ADMINS_KEY), dao, createPubSubRepository(dao), new LeafNodeConfig("default"));

			log.config("Initialized " + cls_name + " as pubsub repository: " + res_uri);
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
