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
import tigase.db.UserRepository;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.util.JIDUtils;
import tigase.xml.Element;

public class PEPPublisherModule extends PublishItemModule {

	private static final Criteria CRIT_PUBLISH = ElementCriteria.nameType("iq", "set").add(ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(
			ElementCriteria.name("publish", new String[] { "node" }, new String[] { "http://jabber.org/protocol/tune" }));

	public PEPPublisherModule(PubSubConfig config, UserRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_PUBLISH;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element publish = pubSub.getChild("publish");

		List<Element> result = new ArrayList<Element>();

		String jid = element.getAttribute("from");

		try {
			result.add(createResultIQ(element));

			String[] roster = repository.getSubnodes(JIDUtils.getNodeID(jid), "roster");
			for (String tojid : roster) {
				Element notification = createNotification(publish, jid, tojid);
				result.add(notification);
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		return result;
	}

}
