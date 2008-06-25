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
package tigase.pubsub.modules;

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.Affiliation;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.PubSubRepository;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class NodeConfigModule extends AbstractConfigCreateNode {

	public NodeConfigModule(PubSubConfig config, PubSubRepository pubsubRepository, LeafNodeConfig defaultNodeConfig) {
		super(config, pubsubRepository, defaultNodeConfig);
	}

	private static final Criteria CRIT_CONFIG = ElementCriteria.name("iq").add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("configure"));

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_CONFIG;
	}

	protected void parseConf(final IntConf conf, final String nodeName, final Element configure) throws PubSubException {
		Element x = configure.getChild("x", "jabber:x:data");
		if (x != null && "submit".equals(x.getAttribute("type"))) {
			for (Element field : x.getChildren()) {
				if ("field".equals(field.getName())) {
					final String var = field.getAttribute("var");
					String val = null;
					Element value = field.getChild("value");
					if (value != null) {
						val = value.getCData();
					}
					if ("pubsub#node_type".equals(var)) {
						conf.nodeType = NodeType.valueOf(val);
					} else if ("pubsub#children".equals(var)) {
						List<Element> values = field.getChildren();
						if (values != null) {
							conf.children = new String[values.size()];
							for (int i = 0; i < values.size(); i++) {
								conf.children[i] = values.get(i).getCData();
							}
						}
					} else if ("pubsub#collection".equals(var)) {
						List<Element> values = field.getChildren();
						if (values == null || values.size() != 1) {
							throw new PubSubException(Authorization.BAD_REQUEST);
						}
						conf.collection = val == null ? "" : val;
					} else
						conf.nodeConfig.setValue(var, val);
				}
			}
		}
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		try {
			final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
			final Element configure = pubSub.getChild("configure");
			final String nodeName = configure.getAttribute("node");
			final String type = element.getAttribute("type");

			if (nodeName == null) {
				throw new PubSubException(element, Authorization.BAD_REQUEST, PubSubErrorCondition.NODEID_REQUIRED);
			}

			NodeType nodeType = repository.getNodeType(nodeName);
			if (nodeType == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}

			String jid = element.getAttribute("from");
			Affiliation senderAffiliation = NodeDeleteModule.getUserAffiliation(this.repository, nodeName, jid);
			if (senderAffiliation != Affiliation.owner) {
				throw new PubSubException(element, Authorization.FORBIDDEN);
			}
			// TODO 8.2.3.4 No Configuration Options

			Element result = createResultIQ(element);
			List<Element> resultArray = makeArray(result);
			if ("get".equals(type)) {
				LeafNodeConfig nodeConfig = repository.getNodeConfig(nodeName);
				Element rPubSub = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub#owner" });
				Element rConfigure = new Element("configure", new String[] { "node" }, new String[] { nodeName });
				rConfigure.addChild(nodeConfig.getJabberForm());
				rPubSub.addChild(rConfigure);
				result.addChild(rPubSub);
			} else if ("set".equals(type)) {
				LeafNodeConfig nodeConfig = repository.getNodeConfig(nodeName);
				String collectionCurrent = repository.getCollectionOf(nodeName);
				IntConf conf = new IntConf();
				conf.nodeConfig = nodeConfig;

				parseConf(conf, nodeName, configure);

				if (conf.collection != null && !conf.collection.equals(collectionCurrent)) {
					NodeType colNodeType = "".equals(conf.collection) ? NodeType.collection
							: repository.getNodeType(conf.collection);
					if (colNodeType == null) {
						throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
					} else if (colNodeType == NodeType.leaf) {
						throw new PubSubException(element, Authorization.NOT_ALLOWED);
					}
					repository.setNewNodeCollection(nodeName, conf.collection);

				}

				if (nodeType == NodeType.collection && conf.children != null) {
					// XXX sprawdzić usuwanie itp.
					String[] nodes = repository.getNodesList();
					if (isIn("", conf.children)) {
						throw new PubSubException(Authorization.BAD_REQUEST);
					}
					for (String node : nodes) {
						if (isIn(node, conf.children)) {
							NodeType colNodeType = repository.getNodeType(node);
							if (colNodeType == null) {
								throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
							} else if (colNodeType == NodeType.leaf) {
								throw new PubSubException(element, Authorization.NOT_ALLOWED);
							}
							repository.setNewNodeCollection(node, nodeName);
						}
					}
				}

				repository.update(nodeName, nodeConfig);

				if (nodeConfig.isNotify_config()) {
					String pssJid = element.getAttribute("to");
					String[] jids = repository.getSubscribersJid(nodeName);
					for (String sjid : jids) {
						Affiliation affiliation = NodeDeleteModule.getUserAffiliation(this.repository, nodeName, sjid);
						if (affiliation == null || affiliation == Affiliation.none || affiliation == Affiliation.outcast)
							continue;
						Element message = new Element("message", new String[] { "from", "to" }, new String[] { pssJid, sjid });
						Element event = new Element("event", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub#event" });
						Element configuration = new Element("configuration", new String[] { "node" }, new String[] { nodeName });
						if (nodeConfig.isDeliver_payloads()) {
							configuration.addChild(nodeConfig.getJabberForm());
						}
						event.addChild(configuration);
						message.addChild(event);

						resultArray.add(message);
					}
				}

			} else {
				throw new PubSubException(element, Authorization.BAD_REQUEST);
			}

			return resultArray;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	protected boolean isIn(String node, String[] children) {
		for (String x : children) {
			if (x.equals(node))
				return true;
		}
		return false;
	}
}
