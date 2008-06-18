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
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.PubSubRepository;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class PublishItemModule extends AbstractModule {

	private static final Criteria CRIT_PUBLISH = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("publish"));

	protected PubSubConfig config;

	protected PubSubRepository repository;

	public PublishItemModule(PubSubConfig config, PubSubRepository pubsubRepository) {
		this.repository = pubsubRepository;
		this.config = config;
	}

	protected Element createNotification(final Element publish, final String fromJID, final String toJID) {
		Element message = new Element("message");
		message.setAttribute("from", fromJID);
		message.setAttribute("to", toJID);

		String nodeName = publish.getAttribute("node");

		Element event = new Element("event", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub#event" });
		message.addChild(event);

		Element items = new Element("items", new String[] { "node" }, new String[] { nodeName });
		event.addChild(items);

		for (Element si : publish.getChildren()) {
			if (!"item".equals(si.getName())) {
				continue;
			}
			items.addChild(si);
		}

		return message;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_PUBLISH;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element publish = pubSub.getChild("publish");

		final String nodeName = publish.getAttribute("node");

		try {

			String tmp = repository.getOwnerJid(nodeName);
			if (tmp == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}

			// TODO 7.1.3.1 Insufficient Privileges
			// TODO 7.1.3.2 Item Publication Not Supported
			// TODO 7.1.3.3 Node Does Not Exist
			// TODO 7.1.3.4 Payload Too Big
			// TODO 7.1.3.5 Bad Payload
			// TODO 7.1.3.6 Request Does Not Match Configuration

			List<Element> result = new ArrayList<Element>();
			result.add(createResultIQ(element));

			for (String jid : repository.getSubscribersJid(nodeName)) {
				final String jidTO = jid;
				final String jidFrom = element.getAttribute("to");
				Element notification = createNotification(publish, jidFrom, jidTO);
				result.add(notification);
			}

			// // XXX

			return result;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

}
