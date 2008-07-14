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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.Authorization;

public class RetrieveItemsModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("items"));

	public RetrieveItemsModule(PubSubConfig config, IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	private List<String> extractItemsIds(final Element items) throws PubSubException {
		List<Element> il = items.getChildren();
		if (il == null || il.size() == 0)
			return null;
		final List<String> result = new ArrayList<String>();
		for (Element i : il) {
			final String id = i.getAttribute("id");
			if (!"item".equals(i.getName()) || id == null)
				throw new PubSubException(Authorization.BAD_REQUEST);
			result.add(id);
		}
		return result;
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#retrieve-items" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(final Element element) throws PubSubException {
		try {
			final Element pubsub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
			final Element items = pubsub.getChild("items");
			final String nodeName = items.getAttribute("node");
			final Integer maxItems = asInteger(items.getAttribute("max_items"));
			if (nodeName == null)
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);

			// XXX CHECK RIGHTS AUTH ETC

			List<String> requestedId = extractItemsIds(items);
			final Element iq = createResultIQ(element);
			final Element rpubsub = new Element("pubsub", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/pubsub" });
			final Element ritems = new Element("items", new String[] { "node" }, new String[] { nodeName });
			rpubsub.addChild(ritems);
			iq.addChild(rpubsub);
			if (requestedId == null) {
				String[] ids = this.repository.getItemsIds(nodeName);
				if (ids != null)
					requestedId = Arrays.asList(ids);
			}
			if (requestedId != null) {
				int c = 0;
				for (String id : requestedId) {
					Element item = this.repository.getItem(nodeName, id);
					if (item != null) {
						if (maxItems != null && (++c) > maxItems)
							break;
						ritems.addChild(item);
					}
				}
			}

			return makeArray(iq);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private Integer asInteger(String attribute) {
		if (attribute == null)
			return null;
		return Integer.parseInt(attribute);
	}

}
