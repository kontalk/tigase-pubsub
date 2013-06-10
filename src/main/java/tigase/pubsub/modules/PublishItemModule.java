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

//~--- non-JDK imports --------------------------------------------------------

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;

import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PublisherModel;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;

import tigase.util.JIDUtils;

import tigase.xml.Element;

import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;

//~--- JDK imports ------------------------------------------------------------

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import tigase.pubsub.PacketWriter;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

/**
 * Class description
 *
 *
 * @version 5.0.0, 2010.03.27 at 05:21:54 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PublishItemModule
				extends AbstractModule {
	// ~--- fields
	// ---------------------------------------------------------------

	/** Field description */
	public final static String[] SUPPORTED_PEP_XMLNS = { "http://jabber.org/protocol/mood",
					"http://jabber.org/protocol/geoloc", "http://jabber.org/protocol/activity",
					"http://jabber.org/protocol/tune" };;
	private static final Criteria CRIT_PUBLISH =
		ElementCriteria.nameType("iq", "set").add(
				ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(
				ElementCriteria.name("publish"));

	//~--- fields ---------------------------------------------------------------

	private long idCounter             = 0;
	private final Set<String> pepNodes = new HashSet<String>();
	private final PresenceCollectorModule presenceCollector;

	// ~--- constructors
	// ---------------------------------------------------------
	private final XsltTool xslTransformer;

	//~--- constructors ---------------------------------------------------------

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param config
	 * @param pubsubRepository
	 * @param xsltTool
	 * @param presenceCollector
	 */
	public PublishItemModule(PubSubConfig config, IPubSubRepository pubsubRepository,
													 XsltTool xsltTool, PresenceCollectorModule presenceCollector) {
		super(config, pubsubRepository);
		this.xslTransformer    = xsltTool;
		this.presenceCollector = presenceCollector;
		for (String xmlns : SUPPORTED_PEP_XMLNS) {
			pepNodes.add(xmlns);
		}
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param nodeConfig
	 * @param nodesSubscriptions
	 */
	protected void beforePrepareNotification(final AbstractNodeConfig nodeConfig,
					final ISubscriptions nodesSubscriptions) {
		if (nodeConfig.isPresenceExpired()) {}
	}

	//~--- get methods ----------------------------------------------------------

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

	// ~--- methods
	// --------------------------------------------------------------

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
		ArrayList<String> result      = new ArrayList<String>();
		AbstractNodeConfig nodeConfig = repository.getNodeConfig(serviceJid, nodeName);
		String cn                     = nodeConfig.getCollection();

		while ((cn != null) &&!"".equals(cn)) {
			result.add(cn);

			AbstractNodeConfig nc = repository.getNodeConfig(serviceJid, cn);

			cn = nc.getCollection();
		}
		;

		return result;
	}

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	protected String[] getValidBuddies(String jid) throws RepositoryException {
		ArrayList<String> result = new ArrayList<String>();
		BareJID id               = BareJID.bareJIDInstanceNS(jid);
		String[] rosterJids      = this.repository.getUserRoster(id);

		if (rosterJids != null) {
			for (String j : rosterJids) {
				String sub = this.repository.getBuddySubscription(id, j);

				if ((sub != null) && (sub.equals("both") || sub.equals("from"))) {
					result.add(j);
				}
			}
		}

		return result.toArray(new String[] {});
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

	//~--- methods --------------------------------------------------------------

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

	private List<Packet> pepProcess(final Packet packet, final Element pubSub,
																	 final Element publish)
					throws RepositoryException {
		final JID senderJid = packet.getStanzaFrom();
		final Element item  = publish.getChild("item");
		final Element items = new Element("items", new String[] { "node" },
															 new String[] { publish.getAttributeStaticStr("node") });

		items.addChild(item);

		String[] subscribers = getValidBuddies(senderJid.getBareJID().toString());
		List<Packet> result = prepareNotification(subscribers, items, senderJid, null,
														 publish.getAttributeStaticStr("node"), null);

		result.add(packet.okResult((Element) null, 0));
		result.addAll(prepareNotification(new String[] { senderJid.toString() }, items, senderJid, null,
																			publish.getAttributeStaticStr("node"), null));

		return result;
	}

	// ~--- get methods
	// ----------------------------------------------------------

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
	public List<Packet> prepareNotification(Element itemToSend, final JID jidFrom,
					final String publisherNodeName, AbstractNodeConfig nodeConfig,
					IAffiliations nodeAffiliations, ISubscriptions nodesSubscriptions)
					throws RepositoryException {
		return prepareNotification(itemToSend, jidFrom, publisherNodeName, null, nodeConfig,
															 nodeAffiliations, nodesSubscriptions);
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
	public List<Packet> prepareNotification(final Element itemToSend,
					final JID jidFrom, final String publisherNodeName,
					final Map<String, String> headers, AbstractNodeConfig nodeConfig,
					IAffiliations nodeAffiliations, ISubscriptions nodesSubscriptions)
					throws RepositoryException {
		beforePrepareNotification(nodeConfig, nodesSubscriptions);

		List<String> tmp = getActiveSubscribers(nodeConfig, nodeAffiliations,
												 nodesSubscriptions);
		boolean updateSubscriptions = false;

		if (nodeConfig.isPresenceExpired()) {
			Iterator<String> it = tmp.iterator();

			while (it.hasNext()) {
				final String jid           = it.next();
				boolean available          = this.presenceCollector.isJidAvailable(jid);
				final UsersAffiliation afi = nodeAffiliations.getSubscriberAffiliation(jid);

				if ((afi == null) ||
						(!available && (afi.getAffiliation() == Affiliation.member))) {
					it.remove();
					nodesSubscriptions.changeSubscription(jid, Subscription.none);
					updateSubscriptions = true;
					if (log.isLoggable(Level.FINE)) {
						log.fine("Subscriptione expired. Node: " + nodeConfig.getNodeName() +
										 ", jid: " + jid);
					}
				}
			}
		}
		if (updateSubscriptions) {
			this.repository.update(jidFrom.getBareJID(), nodeConfig.getNodeName(), nodesSubscriptions);
		}

		String[] subscribers = tmp.toArray(new String[] {});

		if (nodeConfig.isDeliverPresenceBased()) {
			List<String> s = new ArrayList<String>();

			for (String jid : subscribers) {
				for (String subjid : this.presenceCollector.getAllAvailableResources(jid)) {
					s.add(subjid);
				}
			}
			subscribers = s.toArray(new String[] {});
		}

		return prepareNotification(subscribers, itemToSend, jidFrom, nodeConfig,
															 publisherNodeName, headers);
	}

	// ~--- methods
	// --------------------------------------------------------------

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
	public List<Packet> prepareNotification(final String[] subscribers,
					final Element itemToSend, final JID jidFrom, AbstractNodeConfig nodeConfig,
					final String publisherNodeName, final Map<String, String> headers) {
		ArrayList<Packet> result = new ArrayList<Packet>();
		List<Element> body        = null;

		if ((this.xslTransformer != null) && (nodeConfig != null)) {
			try {
				body = this.xslTransformer.transform(itemToSend, nodeConfig);
			} catch (Exception e) {
				body = null;
				log.log(Level.WARNING, "Problem with generating BODY", e);
			}
		}
		for (String jidStr : subscribers) {
			JID jid = JID.jidInstanceNS(jidStr);
			Packet packet = Message.getMessage(jidFrom, jid, null, null, null, null, 
						String.valueOf(++this.idCounter));
			Element message = packet.getElement();
			
			if (body != null) {
				message.addChildren(body);
			}

			Element event = new Element("event", new String[] { "xmlns" },
																	new String[] {
																		"http://jabber.org/protocol/pubsub#event" });

			event.addChild(itemToSend);
			message.addChild(event);
			if ((headers != null) && (headers.size() > 0)) {
				Element headElem = new Element("headers", new String[] { "xmlns" },
																			 new String[] {
																				 "http://jabber.org/protocol/shim" });

				for (Entry<String, String> entry : headers.entrySet()) {
					Element h = new Element("header", entry.getValue(), new String[] { "name" },
																	new String[] { entry.getKey() });

					headElem.addChild(h);
				}
				message.addChild(headElem);
			}
			result.add(packet);
		}

		return result;
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
	public List<Packet> process(Packet packet, PacketWriter packetWriter)
					throws PubSubException {
		final BareJID toJid = packet.getStanzaTo().getBareJID();
		final Element element = packet.getElement();
		final Element pubSub = element.getChild("pubsub",
														 "http://jabber.org/protocol/pubsub");
		final Element publish = pubSub.getChild("publish");
		final String nodeName = publish.getAttributeStaticStr("node");

		try {
			if (isPEPNodeName(nodeName)) {
				return pepProcess(packet, pubSub, publish);
			}

			AbstractNodeConfig nodeConfig = repository.getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			} else {
				if (nodeConfig.getNodeType() == NodeType.collection) {
					throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED,
																		new PubSubErrorCondition("unsupported", "publish"));
				}
			}

			IAffiliations nodeAffiliations           = repository.getNodeAffiliations(toJid, nodeName);
			final UsersAffiliation senderAffiliation =
				nodeAffiliations.getSubscriberAffiliation(element.getAttributeStaticStr("from"));
			final ISubscriptions nodeSubscriptions = repository.getNodeSubscriptions(toJid, nodeName);

			// XXX #125
			final PublisherModel publisherModel = nodeConfig.getPublisherModel();

			if (!senderAffiliation.getAffiliation().isPublishItem()) {
				if ((publisherModel == PublisherModel.publishers) ||
						((publisherModel == PublisherModel.subscribers) &&
						 (nodeSubscriptions.getSubscription(element.getAttributeStaticStr("from")) !=
							Subscription.subscribed))) {
					throw new PubSubException(Authorization.FORBIDDEN);
				}
			}

			LeafNodeConfig leafNodeConfig = (LeafNodeConfig) nodeConfig;
			List<Element> itemsToSend     = makeItemsToSend(publish);
			List<Packet> result           = new ArrayList<Packet>();
			final Packet resultIq         = packet.okResult((Element) null, 0);

			result.add(resultIq);
			if (leafNodeConfig.isPersistItem()) {

				// checking ID
				Element resPubsub = new Element("pubsub", new String[] { "xmlns" },
																				new String[] {
																					"http://jabber.org/protocol/pubsub" });

				resultIq.getElement().addChild(resPubsub);

				Element resPublish = new Element("publish", new String[] { "node" },
																				 new String[] { nodeName });

				resPubsub.addChild(resPublish);
				for (Element item : itemsToSend) {
					String id = item.getAttributeStaticStr("id");

					if (id == null) {
						id = Utils.createUID();

						// throw new PubSubException(Authorization.BAD_REQUEST,
						// PubSubErrorCondition.ITEM_REQUIRED);
						item.setAttribute("id", id);
					}
					resPublish.addChild(new Element("item", new String[] { "id" },
																					new String[] { id }));
				}
			}

			final Element items = new Element("items", new String[] { "node" },
																				new String[] { nodeName });

			items.addChildren(itemsToSend);
			result.addAll(prepareNotification(items,	 packet.getStanzaTo(), nodeName,
																				this.repository.getNodeConfig(toJid, nodeName),
																				nodeAffiliations, nodeSubscriptions));

			List<String> parents = getParents(toJid, nodeName);

			if ((parents != null) && (parents.size() > 0)) {
				for (String collection : parents) {
					Map<String, String> headers = new HashMap<String, String>();

					headers.put("Collection", collection);

					AbstractNodeConfig colNodeConfig    = this.repository.getNodeConfig(toJid, collection);
					ISubscriptions colNodeSubscriptions =
						this.repository.getNodeSubscriptions(toJid, collection);
					IAffiliations colNodeAffiliations =
						this.repository.getNodeAffiliations(toJid, collection);

					result.addAll(prepareNotification(items, packet.getStanzaTo(),
																						nodeName, headers, colNodeConfig,
																						colNodeAffiliations, colNodeSubscriptions));
				}
			}
			if (leafNodeConfig.isPersistItem()) {
				IItems nodeItems = repository.getNodeItems(toJid, nodeName);

				for (Element item : itemsToSend) {
					final String id = item.getAttributeStaticStr("id");

					nodeItems.writeItem(System.currentTimeMillis(), id,
															element.getAttributeStaticStr("from"), item);
				}
				if (leafNodeConfig.getMaxItems() != null) {
					trimItems(nodeItems, leafNodeConfig.getMaxItems());
				}
			}

			return result;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	// ~--- inner classes
	// --------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param nodeItems
	 * @param maxItems
	 *
	 * @throws RepositoryException
	 */
	public void trimItems(final IItems nodeItems, final Integer maxItems)
					throws RepositoryException {
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

	//~--- inner classes --------------------------------------------------------

	private static class Item {
		final String id;
		final Date updateDate;

		//~--- constructors -------------------------------------------------------

		// ~--- constructors
		// -------------------------------------------------------
		Item(String id, Date date) {
			this.updateDate = date;
			this.id         = id;
		}
	}
}



// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com


//~ Formatted in Tigase Code Convention on 13/02/20
