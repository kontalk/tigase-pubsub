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
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tigase.component2.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.AccessModel;
import tigase.pubsub.Affiliation;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.util.DateTimeFormatter;
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

	private static final Comparator<IItems.ItemMeta> itemsCreationDateComparator = new Comparator<IItems.ItemMeta>() {

		@Override
		public int compare(IItems.ItemMeta o1, IItems.ItemMeta o2) {
			return o1.getCreationDate().compareTo(o2.getCreationDate()) * (-1);
		}

	};

	private final DateTimeFormatter dtf = new DateTimeFormatter();

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

	private void checkPermission(JID senderJid, BareJID toJid, String nodeName, AbstractNodeConfig nodeConfig)
			throws PubSubException, RepositoryException {
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

		if ((nodeConfig.getNodeAccessModel() == AccessModel.whitelist) && !senderAffiliation.getAffiliation().isRetrieveItem()) {
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
			final JID senderJid = packet.getStanzaFrom();

			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODEID_REQUIRED);
			}

			// XXX CHECK RIGHTS AUTH ETC
			AbstractNodeConfig nodeConfig = this.getRepository().getNodeConfig(toJid, nodeName);
			checkPermission(senderJid, toJid, nodeName, nodeConfig);

			if (nodeConfig instanceof CollectionNodeConfig) {
				List<IItems.ItemMeta> itemsMeta = new ArrayList<IItems.ItemMeta>();
				String[] childNodes = nodeConfig.getChildren();
				Map<String, IItems> nodeItemsCache = new HashMap<String, IItems>();
				if (childNodes != null) {
					for (String childNodeName : childNodes) {
						AbstractNodeConfig childNode = getRepository().getNodeConfig(toJid, childNodeName);
						if (childNode == null || childNode.getNodeType() != NodeType.leaf)
							continue;

						LeafNodeConfig leafChildNode = (LeafNodeConfig) childNode;
						if (!leafChildNode.isPersistItem())
							continue;

						try {
							checkPermission(senderJid, toJid, childNodeName, childNode);
							IItems childNodeItems = getRepository().getNodeItems(toJid, childNodeName);
							nodeItemsCache.put(childNodeName, childNodeItems);
							itemsMeta.addAll(childNodeItems.getItemsMeta());
						} catch (PubSubException ex) {
							// here we ignode PubSubExceptions as they are
							// permission exceptions for subnodes
						}
					}
				}

				Collections.sort(itemsMeta, itemsCreationDateComparator);

				final Element rpubsub = new Element("pubsub", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/pubsub" });
				final Packet iq = packet.okResult(rpubsub, 0);

				Integer maxItems = asInteger(items.getAttributeStaticStr("max_items"));
				Integer offset = 0;

				final Element rsmGet = pubsub.getChild("set", "http://jabber.org/protocol/rsm");
				if (rsmGet != null) {
					Element m = rsmGet.getChild("max");
					if (m != null) {
						maxItems = asInteger(m.getCData());
					}
					m = rsmGet.getChild("index");
					if (m != null) {
						offset = asInteger(m.getCData());
					}
				}

				Map<String, List<Element>> nodeItemsElMap = new HashMap<String, List<Element>>();
				int idx = offset;
				int count = 0;
				String lastId = null;
				while (itemsMeta.size() > idx && (maxItems == null || count < maxItems)) {
					IItems.ItemMeta itemMeta = itemsMeta.get(idx);
					String node = itemMeta.getNode();
					List<Element> nodeItemsElems = nodeItemsElMap.get(node);
					if (nodeItemsElems == null) {
						// nodeItemsEl = new Element("items", new String[] {
						// "node" }, new String[] { node });
						nodeItemsElems = new ArrayList<Element>();
						nodeItemsElMap.put(node, nodeItemsElems);
					}

					IItems nodeItems = nodeItemsCache.get(node);
					Element item = nodeItems.getItem(itemMeta.getId());
					lastId = itemMeta.getId();
					nodeItemsElems.add(item);

					idx++;
					count++;
				}

				nodeItemsCache.clear();

				for (Map.Entry<String, List<Element>> entry : nodeItemsElMap.entrySet()) {
					Element itemsEl = new Element("items", new String[] { "node" }, new String[] { entry.getKey() });

					List<Element> itemsElems = entry.getValue();
					Collections.reverse(itemsElems);
					itemsEl.addChildren(itemsElems);

					rpubsub.addChild(itemsEl);
				}

				if (nodeItemsElMap.size() > 0) {
					final Element rsmResponse = new Element("set", new String[] { "xmlns" },
							new String[] { "http://jabber.org/protocol/rsm" });

					rsmResponse.addChild(new Element("first", itemsMeta.get(offset).getId(), new String[] { "index" },
							new String[] { String.valueOf(offset) }));
					rsmResponse.addChild(new Element("count", "" + itemsMeta.size()));
					if (lastId != null)
						rsmResponse.addChild(new Element("last", lastId));

					rpubsub.addChild(rsmResponse);
				} else {
					rpubsub.addChild(new Element("items", new String[] { "node" }, new String[] { nodeName }));
				}

				packetWriter.write(iq);
				return;
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

			Integer maxItems = asInteger(items.getAttributeStaticStr("max_items"));
			Integer offset = 0;
			Calendar dtAfter = null;
			String afterId = null;
			String beforeId = null;

			final Element rsmGet = pubsub.getChild("set", "http://jabber.org/protocol/rsm");
			if (rsmGet != null) {
				Element m = rsmGet.getChild("max");
				if (m != null)
					maxItems = asInteger(m.getCData());
				m = rsmGet.getChild("index");
				if (m != null)
					offset = asInteger(m.getCData());
				m = rsmGet.getChild("before");
				if (m != null)
					beforeId = m.getCData();
				m = rsmGet.getChild("adter");
				if (m != null)
					afterId = m.getCData();
				m = rsmGet.getChild("dt_after", "http://tigase.org/pubsub");
				if (m != null)
					dtAfter = dtf.parseDateTime(m.getCData());
			}

			IItems nodeItems = this.getRepository().getNodeItems(toJid, nodeName);

			if (requestedId == null) {
				String[] ids = nodeItems.getItemsIds();

				if (ids != null) {
					requestedId = Arrays.asList(ids);
					requestedId = new ArrayList<String>(requestedId);
					Collections.reverse(requestedId);
				}
			}

			final Element rsmResponse = new Element("set", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/rsm" });

			if (requestedId != null) {
				if (maxItems == null)
					maxItems = requestedId.size();

				List<Element> ritemsList = new ArrayList<Element>();

				rsmResponse.addChild(new Element("count", "" + requestedId.size()));

				String lastId = null;
				int c = 0;
				boolean allow = false;
				for (int i = 0; i < requestedId.size(); i++) {
					if (i + offset >= requestedId.size())
						continue;

					if (c > maxItems)
						break;
					String id = requestedId.get(i + offset);

					Date cd = nodeItems.getItemCreationDate(id);
					if (dtAfter != null && !cd.after(dtAfter.getTime()))
						continue;

					if (afterId != null && !allow && afterId.equals(id)) {
						allow = true;
						continue;
					} else if (afterId != null && !allow)
						continue;

					if (beforeId != null && beforeId.equals(id))
						break;

					if (c == 0) {
						rsmResponse.addChild(new Element("first", id, new String[] { "index" }, new String[] { ""
								+ (i + offset) }));
					}
					Element item = nodeItems.getItem(id);

					lastId = id;
					ritemsList.add(item);
					++c;
				}
				if (lastId != null)
					rsmResponse.addChild(new Element("last", lastId));

				Collections.reverse(ritemsList);
				ritems.addChildren(ritemsList);

				if (maxItems != requestedId.size())
					rpubsub.addChild(rsmResponse);

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