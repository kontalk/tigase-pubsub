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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class PublishItemModule extends AbstractModule {

	private static class Item {
		final String id;
		final Date updateDate;

		Item(String id, Date date) {
			this.updateDate = date;
			this.id = id;
		}
	}

	private static final Criteria CRIT_PUBLISH = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("publish"));

	public final static String[] SUPPORTED_PEP_XMLNS = { "http://jabber.org/protocol/mood", "http://jabber.org/protocol/geoloc",
			"http://jabber.org/protocol/activity", "http://jabber.org/protocol/tune" };

	private long idCounter = 0;;

	private final Set<String> pepNodes = new HashSet<String>();

	private final PresenceCollectorModule presenceCollector;

	private final XsltTool xslTransformer;

	public PublishItemModule(PubSubConfig config, IPubSubRepository pubsubRepository, XsltTool xsltTool,
			PresenceCollectorModule presenceCollector) {
		super(config, pubsubRepository);
		this.xslTransformer = xsltTool;
		this.presenceCollector = presenceCollector;
		for (String xmlns : SUPPORTED_PEP_XMLNS) {
			pepNodes.add(xmlns);
		}
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#publish", };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_PUBLISH;
	}

	protected List<String> getParents(final String nodeName) throws RepositoryException {
		ArrayList<String> result = new ArrayList<String>();
		AbstractNodeConfig nodeConfig = repository.getNodeConfig(nodeName);
		String cn = nodeConfig.getCollection();
		while (cn != null && !"".equals(cn)) {
			result.add(cn);
			AbstractNodeConfig nc = repository.getNodeConfig(cn);
			cn = nc.getCollection();
		}
		;
		return result;
	}

	protected String[] getValidBuddies(String jid) throws RepositoryException {
		ArrayList<String> result = new ArrayList<String>();
		String[] rosterJids = this.repository.getUserRoster(jid);
		if (rosterJids != null)
			for (String j : rosterJids) {
				String sub = this.repository.getBuddySubscription(jid, j);
				if (sub != null && (sub.equals("both") || sub.equals("from"))) {
					result.add(j);
				}
			}

		return result.toArray(new String[] {});
	}

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

	private List<Element> pepProcess(final Element element, final Element pubSub, final Element publish) throws RepositoryException {
		final String senderJid = element.getAttribute("from");
		final Element item = publish.getChild("item");

		final Element items = new Element("items", new String[] { "node" }, new String[] { publish.getAttribute("node") });
		items.addChild(item);

		String[] subscribers = getValidBuddies(JIDUtils.getNodeID(senderJid));
		List<Element> result = prepareNotification(subscribers, items, senderJid, null, publish.getAttribute("node"), null);
		result.add(createResultIQ(element));
		result.addAll(prepareNotification(new String[] { senderJid }, items, senderJid, null, publish.getAttribute("node"), null));

		return result;
	}

	public List<Element> prepareNotification(Element itemToSend, final String jidFrom, final String publisherNodeName,
			AbstractNodeConfig nodeConfig, IAffiliations nodeAffiliations, ISubscriptions nodesSubscriptions)
			throws RepositoryException {
		return prepareNotification(itemToSend, jidFrom, publisherNodeName, null, nodeConfig, nodeAffiliations, nodesSubscriptions);
	}

	public List<Element> prepareNotification(final Element itemToSend, final String jidFrom, final String publisherNodeName,
			final Map<String, String> headers, AbstractNodeConfig nodeConfig, IAffiliations nodeAffiliations,
			ISubscriptions nodesSubscriptions) throws RepositoryException {
		String[] subscribers = getActiveSubscribers(nodeAffiliations, nodesSubscriptions);
		if (nodeConfig.isDeliverPresenceBased()) {
			List<String> s = new ArrayList<String>();
			for (String jid : subscribers) {
				for (String subjid : this.presenceCollector.getAllAvailableResources(jid)) {
					s.add(subjid);
				}
			}
			subscribers = s.toArray(new String[] {});
		}

		return prepareNotification(subscribers, itemToSend, jidFrom, nodeConfig, publisherNodeName, headers);
	}

	public List<Element> prepareNotification(final String[] subscribers, final Element itemToSend, final String jidFrom,
			AbstractNodeConfig nodeConfig, final String publisherNodeName, final Map<String, String> headers) {
		ArrayList<Element> result = new ArrayList<Element>();

		List<Element> body = null;

		if (this.xslTransformer != null && nodeConfig != null) {
			try {
				body = this.xslTransformer.transform(itemToSend, nodeConfig);
			} catch (Exception e) {
				body = null;
				log.log(Level.WARNING, "Problem with generating BODY", e);
			}
		}

		for (String jid : subscribers) {
			Element message = new Element("message", new String[] { "from", "to", "id" }, new String[] { jidFrom, jid,
					String.valueOf(++this.idCounter) });
			if (body != null) {
				message.addChildren(body);
			}
			Element event = new Element("event", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/pubsub#event" });

			event.addChild(itemToSend);
			message.addChild(event);

			if (headers != null && headers.size() > 0) {
				Element headElem = new Element("headers", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/shim" });
				for (Entry<String, String> entry : headers.entrySet()) {
					Element h = new Element("header", entry.getValue(), new String[] { "name" }, new String[] { entry.getKey() });
					headElem.addChild(h);
				}
				message.addChild(headElem);
			}
			result.add(message);
		}

		return result;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element publish = pubSub.getChild("publish");

		final String nodeName = publish.getAttribute("node");

		try {
			if (isPEPNodeName(nodeName)) {
				return pepProcess(element, pubSub, publish);
			}

			AbstractNodeConfig nodeConfig = repository.getNodeConfig(nodeName);
			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			} else if (nodeConfig.getNodeType() == NodeType.collection) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported", "publish"));
			}
			IAffiliations nodeAffiliations = repository.getNodeAffiliations(nodeName);

			final UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(element.getAttribute("from"));

			if (!senderAffiliation.getAffiliation().isPublishItem()) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			LeafNodeConfig leafNodeConfig = (LeafNodeConfig) nodeConfig;

			List<Element> itemsToSend = makeItemsToSend(publish);

			if (leafNodeConfig.isPersistItem()) {
				// checking ID
				for (Element item : itemsToSend) {
					String id = item.getAttribute("id");
					if (id == null)
						throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.ITEM_REQUIRED);
				}
			}

			// TODO 7.1.3.1 Insufficient Privileges
			// TODO 7.1.3.2 Item Publication Not Supported
			// TODO 7.1.3.3 Node Does Not Exist
			// TODO 7.1.3.4 Payload Too Big
			// TODO 7.1.3.5 Bad Payload
			// TODO 7.1.3.6 Request Does Not Match Configuration

			List<Element> result = new ArrayList<Element>();
			result.add(createResultIQ(element));

			final Element items = new Element("items", new String[] { "node" }, new String[] { nodeName });
			items.addChildren(itemsToSend);

			final ISubscriptions nodeSubscriptions = repository.getNodeSubscriptions(nodeName);

			result.addAll(prepareNotification(items, element.getAttribute("to"), nodeName, this.repository.getNodeConfig(nodeName),
					nodeAffiliations, nodeSubscriptions));
			List<String> parents = getParents(nodeName);
			if (parents != null && parents.size() > 0) {
				for (String collection : parents) {
					Map<String, String> headers = new HashMap<String, String>();
					headers.put("Collection", collection);
					AbstractNodeConfig colNodeConfig = this.repository.getNodeConfig(collection);
					ISubscriptions colNodeSubscriptions = this.repository.getNodeSubscriptions(collection);
					IAffiliations colNodeAffiliations = this.repository.getNodeAffiliations(collection);
					result.addAll(prepareNotification(items, element.getAttribute("to"), nodeName, headers, colNodeConfig,
							colNodeAffiliations, colNodeSubscriptions));
				}
			}

			if (leafNodeConfig.isPersistItem()) {
				for (Element item : itemsToSend) {
					final String id = item.getAttribute("id");
					repository.writeItem(nodeName, System.currentTimeMillis(), id, element.getAttribute("from"), item);
				}
				if (leafNodeConfig.getMaxItems() != null) {
					trimItems(nodeName, leafNodeConfig.getMaxItems());
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

	public void trimItems(final String nodeName, final Integer maxItems) throws RepositoryException {
		final String[] ids = this.repository.getItemsIds(nodeName);
		if (ids == null || ids.length <= maxItems)
			return;
		final ArrayList<Item> items = new ArrayList<Item>();
		for (String id : ids) {
			Date updateDate = this.repository.getItemUpdateDate(nodeName, id);
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
			this.repository.deleteItem(nodeName, it.id);
		}
	}

}
