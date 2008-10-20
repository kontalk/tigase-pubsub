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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.inmemory.NodeAffiliation;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class NodeConfigModule extends AbstractConfigCreateNode {

	private static final Criteria CRIT_CONFIG = ElementCriteria.name("iq").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("configure"));

	protected static String[] diff(String[] a, String[] b) {
		HashSet<String> r = new HashSet<String>();
		for (String $a : a) {
			r.add($a);
		}
		for (String $a : b) {
			r.add($a);
		}
		for (String $a : b) {
			r.remove($a);
		}
		return r.toArray(new String[] {});
	}

	public static void parseConf(final AbstractNodeConfig conf, final Element configure) throws PubSubException {
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
					conf.setValue(var, val);
				}
			}
		}
	}

	private final ArrayList<NodeConfigListener> nodeConfigListeners = new ArrayList<NodeConfigListener>();

	private final PublishItemModule publishModule;

	public NodeConfigModule(PubSubConfig config, IPubSubRepository pubsubRepository, LeafNodeConfig defaultNodeConfig,
			PublishItemModule publishItemModule) {
		super(config, pubsubRepository, defaultNodeConfig);
		this.publishModule = publishItemModule;
	}

	public void addNodeConfigListener(NodeConfigListener listener) {
		this.nodeConfigListeners.add(listener);
	}

	protected void fireOnNodeConfigChange(final String nodeName) {
		for (NodeConfigListener listener : this.nodeConfigListeners) {
			listener.onNodeConfigChanged(nodeName);
		}
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#config-node" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_CONFIG;
	}

	protected boolean isIn(String node, String[] children) {
		if (node == null | children == null)
			return false;
		for (String x : children) {
			if (x.equals(node))
				return true;
		}
		return false;
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

			final AbstractNodeConfig nodeConfig = repository.getNodeConfig(nodeName);
			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}

			String jid = element.getAttribute("from");
			if (!this.config.isAdmin(JIDUtils.getNodeID(jid))) {
				NodeAffiliation senderAffiliation = this.repository.getSubscriberAffiliation(nodeName, jid);
				if (senderAffiliation.getAffiliation() != Affiliation.owner) {
					throw new PubSubException(element, Authorization.FORBIDDEN);
				}
			}
			// TODO 8.2.3.4 No Configuration Options

			Element result = createResultIQ(element);
			List<Element> resultArray = makeArray(result);

			if ("get".equals(type)) {
				Element rPubSub = new Element("pubsub", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/pubsub#owner" });
				Element rConfigure = new Element("configure", new String[] { "node" }, new String[] { nodeName });
				rConfigure.addChild(nodeConfig.getFormElement());
				rPubSub.addChild(rConfigure);

				result.addChild(rPubSub);
			} else if ("set".equals(type)) {
				String[] children = nodeConfig.getChildren() == null ? new String[] {} : Arrays.copyOf(nodeConfig.getChildren(),
						nodeConfig.getChildren().length);
				final String collectionCurrent = nodeConfig.getCollection();

				parseConf(nodeConfig, configure);

				// && (nodeConfig.getCollection()==null ^
				// collectionCurrent==null) &&
				// !nodeConfig.getCollection().equals(collectionCurrent)

				if (nodeConfig.getNodeType() != NodeType.leaf && nodeConfig.getNodeType() != NodeType.collection)
					throw new PubSubException(Authorization.NOT_ALLOWED);

				try {
					if (nodeConfig.isCollectionSet()) {
						String collectionName = nodeConfig.getCollection() == null ? "" : nodeConfig.getCollection();
						AbstractNodeConfig colNodeConfig = this.repository.getNodeConfig(collectionName);
						NodeType colNodeType = "".equals(collectionName) ? NodeType.collection : (colNodeConfig == null ? null
								: colNodeConfig.getNodeType());
						if (colNodeType == null) {
							throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
						} else if (colNodeType == NodeType.leaf) {
							throw new PubSubException(element, Authorization.NOT_ALLOWED);
						}
						switchCollection(nodeName, collectionName);
						if (!"".equals(collectionName)) {
							Element colE = new Element("collection", new String[] { "node" }, new String[] { collectionName });
							colE.addChild(new Element("associate", new String[] { "node" }, new String[] { nodeName }));
							resultArray.addAll(publishModule.prepareNotification(colE, element.getAttribute("to"), collectionName));
						}
						if (collectionCurrent != null && !"".equals(collectionCurrent)) {
							Element colE = new Element("collection", new String[] { "node" }, new String[] { collectionCurrent });
							colE.addChild(new Element("disassociate", new String[] { "node" }, new String[] { nodeName }));
							resultArray.addAll(publishModule.prepareNotification(colE, element.getAttribute("to"),
									collectionCurrent));
						}
					}

					if (nodeConfig.getNodeType() == NodeType.collection) {
						if (isIn("", nodeConfig.getChildren())) {
							throw new PubSubException(Authorization.BAD_REQUEST);
						}
						String[] removedChildNodes = diff(children == null ? new String[] {} : children,
								nodeConfig.getChildren() == null ? new String[] {} : nodeConfig.getChildren());
						String[] addedChildNodes = diff(nodeConfig.getChildren() == null ? new String[] {}
								: nodeConfig.getChildren(), children == null ? new String[] {} : children);

						for (String node : addedChildNodes) {
							AbstractNodeConfig nc = repository.getNodeConfig(node);
							if (nc == null) {
								throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
							}
							switchCollection(node, nodeName);

							Element colE = new Element("collection", new String[] { "node" }, new String[] { nodeName });
							colE.addChild(new Element("associate", new String[] { "node" }, new String[] { node }));
							resultArray.addAll(publishModule.prepareNotification(colE, element.getAttribute("to"), nodeName));
						}
						if (removedChildNodes != null && removedChildNodes.length > 0) {
							for (String node : removedChildNodes) {
								AbstractNodeConfig nc = repository.getNodeConfig(node);
								if (nc == null) {
									throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
								}
								switchCollection(node, "");
								Element colE = new Element("collection", new String[] { "node" }, new String[] { nodeName });
								colE.addChild(new Element("disassociate", new String[] { "node" }, new String[] { node }));
								resultArray.addAll(publishModule.prepareNotification(colE, element.getAttribute("to"), nodeName));
							}
						}
					}

					repository.update(nodeName, nodeConfig);

					if (nodeConfig.isNotify_config()) {
						String pssJid = element.getAttribute("to");
						Element configuration = new Element("configuration", new String[] { "node" }, new String[] { nodeName });
						resultArray.addAll(this.publishModule.prepareNotification(configuration, pssJid, nodeName));
					}
				} finally {
					fireOnNodeConfigChange(nodeName);
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

	public void removeNodeConfigListener(NodeConfigListener listener) {
		this.nodeConfigListeners.remove(listener);
	}

	private void switchCollection(String nodeName, String collectionNodeName) throws RepositoryException {
		repository.setNewNodeCollection(nodeName, collectionNodeName);
	}
}
