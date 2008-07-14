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
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import tigase.conf.Configurable;
import tigase.db.RepositoryFactory;
import tigase.db.UserRepository;
import tigase.disco.ServiceEntity;
import tigase.disco.ServiceIdentity;
import tigase.disco.XMPPService;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.DefaultConfigModule;
import tigase.pubsub.modules.JabberVersionModule;
import tigase.pubsub.modules.ManageAffiliationsModule;
import tigase.pubsub.modules.ManageSubscriptionModule;
import tigase.pubsub.modules.NodeConfigModule;
import tigase.pubsub.modules.NodeCreateModule;
import tigase.pubsub.modules.NodeDeleteModule;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.modules.RetractItemModule;
import tigase.pubsub.modules.SubscribeNodeModule;
import tigase.pubsub.modules.UnsubscribeNodeModule;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.StatelessPubSubRepository;
import tigase.pubsub.repository.inmemory.InMemoryPubSubRepository;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.util.DNSResolver;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.PacketErrorTypeException;

public class PubSubComponent extends AbstractMessageReceiver implements XMPPService, Configurable {

	protected static final String PUBSUB_REPO_CLASS_PROP_KEY = "pubsub-repo-class";

	protected static final String PUBSUB_REPO_URL_PROP_KEY = "pubsub-repo-url";

	protected final PubSubConfig config = new PubSubConfig();

	protected DefaultConfigModule defaultConfigModule;

	protected LeafNodeConfig defaultNodeConfig;

	public String[] HOSTNAMES_PROP_VAL = { "localhost", "hostname" };

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private ManageAffiliationsModule manageAffiliationsModule;

	private ManageSubscriptionModule manageSubscriptionModule;

	private final ArrayList<Module> modules = new ArrayList<Module>();

	protected NodeConfigModule nodeConfigModule;

	protected NodeCreateModule nodeCreateModule;

	protected NodeDeleteModule nodeDeleteModule;

	protected PublishItemModule publishNodeModule;

	protected IPubSubRepository pubsubRepository;

	protected RetractItemModule retractItemModule;

	protected ServiceEntity serviceEntity;

	protected SubscribeNodeModule subscribeNodeModule;

	protected UnsubscribeNodeModule unsubscribeNodeModule;

	public PubSubComponent() {
		setName("pubsub");
	}

	protected IPubSubRepository createPubSubRepository(StatelessPubSubRepository directRepository) {
		return new InMemoryPubSubRepository(directRepository, this.config);
	}

	protected String extractNodeName(Element element) {
		if (element == null)
			return null;
		Element ps = element.getChild("pubsub");
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
		String repo_class = XML_REPO_CLASS_PROP_VAL;
		String repo_uri = XML_REPO_URL_PROP_VAL;
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

		props.put("admin", new String[] { "admin@localhost" });

		return props;
	}

	@Override
	public List<Element> getDiscoFeatures() {
		System.out.println("GET DISCO FEATORUES");

		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Element getDiscoInfo(String node, String jid) {
		System.out.println("GET DISCO INFO " + node);
		if (node != null && jid != null && jid.startsWith(getName() + ".")) {
			try {
				NodeType type = pubsubRepository.getNodeType(node);
				Element query = new Element("query", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/disco#info" });
				Element identyity = new Element("identity", new String[] { "category", "type" }, new String[] { "pubsub",
						type.name() });
				query.addChild(identyity);

				return query;
			} catch (RepositoryException e) {
				e.printStackTrace();
			}
		} else if (jid != null && jid.startsWith(getName() + ".")) {
			Element result = serviceEntity.getDiscoInfo(node);
			return result;
		}
		return null;
	}

	@Override
	public List<Element> getDiscoItems(String node, String jid) {
		log.finest("GET DISCO ITEMS");
		final String tmpNode = node == null ? "" : node;
		if (jid.startsWith(getName() + ".")) {
			try {
				List<Element> result = new ArrayList<Element>();
				String[] nodes = pubsubRepository.getNodesList();
				if (nodes != null)
					for (String nodeName : nodes) {
						final NodeType type = pubsubRepository.getNodeType(nodeName);
						if (type == null)
							continue;
						final String collection = pubsubRepository.getCollectionOf(nodeName);
						if (tmpNode.equals(collection)) {
							Element item = new Element("item", new String[] { "jid", "node", "name" }, new String[] { jid,
									nodeName, nodeName });
							result.add(item);
						}
					}
				return result;
			} catch (RepositoryException e) {
				throw new RuntimeException("Disco", e);
			}
		} else {
			Element result = serviceEntity.getDiscoItem(null, getName() + "." + jid);
			return Arrays.asList(result);
		}
	}

	protected void init() {
		this.publishNodeModule = registerModule(new PublishItemModule(this.config, this.pubsubRepository));
		this.retractItemModule = registerModule(new RetractItemModule(this.config, this.pubsubRepository, this.publishNodeModule));
		this.manageSubscriptionModule = registerModule(new ManageSubscriptionModule(this.config, this.pubsubRepository));
		this.subscribeNodeModule = registerModule(new SubscribeNodeModule(this.config, this.pubsubRepository,
				this.manageSubscriptionModule));
		this.nodeCreateModule = registerModule(new NodeCreateModule(this.config, this.pubsubRepository, this.defaultNodeConfig,
				this.publishNodeModule));
		this.nodeDeleteModule = registerModule(new NodeDeleteModule(this.config, this.pubsubRepository, this.publishNodeModule));
		this.defaultConfigModule = registerModule(new DefaultConfigModule(this.config, this.pubsubRepository,
				this.defaultNodeConfig));
		this.nodeConfigModule = registerModule(new NodeConfigModule(this.config, this.pubsubRepository, this.defaultNodeConfig,
				this.publishNodeModule));
		this.unsubscribeNodeModule = registerModule(new UnsubscribeNodeModule(this.config, this.pubsubRepository));
		this.manageAffiliationsModule = registerModule(new ManageAffiliationsModule(this.config, this.pubsubRepository));
		registerModule(new JabberVersionModule());
	}

	public String myDomain() {
		return getName() + "." + getDefHostName();
	}

	@Override
	public void processPacket(final Packet packet) {
		log.finest("Received by " + getComponentId() + ": " + packet.getElement().toString());
		try {
			final Element element = packet.getElement();
			boolean handled = runModules(element);

			if (!handled) {
				addOutPacket(Authorization.FEATURE_NOT_IMPLEMENTED.getResponseMessage(packet, "Stanza is not processed", true));
			}
		} catch (PubSubException e) {
			Element result = e.makeElement(packet.getElement());
			addOutPacket(new Packet(result));
		} catch (Exception e) {
			log.throwing("PubSUb Service", "processPacket", e);
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
		this.modules.add(module);
		return module;
	}

	protected boolean runModules(final Element element) throws PubSubException {
		boolean handled = false;
		log.finest("Processing packet: " + element.toString());

		for (Module module : this.modules) {
			if (module.getModuleCriteria().match(element)) {
				handled = true;
				log.finest("Handled by module " + module.getClass());
				List<Element> result = module.process(element);
				if (result != null) {
					for (Element e : result) {
						addOutPacket(new Packet(e));
					}
					return true;
				}
			}
		}
		return handled;
	};

	@Override
	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);

		String[] hostnames = (String[]) props.get(HOSTNAMES_PROP_KEY);
		if (hostnames == null || hostnames.length == 0) {
			log.warning("Hostnames definition is empty, setting 'localhost'");
			hostnames = new String[] { getName() + ".localhost" };
		}
		clearRoutings();
		for (String host : hostnames) {
			addRouting(host);
		}
		serviceEntity = new ServiceEntity(getName(), null, "Publish-Subscribe");
		serviceEntity.addIdentities(new ServiceIdentity("pubsub", "service", "Publish-Subscribe"));

		serviceEntity.addFeatures("http://jabber.org/protocol/pubsub");

		ServiceEntity blogItems = new ServiceEntity(getName(), "blogs", "Blogs items news");
		serviceEntity.addItems(blogItems);

		this.config.setAdmins((String[]) props.get("admin"));

		this.config.setServiceName("tigase-pubsub");

		try {
			String cls_name = (String) props.get(PUBSUB_REPO_CLASS_PROP_KEY);
			String res_uri = (String) props.get(PUBSUB_REPO_URL_PROP_KEY);

			UserRepository userRepository = RepositoryFactory.getUserRepository("pubsub", cls_name, res_uri, null);
			userRepository.initRepository(res_uri, null);

			StatelessPubSubRepository directPubSubRepository = new StatelessPubSubRepository(userRepository, this.config);

			this.pubsubRepository = createPubSubRepository(directPubSubRepository);
			this.defaultNodeConfig = new LeafNodeConfig();
			this.defaultNodeConfig.read(userRepository, config, "default-node-config");
			this.defaultNodeConfig.write(userRepository, config, "default-node-config");
			log.config("Initialized " + cls_name + " as pubsub repository: " + res_uri);
		} catch (Exception e) {
			log.severe("Can't initialize pubsub repository: " + e);
			e.printStackTrace();
			System.exit(1);
		}

		init();

		for (Module module : this.modules) {
			String[] features = module.getFeatures();
			if (features != null) {
				for (String f : features) {
					serviceEntity.addFeatures(f);
				}
			}
		}
	}

}
