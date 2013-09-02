/*
 * NodeCreateModule.java
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

package tigase.pubsub.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PacketWriter;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.server.Packet;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;

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

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 * @param defaultNodeConfig
	 * @param publishItemModule
	 */
	public NodeCreateModule(PubSubConfig config, IPubSubRepository pubsubRepository, LeafNodeConfig defaultNodeConfig,
			PublishItemModule publishItemModule) {
		super(config, pubsubRepository, defaultNodeConfig);
		this.publishModule = publishItemModule;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param listener
	 */
	public void addNodeConfigListener(NodeConfigListener listener) {
		this.nodeConfigListeners.add(listener);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 */
	protected void fireOnNodeCreatedConfigChange(final String nodeName) {
		for (NodeConfigListener listener : this.nodeConfigListeners) {
			listener.onNodeCreated(nodeName);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#create-and-configure",
				"http://jabber.org/protocol/pubsub#collections", "http://jabber.org/protocol/pubsub#create-nodes",
				"http://jabber.org/protocol/pubsub#instant-nodes", "http://jabber.org/protocol/pubsub#multi-collection",
				"http://jabber.org/protocol/pubsub#access-authorize", "http://jabber.org/protocol/pubsub#access-open",
				"http://jabber.org/protocol/pubsub#access-presence", "http://jabber.org/protocol/pubsub#access-roster",
				"http://jabber.org/protocol/pubsub#access-whitelist", };
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT_CREATE;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 * @param packetWriter
	 * 
	 * @return
	 * 
	 * @throws PubSubException
	 */
	@Override
	public List<Packet> process(Packet packet, PacketWriter packetWriter) throws PubSubException {
		final long time1 = System.currentTimeMillis();
		final BareJID toJid = packet.getStanzaTo().getBareJID();
		final Element element = packet.getElement();
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element create = pubSub.getChild("create");
		final Element configure = pubSub.getChild("configure");
		String nodeName = create.getAttributeStaticStr("node");

		try {
			boolean instantNode = nodeName == null;

			if (instantNode) {
				nodeName = UUID.randomUUID().toString().replaceAll("-", "");
			}
			if (repository.getNodeConfig(toJid, nodeName) != null) {
				throw new PubSubException(element, Authorization.CONFLICT);
			}

			NodeType nodeType = NodeType.leaf;
			String collection = null;
			AbstractNodeConfig nodeConfig = new LeafNodeConfig(nodeName, defaultNodeConfig);

			if (configure != null) {
				Element x = configure.getChild("x", "jabber:x:data");

				if ((x != null) && "submit".equals(x.getAttributeStaticStr("type"))) {
					for (Element field : x.getChildren()) {
						if ("field".equals(field.getName())) {
							final String var = field.getAttributeStaticStr("var");
							String val = null;
							Element value = field.getChild("value");

							if (value != null) {
								val = value.getCData();
							}
							if ("pubsub#node_type".equals(var)) {
								nodeType = (val == null) ? NodeType.leaf : NodeType.valueOf(val);
							} else if ("pubsub#collection".equals(var)) {
								collection = val;
							}
							nodeConfig.setValue(var, val);
						}
					}
				}
			}
			if (nodeType == NodeType.collection) {
				Form f = nodeConfig.getForm();

				nodeConfig = new CollectionNodeConfig(nodeConfig.getNodeName());
				nodeConfig.copyFromForm(f);
				nodeConfig.setNodeType(NodeType.collection);
			}

			CollectionNodeConfig colNodeConfig = null;

			if (collection != null) {
				AbstractNodeConfig absNodeConfig = repository.getNodeConfig(toJid, collection);

				if (absNodeConfig == null) {
					throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
				} else if (absNodeConfig.getNodeType() == NodeType.leaf) {
					throw new PubSubException(element, Authorization.NOT_ALLOWED);
				}
				colNodeConfig = (CollectionNodeConfig) absNodeConfig;
			}
			if ((nodeType != NodeType.leaf) && (nodeType != NodeType.collection)) {
				throw new PubSubException(Authorization.NOT_ALLOWED);
			}
			repository.createNode(toJid, nodeName, JIDUtils.getNodeID(element.getAttributeStaticStr("from")), nodeConfig,
					nodeType, (collection == null) ? "" : collection);

			ISubscriptions nodeSubscriptions = repository.getNodeSubscriptions(toJid, nodeName);
			IAffiliations nodeaAffiliations = repository.getNodeAffiliations(toJid, nodeName);

			nodeSubscriptions.addSubscriberJid(element.getAttributeStaticStr("from"), Subscription.subscribed);
			nodeaAffiliations.addAffiliation(element.getAttributeStaticStr("from"), Affiliation.owner);
			repository.update(toJid, nodeName, nodeaAffiliations);
			repository.update(toJid, nodeName, nodeSubscriptions);
			if (colNodeConfig == null) {
				repository.addToRootCollection(toJid, nodeName);
			} else {
				colNodeConfig.addChildren(nodeName);
				repository.update(toJid, collection, colNodeConfig);
			}
			fireOnNodeCreatedConfigChange(nodeName);

			Packet result = packet.okResult((Element) null, 0);

			if (collection != null) {
				ISubscriptions colNodeSubscriptions = this.repository.getNodeSubscriptions(toJid, collection);
				IAffiliations colNodeAffiliations = this.repository.getNodeAffiliations(toJid, collection);
				Element colE = new Element("collection", new String[] { "node" }, new String[] { collection });

				colE.addChild(new Element("associate", new String[] { "node" }, new String[] { nodeName }));
				packetWriter.write(publishModule.prepareNotification(colE, packet.getStanzaTo(), collection, nodeConfig,
						colNodeAffiliations, colNodeSubscriptions));
			}
			if (instantNode) {
				Element ps = new Element("pubsub", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/pubsub" });
				Element cr = new Element("create", new String[] { "node" }, new String[] { nodeName });

				ps.addChild(cr);
				result.getElement().addChild(ps);
			}

			final long time2 = System.currentTimeMillis();

			result.getElement().addChild(new Element("text", "Created in " + (time2 - time1) + " ms"));
			packetWriter.write(result);

			return null;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param listener
	 */
	public void removeNodeConfigListener(NodeConfigListener listener) {
		this.nodeConfigListeners.remove(listener);
	}
}
