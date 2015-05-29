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
import java.util.Arrays;
import java.util.Collection;
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
import java.util.logging.Logger;

import tigase.component2.PacketWriter;
import tigase.component2.eventbus.Event;
import tigase.component2.eventbus.EventHandler;
import tigase.component2.eventbus.EventType;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.AccessModel;
import tigase.pubsub.Affiliation;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.PublisherModel;
import tigase.pubsub.SendLastPublishedItem;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.PresenceCollectorModule.CapsChangeHandler;
import tigase.pubsub.modules.PresenceCollectorModule.CapsChangeHandler.CapsChangeEvent;
import tigase.pubsub.modules.PresenceCollectorModule.PresenceChangeHandler;
import tigase.pubsub.modules.PresenceCollectorModule.PresenceChangeHandler.PresenceChangeEvent;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;

import tigase.server.Message;
import tigase.server.Packet;

import tigase.xml.Element;

import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;
import tigase.xmpp.impl.roster.RosterAbstract.SubscriptionType;
import tigase.xmpp.impl.roster.RosterElement;

import tigase.util.DateTimeFormatter;

import java.util.Calendar;

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
	}

	public interface ItemPublishedHandler extends EventHandler {

		public static class ItemPublishedEvent extends Event<ItemPublishedHandler> {

			public static final EventType<ItemPublishedHandler> TYPE = new EventType<ItemPublishedHandler>();

			private final Collection<Element> items;

			private final String node;

			private final BareJID serviceJID;

			public ItemPublishedEvent(BareJID serviceJID, String node, Collection<Element> items) {
				super(TYPE);
				this.node = node;
				this.serviceJID = serviceJID;
				this.items = items;
			}

			@Override
			protected void dispatch(ItemPublishedHandler handler) {
				handler.onItemPublished(serviceJID, node, items);
			}

			/**
			 * @return the items
			 */
			public Collection<Element> getItems() {
				return items;
			}

			public String getNode() {
				return node;
			}

			public BareJID getServiceJID() {
				return serviceJID;
			}

		}

		void onItemPublished(BareJID serviceJID, String node, Collection<Element> items);
	};

	private static final Criteria CRIT_PUBLISH = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("publish"));

	public final static String AMP_XMLNS = "http://jabber.org/protocol/amp";

	public final static String[] SUPPORTED_PEP_XMLNS = { "http://jabber.org/protocol/mood",
		"http://jabber.org/protocol/geoloc", "http://jabber.org/protocol/activity", "http://jabber.org/protocol/tune" };

	private final CapsChangeHandler capsChangeHandler = new CapsChangeHandler() {

		@Override
		public void onCapsChange(BareJID serviceJid, JID buddyJid, String[] newCaps, String[] oldCaps, Set<String> newFeatures) {
			if (newFeatures == null || newFeatures.isEmpty() || !config.isSendLastPublishedItemOnPresence())
				return;

			// if we have new features we need to check if there are nodes for
			// which
			// we need to send notifications due to +notify feature
			for (String feature : newFeatures) {
				if (!feature.endsWith("+notify"))
					continue;
				String nodeName = feature.substring(0, feature.length() - "+notify".length());

				try {
					AbstractNodeConfig nodeConfig = config.getPubSubRepository().getNodeConfig(serviceJid, nodeName);
					if (nodeConfig != null
							&& nodeConfig.getSendLastPublishedItem() == SendLastPublishedItem.on_sub_and_presence) {
						publishLastItem(serviceJid, nodeConfig, buddyJid);
					}
				} catch (RepositoryException ex) {
					log.log(Level.WARNING,
							"Exception while sending last published item on on_sub_and_presence with CAPS filtering");
				}
			}
		}

	};

	private final DateTimeFormatter dtf = new DateTimeFormatter();

	private final LeafNodeConfig defaultPepNodeConfig;
	private long idCounter = 0;

	private final Set<String> pepNodes = new HashSet<String>();

	private final PresenceChangeHandler presenceChangeHandler = new PresenceChangeHandler() {

		@Override
		public void onPresenceChange(Packet packet) {
			// PEP services are using CapsChangeEvent - but we should process
			// this here as well
			// as on PEP service we can have some nodes which have there types
			// of subscription
			if (packet.getStanzaTo() == null) // ||
				// packet.getStanzaTo().getLocalpart()
				// != null)
				return;
			if (!config.isSendLastPublishedItemOnPresence())
				return;
			if (packet.getType() == null || packet.getType() == StanzaType.available) {
				BareJID serviceJid = packet.getStanzaTo().getBareJID();
				JID userJid = packet.getStanzaFrom();
				try {
					// sending last published items for subscribed nodes
					Map<String, UsersSubscription> subscrs = config.getPubSubRepository().getUserSubscriptions(serviceJid,
							userJid.getBareJID());
					log.log(Level.FINEST, "Sending last published items for subscribed nodes: {0}", subscrs);
					for (Map.Entry<String, UsersSubscription> e : subscrs.entrySet()) {
						if (e.getValue().getSubscription() != Subscription.subscribed)
							continue;
						String nodeName = e.getKey();
						AbstractNodeConfig nodeConfig = config.getPubSubRepository().getNodeConfig(serviceJid, nodeName);
						if (nodeConfig.getSendLastPublishedItem() != SendLastPublishedItem.on_sub_and_presence)
							continue;
						publishLastItem(serviceJid, nodeConfig, userJid);
					}
				} catch (RepositoryException ex) {
					Logger.getLogger(PublishItemModule.class.getName()).log(Level.SEVERE, null, ex);
				}

			}
		}

	};

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
		// creating default config for autocreate PEP nodes
		this.defaultPepNodeConfig = new LeafNodeConfig("default-pep");
		defaultPepNodeConfig.setValue("pubsub#access_model", AccessModel.presence.name());
		defaultPepNodeConfig.setValue("pubsub#presence_based_delivery", true);
		defaultPepNodeConfig.setValue("pubsub#send_last_published_item", "on_sub_and_presence");

		this.config.getEventBus().addHandler(CapsChangeEvent.TYPE, capsChangeHandler);
		this.config.getEventBus().addHandler(PresenceChangeEvent.TYPE, presenceChangeHandler);
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

	public AbstractNodeConfig ensurePepNode(BareJID toJid, String nodeName, BareJID ownerJid) throws PubSubException {
		AbstractNodeConfig nodeConfig;
		try {
			IPubSubRepository repo = getRepository();
			nodeConfig = repo.getNodeConfig(toJid, nodeName);
		} catch (RepositoryException ex) {
			throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, "Error occured during autocreation of node", ex);
		}

		if (nodeConfig != null)
			return nodeConfig;
			
		return createPepNode(toJid, nodeName, ownerJid);
	}
	
	private AbstractNodeConfig createPepNode(BareJID toJid, String nodeName, BareJID ownerJid) 
			throws PubSubException {
		if (!toJid.equals(ownerJid))
			throw new PubSubException(Authorization.FORBIDDEN);
			
		AbstractNodeConfig nodeConfig;
		try {
			IPubSubRepository repo = getRepository();
			nodeConfig = new LeafNodeConfig(nodeName, defaultPepNodeConfig);
			repo.createNode(toJid, nodeName, ownerJid, nodeConfig, NodeType.leaf, "");
			nodeConfig = repo.getNodeConfig(toJid, nodeName);
			IAffiliations nodeaAffiliations = repo.getNodeAffiliations(toJid, nodeName);
			nodeaAffiliations.addAffiliation(ownerJid, Affiliation.owner);
			ISubscriptions nodeaSubscriptions = repo.getNodeSubscriptions(toJid, nodeName);
			nodeaSubscriptions.addSubscriberJid(toJid, Subscription.subscribed);
			repo.update(toJid, nodeName, nodeaAffiliations);
			repo.addToRootCollection(toJid, nodeName);
			log.log(Level.FINEST, "Created new PEP node: {0}, conf: {1}, aff: {2}, subs: {3} ",
														new Object[] {nodeName, nodeConfig, nodeaAffiliations, nodeaSubscriptions});
		} catch (RepositoryException ex) {
			throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR, "Error occured during autocreation of node", ex);
		}
		return nodeConfig;
	}
	
	public void doPublishItems(BareJID serviceJID, String nodeName, LeafNodeConfig leafNodeConfig,
			IAffiliations nodeAffiliations, ISubscriptions nodeSubscriptions, String publisher, List<Element> itemsToSend)
					throws RepositoryException {
		getEventBus().fire(new ItemPublishedHandler.ItemPublishedEvent(serviceJID, nodeName, itemsToSend));

		final Element items = new Element("items", new String[] { "node" }, new String[] { nodeName });

		items.addChildren(itemsToSend);
		sendNotifications(items, JID.jidInstance(serviceJID), nodeName,
				this.getRepository().getNodeConfig(serviceJID, nodeName), nodeAffiliations, nodeSubscriptions);

		List<String> parents = getParents(serviceJID, nodeName);

			log.log(Level.FINEST, "Publishing item: {0}, node: {1}, conf: {2}, aff: {3}, subs: {4} ",
														new Object[] {items, nodeName, leafNodeConfig, nodeAffiliations, nodeSubscriptions});

			if ((parents != null) && (parents.size() > 0)) {
			for (String collection : parents) {
				Map<String, String> headers = new HashMap<String, String>();

				headers.put("Collection", collection);

				AbstractNodeConfig colNodeConfig = this.getRepository().getNodeConfig(serviceJID, collection);
				ISubscriptions colNodeSubscriptions = this.getRepository().getNodeSubscriptions(serviceJID, collection);
				IAffiliations colNodeAffiliations = this.getRepository().getNodeAffiliations(serviceJID, collection);

				sendNotifications(items, JID.jidInstance(serviceJID), nodeName, headers, colNodeConfig, colNodeAffiliations,
						colNodeSubscriptions);
			}
		}
		if (leafNodeConfig.isPersistItem()) {
			IItems nodeItems = getRepository().getNodeItems(serviceJID, nodeName);

			for (Element item : itemsToSend) {
				final String id = item.getAttributeStaticStr("id");

				if ( !config.isPepRemoveEmptyGeoloc() ){
					nodeItems.writeItem( System.currentTimeMillis(), id, publisher, item );
				} else {
					Element geoloc = item.findChildStaticStr( new String[] { "item", "geoloc" } );
					if ( geoloc != null && ( geoloc.getChildren() == null || geoloc.getChildren().size() == 0 ) ){
						nodeItems.deleteItem( id );
					} else {
						nodeItems.writeItem( System.currentTimeMillis(), id, publisher, item );
					}
				}
			}
			if (leafNodeConfig.getMaxItems() != null) {
				trimItems(nodeItems, leafNodeConfig.getMaxItems());
			}
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
		Map<BareJID, RosterElement> rosterJids = this.getRepository().getUserRoster(id);

			if (rosterJids != null) {
			for (Entry<BareJID, RosterElement> e : rosterJids.entrySet()) {
				SubscriptionType sub = e.getValue().getSubscription();

				if (sub == SubscriptionType.both || sub == SubscriptionType.from || sub == SubscriptionType.from_pending_out) {
					result.add(JID.jidInstance(e.getKey()));
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
		if (config.isPepPeristent())
			return false;

		return this.pepNodes.contains(nodeName);
	}

	private List<Element> makeItemsToSend(Element publish) {
		List<Element> items = new ArrayList<Element>();

		for (Element si : publish.getChildren()) {
			if (!"item".equals(si.getName())) {
				continue;
			}
			String expireAttr = si.getAttributeStaticStr( "expire-at");
			if ( expireAttr != null ){
				Calendar parseDateTime = dtf.parseDateTime( expireAttr );
				if ( null != parseDateTime ){
					si.setAttribute( "expire-at", dtf.formatDateTime( parseDateTime.getTime() ) );
				} else {
					si.removeAttribute( "expire-at" );
				}
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
		sendNotifications(subscribers, items, senderJid, null, publish.getAttributeStaticStr("node"), null);

		packetWriter.write(packet.okResult((Element) null, 0));
		sendNotifications(new JID[] { senderJid }, items, senderJid, null, publish.getAttributeStaticStr("node"), null);
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
				if (packet.getStanzaTo().getLocalpart() == null || !config.isPepPeristent()) {
					throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
				} else {
					// this is PubSub service for particular user - we should autocreate node
					nodeConfig = createPepNode(toJid, nodeName, packet.getStanzaFrom().getBareJID());
				}
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

			doPublishItems(toJid, nodeName, leafNodeConfig, nodeAffiliations, nodeSubscriptions,
					element.getAttributeStaticStr("from"), itemsToSend);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	public void publish(BareJID serviceJid, String publisher, String nodeName, Element item) throws RepositoryException {
		AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJid, nodeName);
		IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(serviceJid, nodeName);
		ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(serviceJid, nodeName);

		doPublishItems(serviceJid, nodeName, (LeafNodeConfig) nodeConfig, nodeAffiliations, nodeSubscriptions, publisher,
				Collections.singletonList(item));
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
			items.addChild(payload);

			sendNotifications(new JID[] { destinationJID }, items, JID.jidInstance(serviceJid), nodeConfig,
					nodeConfig.getNodeName(), null);
		}

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
		sendNotifications(itemToSend, jidFrom, publisherNodeName, null, nodeConfig, nodeAffiliations, nodesSubscriptions);
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

		log.log(Level.FINEST, "Sending notifications[1] item: {0}, node: {1}, conf: {2}, aff: {3}, subs: {4} ",
													new Object[] {itemToSend, publisherNodeName, nodeConfig,
																																			 nodeAffiliations, nodesSubscriptions});

		if (nodeConfig.isPresenceExpired()) {
			Iterator<JID> it = tmp.iterator();

			while (it.hasNext()) {
				final JID jid = it.next();
				boolean available = this.presenceCollector.isJidAvailable(jidFrom.getBareJID(), jid.getBareJID());
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
				s.addAll(this.presenceCollector.getAllAvailableResources(jidFrom.getBareJID(), jid.getBareJID()));
			}

			// for pubsub service for user accounts we need dynamic
			// subscriptions based on presence
			if (jidFrom.getLocalpart() != null) {
				switch (nodeConfig.getNodeAccessModel()) {
				case open:
				case presence:
					s.addAll(this.presenceCollector.getAllAvailableJidsWithFeature(jidFrom.getBareJID(),
							nodeConfig.getNodeName() + "+notify"));
					break;
				case roster:
					String[] allowedGroups = nodeConfig.getRosterGroupsAllowed();
					Arrays.sort(allowedGroups);
					List<JID> jids = this.presenceCollector.getAllAvailableJidsWithFeature(jidFrom.getBareJID(),
							nodeConfig.getNodeName() + "+notify");
					if (!jids.isEmpty() && (allowedGroups != null && allowedGroups.length > 0)) {
						Map<BareJID, RosterElement> roster = this.getRepository().getUserRoster(jidFrom.getBareJID());
						Iterator<JID> it = jids.iterator();
						for (JID jid : jids) {
							RosterElement re = roster.get(jid.getBareJID());
							if (re == null) {
								it.remove();
								continue;
							}
							boolean notInGroups = true;
							String[] groups = re.getGroups();
							if (groups != null) {
								for (String group : groups) {
									notInGroups &= Arrays.binarySearch(allowedGroups, group) < 0;
								}
							}
							if (notInGroups)
								it.remove();
						}
					}
					break;
				default:
					break;
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

		log.log(Level.FINEST, "Sending notifications[2] item: {0}, node: {1}, conf: {2}, subs: {3} ",
													new Object[] {itemToSend, publisherNodeName, nodeConfig, Arrays.asList( subscribers )  });

		if ((this.xslTransformer != null) && (nodeConfig != null)) {
			try {
				body = this.xslTransformer.transform(itemToSend, nodeConfig);
			} catch (Exception e) {
				body = null;
				log.log(Level.WARNING, "Problem with generating BODY", e);
			}
		}
		for (JID jid : subscribers) {

			// in case of low memory we should slow down creation of response to
			// prevent OOM on high traffic node
			// maybe we should drop some notifications if we can not get enough
			// memory for n-th time?
			long lowMemoryDelay;
			while ((lowMemoryDelay = config.getDelayOnLowMemory()) != 0) {
				try {
					System.gc();
					Thread.sleep(lowMemoryDelay);
				} catch (Exception e) {
				}
			}

			Packet packet = Message.getMessage(jidFrom, jid, null, null, null, null, String.valueOf(++this.idCounter));
			Element message = packet.getElement();

			if (body != null) {
				message.addChildren(body);
			}

			Element event = new Element("event", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/pubsub#event" });

			event.addChild(itemToSend);
			String expireAttr = itemToSend.getAttributeStaticStr( new String[] {"items","item"}, "expire-at" );
			if (expireAttr != null ) {
				Element amp = new Element("amp");
				amp.setXMLNS( AMP_XMLNS );
				amp.addChild( new Element("rule",
						new String[] {"condition", "action", "value"},
						new String[] {"expire-at", "drop", expireAttr }));
				message.addChild( amp );
			}
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

			// we are adding notifications to outgoing queue instead temporary
			// list
			// of notifications to send, so before creating next packets other
			// threads
			// will be able to process first notifications and deliver them
			packetWriter.write(packet);
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
