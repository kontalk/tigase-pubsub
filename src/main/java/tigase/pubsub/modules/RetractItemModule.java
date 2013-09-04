/*
 * RetractItemModule.java
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
import java.util.Date;
import java.util.List;

import tigase.component.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;

/**
 * Class description
 * 
 * 
 */
public class RetractItemModule extends AbstractPubSubModule {
	private static final Criteria CRIT_RETRACT = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("retract"));

	private final PublishItemModule publishModule;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 * @param publishItemModule
	 */
	public RetractItemModule(final PubSubConfig config, final IPubSubRepository pubsubRepository, PacketWriter packetWriter,
			final PublishItemModule publishItemModule) {
		super(config, pubsubRepository, packetWriter);
		this.publishModule = publishItemModule;
	}

	private Element createNotification(final LeafNodeConfig config, final List<String> itemsToSend, final String nodeName) {
		Element items = new Element("items", new String[] { "node" }, new String[] { nodeName });

		for (String id : itemsToSend) {
			items.addChild(new Element("retract", new String[] { "id" }, new String[] { id }));
		}

		return items;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#retract-items" };
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT_RETRACT;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 * @return
	 * 
	 * @throws PubSubException
	 */
	@Override
	public void process(Packet packet) throws PubSubException {
		final BareJID toJid = packet.getStanzaTo().getBareJID();
		final Element pubSub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element retract = pubSub.getChild("retract");
		final String nodeName = retract.getAttributeStaticStr("node");

		try {
			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}

			AbstractNodeConfig nodeConfig = repository.getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(packet.getElement(), Authorization.ITEM_NOT_FOUND);
			} else if (nodeConfig.getNodeType() == NodeType.collection) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
						"retract-items"));
			}

			IAffiliations nodeAffiliations = repository.getNodeAffiliations(toJid, nodeName);
			UsersAffiliation affiliation = nodeAffiliations.getSubscriberAffiliation(packet.getStanzaFrom().toString());

			if (!affiliation.getAffiliation().isDeleteItem()) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			LeafNodeConfig leafNodeConfig = (LeafNodeConfig) nodeConfig;

			if (!leafNodeConfig.isPersistItem()) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
						"persistent-items"));
			}

			List<String> itemsToDelete = new ArrayList<String>();

			if (retract.getChildren() != null) {
				for (Element item : retract.getChildren()) {
					final String n = item.getAttributeStaticStr("id");

					if (n != null) {
						itemsToDelete.add(n);
					} else {
						throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.ITEM_REQUIRED);
					}
				}
			} else {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.ITEM_REQUIRED);
			}

			List<Packet> result = new ArrayList<Packet>();

			result.add(packet.okResult((Element) null, 0));

			ISubscriptions nodeSubscriptions = repository.getNodeSubscriptions(toJid, nodeName);
			IItems nodeItems = this.repository.getNodeItems(toJid, nodeName);

			for (String id : itemsToDelete) {
				Date date = nodeItems.getItemCreationDate(id);

				if (date != null) {
					Element notification = createNotification(leafNodeConfig, itemsToDelete, nodeName);

					result.addAll(publishModule.prepareNotification(notification, packet.getStanzaTo(), nodeName, nodeConfig,
							nodeAffiliations, nodeSubscriptions));
					nodeItems.deleteItem(id);
				}
			}

			packetWriter.write(result);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}
