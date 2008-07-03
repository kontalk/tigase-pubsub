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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.Affiliation;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class PublishItemModule extends AbstractModule {

	private static final Criteria CRIT_PUBLISH = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("publish"));

	private long idCounter = 0;

	public PublishItemModule(PubSubConfig config, IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	@Override
	public String[] getFeatures() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_PUBLISH;
	}

	protected List<String> getParents(final String nodeName) throws RepositoryException {
		ArrayList<String> result = new ArrayList<String>();
		String cn = repository.getCollectionOf(nodeName);
		while (cn != null && !"".equals(cn)) {
			result.add(cn);
			cn = repository.getCollectionOf(cn);
		}
		;
		return result;
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

	public List<Element> prepareNotification(Element itemToSend, final String jidFrom, final String publisherNodeName)
			throws RepositoryException {
		ArrayList<Element> x = new ArrayList<Element>();
		x.add(itemToSend);
		return prepareNotification(x, jidFrom, publisherNodeName, null);
	}

	public List<Element> prepareNotification(Element itemToSend, final String jidFrom, final String publisherNodeName,
			final Map<String, String> headers) throws RepositoryException {
		ArrayList<Element> x = new ArrayList<Element>();
		x.add(itemToSend);
		return prepareNotification(x, jidFrom, publisherNodeName, headers);
	}

	public List<Element> prepareNotification(final List<Element> itemsToSend, final String jidFrom, final String publisherNodeName,
			final Map<String, String> headers) throws RepositoryException {
		String[] subscribers = getActiveSubscribers(publisherNodeName);
		return prepareNotification(subscribers, itemsToSend, jidFrom, publisherNodeName, headers);
	}

	public List<Element> prepareNotification(final String[] subscribers, final List<Element> itemsToSend, final String jidFrom,
			final String publisherNodeName, final Map<String, String> headers) {
		ArrayList<Element> result = new ArrayList<Element>();
		for (String jid : subscribers) {
			Element message = new Element("message", new String[] { "from", "to", "id" }, new String[] { jidFrom, jid,
					String.valueOf(++this.idCounter) });
			Element event = new Element("event", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/pubsub#event" });

			for (Element item : itemsToSend) {
				event.addChild(item);
			}
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

			NodeType nodeType = repository.getNodeType(nodeName);
			if (nodeType == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			} else if (nodeType == NodeType.collection) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported", "publish"));
			}

			final String[] allSubscribers = repository.getSubscribersJid(nodeName);
			final String publisherJid = findBestJid(allSubscribers, element.getAttribute("from"));

			Affiliation affiliation = repository.getSubscriberAffiliation(nodeName, publisherJid);

			if (affiliation != Affiliation.owner && affiliation != Affiliation.publisher) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			LeafNodeConfig nodeConfig = (LeafNodeConfig) repository.getNodeConfig(nodeName);

			List<Element> itemsToSend = makeItemsToSend(publish);

			if (nodeConfig.isPersistItem()) {
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

			result.addAll(prepareNotification(items, element.getAttribute("to"), nodeName, null));
			List<String> parents = getParents(nodeName);
			if (parents != null && parents.size() > 0) {
				for (String collection : parents) {
					Map<String, String> headers = new HashMap<String, String>();
					headers.put("Collection", collection);
					result.addAll(prepareNotification(items, element.getAttribute("to"), nodeName, headers));
				}
			}

			if (nodeConfig.isPersistItem()) {
				for (Element item : itemsToSend) {
					final String id = item.getAttribute("id");
					repository.writeItem(nodeName, System.currentTimeMillis(), id, element.getAttribute("from"), item);
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
}
