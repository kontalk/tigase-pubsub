/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
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
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub.modules;

import java.util.ArrayList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.criteria.Or;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.Module;
import tigase.pubsub.exceptions.PubSubException;
import tigase.xml.Element;

public class XmppPingModule implements Module {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			new Or(ElementCriteria.name("ping", "http://www.xmpp.org/extensions/xep-0199.html#ns"), ElementCriteria.name(
					"ping", "urn:xmpp:ping")));

	@Override
	public String[] getFeatures() {
		return new String[] { "urn:xmpp:ping" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(Element iq, ElementWriter elementWriter) throws PubSubException {
		Element reposnse = new Element("iq", new String[] { "type", "from", "to", "id" }, new String[] { "result",
				iq.getAttribute("to"), iq.getAttribute("from"), iq.getAttribute("id") });
		List<Element> x = new ArrayList<Element>();
		x.add(reposnse);
		return x;
	}

}
