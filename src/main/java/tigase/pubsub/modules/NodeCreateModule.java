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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

/**
 * Case 8.1.2
 * 
 * @author bmalkow
 * 
 */
public class NodeCreateModule extends AbstractConfigCreateNode {

	private static final Criteria CRIT_CREATE = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("create"));

	private final ArrayList<NodeConfigListener> nodeConfigListeners = new ArrayList<NodeConfigListener>();

	private final PublishItemModule publishModule;

	public NodeCreateModule(PubSubConfig config, IPubSubRepository pubsubRepository, LeafNodeConfig defaultNodeConfig,
			PublishItemModule publishItemModule) {
		super(config, pubsubRepository, defaultNodeConfig);
		this.publishModule = publishItemModule;
	}

	public void addNodeConfigListener(NodeConfigListener listener) {
		this.nodeConfigListeners.add(listener);
	}

	protected void fireOnNodeCreatedConfigChange(final String nodeName) {
		for (NodeConfigListener listener : this.nodeConfigListeners) {
			listener.onNodeCreated(nodeName);
		}
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#create-and-configure",
				"http://jabber.org/protocol/pubsub#collections", "http://jabber.org/protocol/pubsub#create-nodes",
				"http://jabber.org/protocol/pubsub#instant-nodes", "http://jabber.org/protocol/pubsub#multi-collection",
				"http://jabber.org/protocol/pubsub#access-authorize", "http://jabber.org/protocol/pubsub#access-open",
				"http://jabber.org/protocol/pubsub#access-presence", "http://jabber.org/protocol/pubsub#access-roster",
				"http://jabber.org/protocol/pubsub#access-whitelist",

		};
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_CREATE;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		StringBuilder logsb = new StringBuilder();

		logsb.append("CHECKPOINT 1; entering; " + System.currentTimeMillis() + ";\n");
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element create = pubSub.getChild("create");
		final Element configure = pubSub.getChild("configure");

		String nodeName = create.getAttribute("node");
		logsb.append("CHECKPOINT 2; getting node name '" + nodeName + "'; " + System.currentTimeMillis() + ";\n");
		try {
			boolean instantNode = nodeName == null;
			if (instantNode) {
				nodeName = UUID.randomUUID().toString().replaceAll("-", "");
			}

			if (repository.getNodeConfig(nodeName) != null) {
				throw new PubSubException(element, Authorization.CONFLICT);
			}

			logsb.append("CHECKPOINT 3; potential conflict checked'; " + System.currentTimeMillis() + ";\n");

			NodeType nodeType = NodeType.leaf;
			String collection = null;
			LeafNodeConfig nodeConfig = new LeafNodeConfig(nodeName, defaultNodeConfig);
			if (configure != null) {
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
								nodeType = val == null ? NodeType.leaf : NodeType.valueOf(val);
							} else if ("pubsub#collection".equals(var)) {
								collection = val;
							}
							nodeConfig.setValue(var, val);
						}
					}
				}
			}

			logsb.append("CHECKPOINT 4; configuration processed'; " + System.currentTimeMillis() + ";\n");

			CollectionNodeConfig colNodeConfig = null;
			if (collection != null) {
				AbstractNodeConfig absNodeConfig = repository.getNodeConfig(collection);
				if (absNodeConfig == null) {
					throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
				} else if (absNodeConfig.getNodeType() == NodeType.leaf) {
					throw new PubSubException(element, Authorization.NOT_ALLOWED);
				}
				colNodeConfig = (CollectionNodeConfig) absNodeConfig;
			}

			logsb.append("CHECKPOINT 5; collections checked'; " + System.currentTimeMillis() + ";\n");

			if (nodeType != NodeType.leaf && nodeType != NodeType.collection)
				throw new PubSubException(Authorization.NOT_ALLOWED);

			logsb.append("CHECKPOINT 6; ready to create'; " + System.currentTimeMillis() + ";\n");

			repository.createNode(nodeName, JIDUtils.getNodeID(element.getAttribute("from")), nodeConfig, nodeType,
					collection == null ? "" : collection);

			logsb.append("CHECKPOINT 7; node created'; " + System.currentTimeMillis() + ";\n");

			ISubscriptions nodeSubscriptions = repository.getNodeSubscriptions(nodeName);
			IAffiliations nodeaAffiliations = repository.getNodeAffiliations(nodeName);

			logsb.append("CHECKPOINT 8; ready to add subscriptions and affilitaions'; " + System.currentTimeMillis() + ";\n");

			nodeSubscriptions.addSubscriberJid(element.getAttribute("from"), Subscription.subscribed);
			nodeaAffiliations.addAffiliation(element.getAttribute("from"), Affiliation.owner);

			logsb.append("CHECKPOINT 9; subscriptions and affilitaions added'; " + System.currentTimeMillis() + ";\n");

			repository.update(nodeName, nodeaAffiliations);
			logsb.append("CHECKPOINT 10; affiliations updated'; " + System.currentTimeMillis() + ";\n");
			repository.update(nodeName, nodeSubscriptions);
			logsb.append("CHECKPOINT 11; subscriptions updated'; " + System.currentTimeMillis() + ";\n");

			if (colNodeConfig == null) {
				repository.addToRootCollection(nodeName);
				logsb.append("CHECKPOINT 12a; root collection updated'; " + System.currentTimeMillis() + ";\n");
			} else {
				colNodeConfig.addChildren(nodeName);
				repository.update(collection, colNodeConfig);
				logsb.append("CHECKPOINT 12b; collection updated'; " + System.currentTimeMillis() + ";\n");
			}

			fireOnNodeCreatedConfigChange(nodeName);

			ArrayList<Element> notifications = new ArrayList<Element>();
			Element result = createResultIQ(element);
			notifications.add(result);

			if (collection != null) {
				ISubscriptions colNodeSubscriptions = this.repository.getNodeSubscriptions(collection);
				IAffiliations colNodeAffiliations = this.repository.getNodeAffiliations(collection);

				Element colE = new Element("collection", new String[] { "node" }, new String[] { collection });
				colE.addChild(new Element("associate", new String[] { "node" }, new String[] { nodeName }));
				notifications.addAll(publishModule.prepareNotification(colE, element.getAttribute("to"), collection, nodeConfig,
						colNodeAffiliations, colNodeSubscriptions));

			}

			logsb.append("CHECKPOINT 13; notification prepared'; " + System.currentTimeMillis() + ";\n");

			if (instantNode) {
				Element ps = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub" });
				Element cr = new Element("create", new String[] { "node" }, new String[] { nodeName });
				ps.addChild(cr);
				result.addChild(ps);
			}
			logsb.append("CHECKPOINT 14; sending results (" + notifications.size() + "); " + System.currentTimeMillis() + ";\n");

			result.addChild(new Element("tigaselog", logsb.toString()));

			return notifications;
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
}
