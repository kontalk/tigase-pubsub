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
package tigase.pubsub;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import tigase.xml.Element;

public abstract class AbstractModule implements Module {

	public static Element createResultIQ(Element iq) {
		return new Element("iq", new String[] { "type", "from", "to", "id" }, new String[] { "result", iq.getAttribute("to"), iq.getAttribute("from"),
				iq.getAttribute("id") });
	}

	public static List<Element> createResultIQArray(Element iq) {
		return makeArray(createResultIQ(iq));
	}

	public static List<Element> makeArray(Element... elements) {
		ArrayList<Element> result = new ArrayList<Element>();
		for (Element element : elements) {
			result.add(element);

		}
		return result;
	}

	protected Logger log = Logger.getLogger(this.getClass().getName());

}
