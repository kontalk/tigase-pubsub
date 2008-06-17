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
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.conf.Configurable;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.disco.ServiceEntity;
import tigase.disco.ServiceIdentity;
import tigase.disco.XMPPService;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.NodeCreateModule;
import tigase.pubsub.modules.PEPPublisherModule;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.modules.SubscribeNodeModule;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.util.DNSResolver;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.PacketErrorTypeException;

public class PubSubService extends AbstractMessageReceiver implements XMPPService, Configurable {

	private static final String PUBSUB_REPO_CLASS_PROP_KEY = "pubsub-repo-class";

	private static final String PUBSUB_REPO_URL_PROP_KEY = "pubsub-repo-url";

	protected final PubSubConfig config = new PubSubConfig();

	private Logger log = Logger.getLogger(this.getClass().getName());

	private final ArrayList<Module> modules = new ArrayList<Module>();

	protected NodeCreateModule nodeCreateModule;

	private PEPPublisherModule pepPublishModule;

	private PublishItemModule publishNodeModule;;

	private UserRepository pubsubRepository;

	private ServiceEntity serviceEntity;

	protected SubscribeNodeModule subscribeNodeModule;

	public PubSubService() {
	}

	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = super.getDefaults(params);
		String[] hostnamesPropVal = null;
		if (params.get("--virt-hosts") != null) {
			hostnamesPropVal = ((String) params.get("--virt-hosts")).split(",");
		} else {
			hostnamesPropVal = DNSResolver.getDefHostNames();
		}
		for (int i = 0; i < hostnamesPropVal.length; i++) {
			if (((String) params.get("config-type")).equals(GEN_CONFIG_COMP)) {
				// This is specialized configuration for a single
				// external component and on specialized component like MUC
				hostnamesPropVal[i] = hostnamesPropVal[i];
			} else {
				hostnamesPropVal[i] = "muc." + hostnamesPropVal[i];
			}
		}

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

		props.put(HOSTNAMES_PROP_KEY, hostnamesPropVal);
		return props;
	}

	@Override
	public List<Element> getDiscoFeatures() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Element getDiscoInfo(String node, String jid) {
		if (jid != null && jid.startsWith(getName() + ".")) {
			return serviceEntity.getDiscoInfo(node);
		}
		return null;
	}

	@Override
	public List<Element> getDiscoItems(String node, String jid) {
		if (jid.startsWith(getName() + ".")) {
			return serviceEntity.getDiscoItems(node, null);
		} else {
			return Arrays.asList(serviceEntity.getDiscoItem(null, getName() + "." + jid));
		}
	}

	protected void init() {
		this.pepPublishModule = registerModule(new PEPPublisherModule(this.config, this.pubsubRepository));
		this.publishNodeModule = registerModule(new PublishItemModule(this.config, this.pubsubRepository));
		this.subscribeNodeModule = registerModule(new SubscribeNodeModule(this.config, this.pubsubRepository));
		this.nodeCreateModule = registerModule(new NodeCreateModule(this.config, this.pubsubRepository));
	}

	public String myDomain() {
		return getName() + "." + getDefHostName();
	}

	@Override
	public void processPacket(final Packet packet) {
		try {
			final Element element = packet.getElement();
			boolean handled = runModules(element);

			if (!handled) {
				addOutPacket(Authorization.FEATURE_NOT_IMPLEMENTED.getResponseMessage(packet, "Stanza is not processed", true));
			}
		} catch (PubSubException e) {
			Element result = e.makeElement();
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
	}

	@Override
	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);

		try {
			String cls_name = (String) props.get(PUBSUB_REPO_CLASS_PROP_KEY);
			String res_uri = (String) props.get(PUBSUB_REPO_URL_PROP_KEY);

			pubsubRepository = RepositoryFactory.getUserRepository("pubsub", cls_name, res_uri, null);
			pubsubRepository.initRepository(res_uri, null);

			log.config("Initialized " + cls_name + " as pubsub repository: " + res_uri);
		} catch (Exception e) {
			log.severe("Can't initialize pubsub repository: " + e);
			e.printStackTrace();
			System.exit(1);
		}
		String[] hostnames = (String[]) props.get(HOSTNAMES_PROP_KEY);
		clearRoutings();
		for (String host : hostnames) {
			addRouting(host);
		}

		serviceEntity = new ServiceEntity(getName(), null, "Publish-Subscribe");
		serviceEntity.addIdentities(new ServiceIdentity("pubsub", "service", "Publish-Subscribe"));

		serviceEntity.addFeatures("http://jabber.org/protocol/pubsub", "");

		this.config.setServiceName(myDomain());

		try {
			this.pubsubRepository.setData(this.config.getServiceName(), "last-start", String.valueOf(System.currentTimeMillis()));
		} catch (UserNotFoundException e) {
			try {
				this.pubsubRepository.addUser(this.config.getServiceName());
				this.pubsubRepository.setData(this.config.getServiceName(), "last-start", String.valueOf(System.currentTimeMillis()));
			} catch (Exception e1) {
				log.log(Level.SEVERE, "PubSub repository initialization problem", e1);
			}
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "PubSub repository initialization problem", e);
		}

		init();
	}
}
