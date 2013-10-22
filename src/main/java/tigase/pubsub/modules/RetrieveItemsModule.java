/*
 * RetrieveItemsModule.java
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import tigase.component2.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.AccessModel;
import tigase.pubsub.Affiliation;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
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
import tigase.xmpp.JID;

/**
 * Class description
 * 
 * 
 */
public class RetrieveItemsModule extends AbstractPubSubModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("items"));

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 */
	public RetrieveItemsModule(PubSubConfig config, PacketWriter packetWriter) {
		super(config, packetWriter);
	}

	private Integer asInteger(String attribute) {
		if (attribute == null) {
			return null;
		}

		return Integer.parseInt(attribute);
	}

	private List<String> extractItemsIds(final Element items) throws PubSubException {
		List<Element> il = items.getChildren();

		if ((il == null) || (il.size() == 0)) {
			return null;
		}

		final List<String> result = new ArrayList<String>();

		for (Element i : il) {
			final String id = i.getAttributeStaticStr("id");

			if (!"item".equals(i.getName()) || (id == null)) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}
			result.add(id);
		}

		return result;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#retrieve-items" };
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
	public void process(final Packet packet) throws PubSubException {
		try {
			final BareJID toJid = packet.getStanzaTo().getBareJID();
			final Element pubsub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub");
			final Element items = pubsub.getChild("items");
			final String nodeName = items.getAttributeStaticStr("node");
			final Integer maxItems = asInteger(items.getAttributeStaticStr("max_items"));
			final JID senderJid = packet.getStanzaFrom();

			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODEID_REQUIRED);
			}

			// XXX CHECK RIGHTS AUTH ETC
			AbstractNodeConfig nodeConfig = this.getRepository().getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			}
			if ((nodeConfig.getNodeAccessModel() == AccessModel.open)
					&& !Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfig.getDomains())) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			IAffiliations nodeAffiliations = this.getRepository().getNodeAffiliations(toJid, nodeName);
			UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid.getBareJID());

			if (senderAffiliation.getAffiliation() == Affiliation.outcast) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(toJid, nodeName);
			Subscription senderSubscription = nodeSubscriptions.getSubscription(senderJid.getBareJID());

			if ((nodeConfig.getNodeAccessModel() == AccessModel.whitelist)
					&& !senderAffiliation.getAffiliation().isRetrieveItem()) {
				throw new PubSubException(Authorization.NOT_ALLOWED, PubSubErrorCondition.CLOSED_NODE);
			} else if ((nodeConfig.getNodeAccessModel() == AccessModel.authorize)
					&& ((senderSubscription != Subscription.subscribed) || !senderAffiliation.getAffiliation().isRetrieveItem())) {
				throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_SUBSCRIBED);
			} else if (nodeConfig.getNodeAccessModel() == AccessModel.presence) {
				boolean allowed = hasSenderSubscription(senderJid.getBareJID(), nodeAffiliations, nodeSubscriptions);

				if (!allowed) {
					throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.PRESENCE_SUBSCRIPTION_REQUIRED);
				}
			} else if (nodeConfig.getNodeAccessModel() == AccessModel.roster) {
				boolean allowed = isSenderInRosterGroup(senderJid.getBareJID(), nodeConfig, nodeAffiliations, nodeSubscriptions);

				if (!allowed) {
					throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_IN_ROSTER_GROUP);
				}
			}
			if (nodeConfig instanceof CollectionNodeConfig) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
						"retrieve-items"));
			} else if ((nodeConfig instanceof LeafNodeConfig) && !((LeafNodeConfig) nodeConfig).isPersistItem()) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
						"persistent-items"));
			}

			List<String> requestedId = extractItemsIds(items);
			final Element rpubsub = new Element("pubsub", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/pubsub" });
			final Element ritems = new Element("items", new String[] { "node" }, new String[] { nodeName });
			final Packet iq = packet.okResult(rpubsub, 0);

			rpubsub.addChild(ritems);

			IItems nodeItems = this.getRepository().getNodeItems(toJid, nodeName);

			if (requestedId == null) {
				String[] ids = nodeItems.getItemsIds();

				if (ids != null) {
					requestedId = Arrays.asList(ids);
					requestedId = new ArrayList<String>(requestedId);
					Collections.reverse(requestedId);
				}
			}
			if (requestedId != null) {
				int c = 0;

				List<Element> ritemsList = new ArrayList<Element>();
				for (String id : requestedId) {
					Element item = nodeItems.getItem(id);

					if (item != null) {
						if ((maxItems != null) && (++c) > maxItems) {
							break;
						}
						ritemsList.add(item);
					}
				}
				Collections.reverse(ritemsList);
				ritems.addChildren(ritemsList);
			}

			packetWriter.write(iq);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}
