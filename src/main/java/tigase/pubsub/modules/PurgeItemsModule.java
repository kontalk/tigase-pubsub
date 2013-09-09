/*
 * PurgeItemsModule.java
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
public class PurgeItemsModule extends AbstractPubSubModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("purge"));

	private final PublishItemModule publishModule;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 * @param publishModule
	 */
	public PurgeItemsModule(PubSubConfig config, PacketWriter packetWriter, PublishItemModule publishModule) {
		super(config, packetWriter);
		this.publishModule = publishModule;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#purge-nodes" };
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
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
		final Element pubSub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
		final Element purge = pubSub.getChild("purge");
		final String nodeName = purge.getAttributeStaticStr("node");

		try {
			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}

			AbstractNodeConfig nodeConfig = this.getRepository().getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(packet.getElement(), Authorization.ITEM_NOT_FOUND);
			} else if (nodeConfig.getNodeType() == NodeType.collection) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
						"purge-nodes"));
			}

			IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(toJid, nodeName);
			UsersAffiliation affiliation = nodeAffiliations.getSubscriberAffiliation(packet.getStanzaFrom().getBareJID());

			if (!affiliation.getAffiliation().isPurgeNode()) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			LeafNodeConfig leafNodeConfig = (LeafNodeConfig) nodeConfig;

			if (!leafNodeConfig.isPersistItem()) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
						"persistent-items"));
			}

			List<Packet> result = new ArrayList<Packet>();

			result.add(packet.okResult((Element) null, 0));

			final IItems nodeItems = this.getRepository().getNodeItems(toJid, nodeName);
			String[] itemsToDelete = nodeItems.getItemsIds();
			ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(toJid, nodeName);

			result.addAll(publishModule.prepareNotification(new Element("purge", new String[] { "node" },
					new String[] { nodeName }), packet.getStanzaTo(), nodeName, nodeConfig, nodeAffiliations, nodeSubscriptions));
			log.info("Purging node " + nodeName);
			if (itemsToDelete != null) {
				for (String id : itemsToDelete) {
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
