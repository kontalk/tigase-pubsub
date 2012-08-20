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
import tigase.pubsub.ElementWriter;
import tigase.pubsub.Module;
import tigase.pubsub.PubSubVersion;
import tigase.pubsub.exceptions.PubSubException;
import tigase.xml.Element;

public class JabberVersionModule implements Module {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("query", "jabber:iq:version"));

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(Element element, ElementWriter elementWriter) throws PubSubException {
		List<Element> result = new ArrayList<Element>();
		Element iq = new Element("iq", new String[] { "to", "from", "id", "type" }, new String[] {
				element.getAttribute("from"), element.getAttribute("to"), element.getAttribute("id"), "result" });
		Element query = new Element("query", new String[] { "xmlns" }, new String[] { "jabber:iq:version" });
		query.addChild(new Element("name", "Tigase PubSub"));
		query.addChild(new Element("version", PubSubVersion.getVersion()));
		query.addChild(new Element("os", System.getProperty("os.name") + "-" + System.getProperty("os.arch") + "-"
				+ System.getProperty("os.version") + ", " + System.getProperty("java.vm.name") + "-"
				+ System.getProperty("java.version") + " " + System.getProperty("java.vm.vendor")));

		iq.addChild(query);
		result.add(iq);

		return result;
	}

}
