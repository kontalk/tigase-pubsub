/*
 * NodeDeleteModule.java
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

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.PacketWriter;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;

/**
 * Class description
 * 
 * 
 * @version Enter version here..., 13/02/20
 * @author Enter your name here...
 */
public class NodeDeleteModule extends AbstractModule {
	private static final Criteria CRIT_DELETE = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("delete"));

	private final ArrayList<NodeConfigListener> nodeConfigListeners = new ArrayList<NodeConfigListener>();
	private final PublishItemModule publishModule;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 * @param publishItemModule
	 */
	public NodeDeleteModule(PubSubConfig config, IPubSubRepository pubsubRepository, PublishItemModule publishItemModule) {
		super(config, pubsubRepository);
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
	protected void fireOnNodeDeleted(final String nodeName) {
		for (NodeConfigListener listener : this.nodeConfigListeners) {
			listener.onNodeDeleted(nodeName);
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
		return new String[] { "http://jabber.org/protocol/pubsub#delete-nodes" };
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT_DELETE;
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
		final BareJID toJid = packet.getStanzaTo().getBareJID();
		final Element element = packet.getElement();
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
		final Element delete = pubSub.getChild("delete");
		final String nodeName = delete.getAttributeStaticStr("node");

		try {
			if (nodeName == null) {
				throw new PubSubException(element, Authorization.NOT_ALLOWED);
			}

			AbstractNodeConfig nodeConfig = repository.getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}

			final IAffiliations nodeAffiliations = this.repository.getNodeAffiliations(toJid, nodeName);
			String jid = element.getAttributeStaticStr("from");

			if (!this.config.isAdmin(JIDUtils.getNodeID(jid))) {
				UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(jid);

				if (!senderAffiliation.getAffiliation().isDeleteNode()) {
					throw new PubSubException(element, Authorization.FORBIDDEN);
				}
			}

			List<Packet> resultArray = makeArray(packet.okResult((Element) null, 0));

			if (nodeConfig.isNotify_config()) {
				ISubscriptions nodeSubscriptions = this.repository.getNodeSubscriptions(toJid, nodeName);
				Element del = new Element("delete", new String[] { "node" }, new String[] { nodeName });

				resultArray.addAll(this.publishModule.prepareNotification(del, packet.getStanzaTo(), nodeName, nodeConfig,
						nodeAffiliations, nodeSubscriptions));
			}

			final String parentNodeName = nodeConfig.getCollection();
			CollectionNodeConfig parentCollectionConfig = null;

			if ((parentNodeName != null) && !parentNodeName.equals("")) {
				parentCollectionConfig = (CollectionNodeConfig) repository.getNodeConfig(toJid, parentNodeName);
				if (parentCollectionConfig != null) {
					parentCollectionConfig.removeChildren(nodeName);
				}
			} else {
				repository.removeFromRootCollection(toJid, nodeName);
			}
			if (nodeConfig instanceof CollectionNodeConfig) {
				CollectionNodeConfig cnc = (CollectionNodeConfig) nodeConfig;
				final String[] childrenNodes = cnc.getChildren();

				if ((childrenNodes != null) && (childrenNodes.length > 0)) {
					for (String childNodeName : childrenNodes) {
						AbstractNodeConfig childNodeConfig = repository.getNodeConfig(toJid, childNodeName);

						if (childNodeConfig != null) {
							childNodeConfig.setCollection(parentNodeName);
							repository.update(toJid, childNodeName, childNodeConfig);
						}
						if (parentCollectionConfig != null) {
							parentCollectionConfig.addChildren(childNodeName);
						} else {
							repository.addToRootCollection(toJid, childNodeName);
						}
					}
				}
			}
			if (parentCollectionConfig != null) {
				repository.update(toJid, parentNodeName, parentCollectionConfig);
			}
			log.fine("Delete node [" + nodeName + "]");
			repository.deleteNode(toJid, nodeName);
			fireOnNodeDeleted(nodeName);

			return resultArray;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	;

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
