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

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class DiscoverItemsModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("query", "http://jabber.org/protocol/disco#items"));

	public DiscoverItemsModule(PubSubConfig config, IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		try {
			final Element query = element.getChild("query", "http://jabber.org/protocol/disco#items");
			final String nodeName = query.getAttribute("node");
			final String senderJid = element.getAttribute("from");

			Element resultIq = createResultIQ(element);
			Element resultQuery = new Element("query", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/disco#items" });
			resultIq.addChild(resultQuery);
			log.finest("Asking about Items of node " + nodeName);
			if (nodeName == null) {
				String[] nodes = this.repository.getNodesList();
				if (nodes != null) {
					for (String node : nodes) {
						AbstractNodeConfig nodeConfig = this.repository.getNodeConfig(node);
						boolean allowed = (senderJid == null || nodeConfig == null) ? true : Utils.isAllowedDomain(senderJid,
								nodeConfig.getDomains());
						if (allowed) {
							String name = nodeConfig.getTitle();
							name = name == null || name.length() == 0 ? node : name;
							Element item = new Element("item", new String[] { "jid", "node", "name" }, new String[] {
									element.getAttribute("to"), node, name });
							resultQuery.addChild(item);
						} else {
							log.fine("User " + senderJid + " not allowed to see node '" + node + "'");
						}
					}
				}
			} else {
				AbstractNodeConfig nodeConfig = this.repository.getNodeConfig(nodeName);
				boolean allowed = (senderJid == null || nodeConfig == null) ? true : Utils.isAllowedDomain(senderJid,
						nodeConfig.getDomains());
				if (!allowed)
					throw new PubSubException(Authorization.FORBIDDEN);
				resultQuery.addAttribute("node", nodeName);
				String[] itemsId = repository.getItemsIds(nodeName);
				if (itemsId != null) {
					for (String itemId : itemsId) {
						resultQuery.addChild(new Element("item", new String[] { "jid", "name" }, new String[] {
								element.getAttribute("to"), itemId }));

					}
				}
			}
			return makeArray(resultIq);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}