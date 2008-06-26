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

import tigase.pubsub.AbstractModule;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.PubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;

public abstract class AbstractConfigCreateNode extends AbstractModule {

	protected final PubSubConfig config;

	protected final LeafNodeConfig defaultNodeConfig;

	protected final PubSubRepository repository;

	public AbstractConfigCreateNode(final PubSubConfig config, final PubSubRepository pubsubRepository,
			final LeafNodeConfig defaultNodeConfig) {
		this.repository = pubsubRepository;
		this.config = config;
		this.defaultNodeConfig = defaultNodeConfig;
	}

	protected List<Element> notifyCollectionChange(final String fromJID, final String baseNodeName, final String newNodeName,
			final String eventKind) throws RepositoryException {
		ArrayList<Element> result = new ArrayList<Element>();

		Element event = new Element("event", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub#event" });
		Element collection = new Element("collection", new String[] { "node" }, new String[] { baseNodeName });
		collection.addChild(new Element(eventKind, new String[] { "node" }, new String[] { newNodeName }));
		event.addChild(collection);

		String[] subscribers = repository.getSubscribersJid(baseNodeName);
		if (subscribers != null)
			for (String toJid : subscribers) {
				Element message = new Element("message", new String[] { "from", "to" }, new String[] { fromJID, toJid });
				message.addChild(event.clone());
				result.add(message);
			}
		return result;
	}
}
