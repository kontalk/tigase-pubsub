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
import tigase.pubsub.Affiliation;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class PurgeItemsModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("purge"));

	private final PublishItemModule publishModule;

	public PurgeItemsModule(PubSubConfig config, IPubSubRepository pubsubRepository, PublishItemModule publishModule) {
		super(config, pubsubRepository);
		this.publishModule = publishModule;
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#purge-nodes" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element purge = pubSub.getChild("purge");
		final String nodeName = purge.getAttribute("node");

		try {
			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}
			NodeType nodeType = repository.getNodeType(nodeName);
			if (nodeType == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			} else if (nodeType == NodeType.collection) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
						"purge-nodes"));
			}

			final String[] allSubscribers = repository.getSubscriptions(nodeName);
			final String publisherJid = findBestJid(allSubscribers, element.getAttribute("from"));

			Affiliation affiliation = repository.getSubscriberAffiliation(nodeName, publisherJid);

			if (affiliation != Affiliation.owner && affiliation != Affiliation.publisher) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			LeafNodeConfig nodeConfig = (LeafNodeConfig) repository.getNodeConfig(nodeName);

			if (!nodeConfig.isPersistItem()) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
						"persistent-items"));
			}

			List<Element> result = new ArrayList<Element>();
			result.add(createResultIQ(element));

			String[] itemsToDelete = this.repository.getItemsIds(nodeName);
			result.addAll(publishModule.prepareNotification(
					new Element("purge", new String[] { "node" }, new String[] { nodeName }), element.getAttribute("to"), nodeName));
			log.info("Purging node " + nodeName);
			for (String id : itemsToDelete) {
				repository.deleteItem(nodeName, id);
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
