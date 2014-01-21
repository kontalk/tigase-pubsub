/*
 * PublishItemModule.java
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

import tigase.component2.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.Affiliation;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.PublisherModel;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:21:54 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PublishItemModule extends AbstractPubSubModule {

	private static class Item {
		final String id;
		final Date updateDate;

		Item(String id, Date date) {
			this.updateDate = date;
			this.id = id;
		}
	};

	private static final Criteria CRIT_PUBLISH = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("publish"));

	/** Field description */
	public final static String[] SUPPORTED_PEP_XMLNS = { "http://jabber.org/protocol/mood",
			"http://jabber.org/protocol/geoloc", "http://jabber.org/protocol/activity", "http://jabber.org/protocol/tune" };
	private long idCounter = 0;
	private final Set<String> pepNodes = new HashSet<String>();

	private final PresenceCollectorModule presenceCollector;

	private final XsltTool xslTransformer;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 * @param xsltTool
	 * @param presenceCollector
	 */
	public PublishItemModule(PubSubConfig config, PacketWriter packetWriter, XsltTool xsltTool,
			PresenceCollectorModule presenceCollector) {
		super(config, packetWriter);
		this.xslTransformer = xsltTool;
		this.presenceCollector = presenceCollector;
		for (String xmlns : SUPPORTED_PEP_XMLNS) {
			pepNodes.add(xmlns);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeConfig
	 * @param nodesSubscriptions
	 */
	protected void beforePrepareNotification(final AbstractNodeConfig nodeConfig, final ISubscriptions nodesSubscriptions) {
		if (nodeConfig.isPresenceExpired()) {
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
		return new String[] { "http://jabber.org/protocol/pubsub#publish", };
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT_PUBLISH;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	protected List<String> getParents(final BareJID serviceJid, final String nodeName) throws RepositoryException {
		ArrayList<String> result = new ArrayList<String>();
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJid, nodeName);
		String cn = nodeConfig.getCollection();

		while ((cn != null) && !"".equals(cn)) {
			result.add(cn);

			AbstractNodeConfig nc = getRepository().getNodeConfig(serviceJid, cn);

			cn = nc.getCollection();
		}
		;

		return result;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param id
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	protected JID[] getValidBuddies(BareJID id) throws RepositoryException {
		ArrayList<JID> result = new ArrayList<JID>();
		BareJID[] rosterJids = this.getRepository().getUserRoster(id);

		if (rosterJids != null) {
			for (BareJID j : rosterJids) {
				String sub = this.getRepository().getBuddySubscription(id, j);

				if ((sub != null) && (sub.equals("both") || sub.equals("from"))) {
					result.add(JID.jidInstance(j));
				}
			}
		}

		return result.toArray(new JID[] {});
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @return
	 */
	public boolean isPEPNodeName(String nodeName) {
		return this.pepNodes.contains(nodeName);
	}

	private List<Element> makeItemsToSend(Element publish) {
		List<Element> items = new ArrayList<Element>();

		for (Element si : publish.getChildren()) {
			if (!"item".equals(si.getName())) {
				continue;
			}
			items.add(si);
		}

		return items;
	}

	private void pepProcess(final Packet packet, final Element pubSub, final Element publish) throws RepositoryException {
		final JID senderJid = packet.getStanzaFrom();
		final Element item = publish.getChild("item");
		final Element items = new Element("items", new String[] { "node" },
				new String[] { publish.getAttributeStaticStr("node") });

		items.addChild(item);

		JID[] subscribers = getValidBuddies(senderJid.getBareJID());
		sendNotifications(subscribers, items, senderJid, null, publish.getAttributeStaticStr("node"),
				null);

		packetWriter.write(packet.okResult((Element) null, 0));
		sendNotifications(new JID[] { senderJid }, items, senderJid, null,
				publish.getAttributeStaticStr("node"), null);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param itemToSend
	 * @param jidFrom
	 * @param publisherNodeName
	 * @param nodeConfig
	 * @param nodeAffiliations
	 * @param nodesSubscriptions
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public void sendNotifications(Element itemToSend, final JID jidFrom, final String publisherNodeName,
			AbstractNodeConfig nodeConfig, IAffiliations nodeAffiliations, ISubscriptions nodesSubscriptions)
			throws RepositoryException {
		sendNotifications(itemToSend, jidFrom, publisherNodeName, null, nodeConfig, nodeAffiliations,
				nodesSubscriptions);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param itemToSend
	 * @param jidFrom
	 * @param publisherNodeName
	 * @param headers
	 * @param nodeConfig
	 * @param nodeAffiliations
	 * @param nodesSubscriptions
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public void sendNotifications(final Element itemToSend, final JID jidFrom, final String publisherNodeName,
			final Map<String, String> headers, AbstractNodeConfig nodeConfig, IAffiliations nodeAffiliations,
			ISubscriptions nodesSubscriptions) throws RepositoryException {
		beforePrepareNotification(nodeConfig, nodesSubscriptions);

		HashSet<JID> tmp = new HashSet<JID>();
		for (BareJID j : getActiveSubscribers(nodeConfig, nodeAffiliations, nodesSubscriptions)) {
			tmp.add(JID.jidInstance(j));
		}
		boolean updateSubscriptions = false;

		if (nodeConfig.isPresenceExpired()) {
			Iterator<JID> it = tmp.iterator();

			while (it.hasNext()) {
				final JID jid = it.next();
				boolean available = this.presenceCollector.isJidAvailable(jid.getBareJID());
				final UsersAffiliation afi = nodeAffiliations.getSubscriberAffiliation(jid.getBareJID());

				if ((afi == null) || (!available && (afi.getAffiliation() == Affiliation.member))) {
					it.remove();
					nodesSubscriptions.changeSubscription(jid.getBareJID(), Subscription.none);
					updateSubscriptions = true;
					if (log.isLoggable(Level.FINE)) {
						log.fine("Subscriptione expired. Node: " + nodeConfig.getNodeName() + ", jid: " + jid);
					}
				}
			}
		}
		if (updateSubscriptions) {
			this.getRepository().update(jidFrom.getBareJID(), nodeConfig.getNodeName(), nodesSubscriptions);
		}

		JID[] subscribers = tmp.toArray(new JID[] {});

		if (nodeConfig.isDeliverPresenceBased()) {
			HashSet<JID> s = new HashSet<JID>();

			for (JID jid : subscribers) {
				for (JID subjid : this.presenceCollector.getAllAvailableResources(jid.getBareJID())) {
					s.add(subjid);
				}
			}
			subscribers = s.toArray(new JID[] {});
		}

		sendNotifications(subscribers, itemToSend, jidFrom, nodeConfig, publisherNodeName, headers);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param subscribers
	 * @param itemToSend
	 * @param jidFrom
	 * @param nodeConfig
	 * @param publisherNodeName
	 * @param headers
	 * 
	 * @return
	 */
	public void sendNotifications(final JID[] subscribers, final Element itemToSend, final JID jidFrom,
			AbstractNodeConfig nodeConfig, final String publisherNodeName, final Map<String, String> headers) {
		List<Element> body = null;

		if ((this.xslTransformer != null) && (nodeConfig != null)) {
			try {
				body = this.xslTransformer.transform(itemToSend, nodeConfig);
			} catch (Exception e) {
				body = null;
				log.log(Level.WARNING, "Problem with generating BODY", e);
			}
		}
		for (JID jid : subscribers) {
			Packet packet = Message.getMessage(jidFrom, jid, null, null, null, null, String.valueOf(++this.idCounter));
			Element message = packet.getElement();

			if (body != null) {
				message.addChildren(body);
			}

			Element event = new Element("event", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/pubsub#event" });

			event.addChild(itemToSend);
			message.addChild(event);
			if ((headers != null) && (headers.size() > 0)) {
				Element headElem = new Element("headers", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/shim" });

				for (Entry<String, String> entry : headers.entrySet()) {
					Element h = new Element("header", entry.getValue(), new String[] { "name" },
							new String[] { entry.getKey() });

					headElem.addChild(h);
				}
				message.addChild(headElem);
			}
			
			// we are adding notifications to outgoing queue instead temporary list
			// of notifications to send, so before creating next packets other threads
			// will be able to process first notifications and deliver them
			packetWriter.write(packet);
		}
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
		final Element element = packet.getElement();
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element publish = pubSub.getChild("publish");
		final String nodeName = publish.getAttributeStaticStr("node");

		try {
			if (isPEPNodeName(nodeName)) {
				pepProcess(packet, pubSub, publish);
				return;
			}

			AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			} else {
				if (nodeConfig.getNodeType() == NodeType.collection) {
					throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
							"publish"));
				}
			}

			IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(toJid, nodeName);
			final UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(packet.getStanzaFrom().getBareJID());
			final ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(toJid, nodeName);

			// XXX #125
			final PublisherModel publisherModel = nodeConfig.getPublisherModel();

			if (!senderAffiliation.getAffiliation().isPublishItem()) {
				if ((publisherModel == PublisherModel.publishers)
						|| ((publisherModel == PublisherModel.subscribers) && (nodeSubscriptions.getSubscription(packet.getStanzaFrom().getBareJID()) != Subscription.subscribed))) {
					throw new PubSubException(Authorization.FORBIDDEN);
				}
			}

			LeafNodeConfig leafNodeConfig = (LeafNodeConfig) nodeConfig;
			List<Element> itemsToSend = makeItemsToSend(publish);
			final Packet resultIq = packet.okResult((Element) null, 0);

			if (leafNodeConfig.isPersistItem()) {

				// checking ID
				Element resPubsub = new Element("pubsub", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/pubsub" });

				resultIq.getElement().addChild(resPubsub);

				Element resPublish = new Element("publish", new String[] { "node" }, new String[] { nodeName });

				resPubsub.addChild(resPublish);
				for (Element item : itemsToSend) {
					String id = item.getAttributeStaticStr("id");

					if (id == null) {
						id = Utils.createUID();

						// throw new PubSubException(Authorization.BAD_REQUEST,
						// PubSubErrorCondition.ITEM_REQUIRED);
						item.setAttribute("id", id);
					}
					resPublish.addChild(new Element("item", new String[] { "id" }, new String[] { id }));
				}
			}
			packetWriter.write(resultIq);

			final Element items = new Element("items", new String[] { "node" }, new String[] { nodeName });

			items.addChildren(itemsToSend);
			sendNotifications(items, packet.getStanzaTo(), nodeName,
					this.getRepository().getNodeConfig(toJid, nodeName), nodeAffiliations, nodeSubscriptions);

			List<String> parents = getParents(toJid, nodeName);

			if ((parents != null) && (parents.size() > 0)) {
				for (String collection : parents) {
					Map<String, String> headers = new HashMap<String, String>();

					headers.put("Collection", collection);

					AbstractNodeConfig colNodeConfig = this.getRepository().getNodeConfig(toJid, collection);
					ISubscriptions colNodeSubscriptions = this.getRepository().getNodeSubscriptions(toJid, collection);
					IAffiliations colNodeAffiliations = this.getRepository().getNodeAffiliations(toJid, collection);

					sendNotifications(items, packet.getStanzaTo(), nodeName, headers, colNodeConfig,
							colNodeAffiliations, colNodeSubscriptions);
				}
			}
			if (leafNodeConfig.isPersistItem()) {
				IItems nodeItems = getRepository().getNodeItems(toJid, nodeName);

				for (Element item : itemsToSend) {
					final String id = item.getAttributeStaticStr("id");

					nodeItems.writeItem(System.currentTimeMillis(), id, element.getAttributeStaticStr("from"), item);
				}
				if (leafNodeConfig.getMaxItems() != null) {
					trimItems(nodeItems, leafNodeConfig.getMaxItems());
				}
			}
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	public void publishLastItem(BareJID serviceJid, AbstractNodeConfig nodeConfig, JID destinationJID)
			throws RepositoryException {
		IItems nodeItems = this.getRepository().getNodeItems(serviceJid, nodeConfig.getNodeName());
		String[] ids = nodeItems.getItemsIds();

		if (ids != null && ids.length > 0) {
			String lastID = ids[ids.length - 1];
			Element payload = nodeItems.getItem(lastID);

			Element items = new Element("items");
			items.addAttribute("node", nodeConfig.getNodeName());
			Element item = new Element("item");
			item.addAttribute("id", lastID);
			items.addChild(item);
			item.addChild(payload);

			sendNotifications(new JID[] { destinationJID }, items, JID.jidInstance(serviceJid),
					nodeConfig, nodeConfig.getNodeName(), null);
		}

	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeItems
	 * @param maxItems
	 * 
	 * @throws RepositoryException
	 */
	public void trimItems(final IItems nodeItems, final Integer maxItems) throws RepositoryException {
		final String[] ids = nodeItems.getItemsIds();

		if ((ids == null) || (ids.length <= maxItems)) {
			return;
		}

		final ArrayList<Item> items = new ArrayList<Item>();

		for (String id : ids) {
			Date updateDate = nodeItems.getItemUpdateDate(id);

			if (updateDate != null) {
				Item i = new Item(id, updateDate);

				items.add(i);
			}
		}
		Collections.sort(items, new Comparator<Item>() {
			@Override
			public int compare(Item o1, Item o2) {
				return o2.updateDate.compareTo(o1.updateDate);
			}
		});
		for (int i = maxItems; i < items.size(); i++) {
			Item it = items.get(i);

			nodeItems.deleteItem(it.id);
		}
	}
}
