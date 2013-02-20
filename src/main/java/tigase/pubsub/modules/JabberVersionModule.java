/*
 * JabberVersionModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
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
 */



package tigase.pubsub.modules;

//~--- non-JDK imports --------------------------------------------------------

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;

import tigase.pubsub.ElementWriter;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.Module;
import tigase.pubsub.PubSubVersion;

import tigase.xml.Element;

//~--- JDK imports ------------------------------------------------------------

import java.util.ArrayList;
import java.util.List;

/**
 * Class description
 *
 *
 * @version        Enter version here..., 13/02/20
 * @author         Enter your name here...
 */
public class JabberVersionModule
				implements Module {
	private static final Criteria CRIT = ElementCriteria.nameType("iq",
																				 "get").add(ElementCriteria.name("query",
																					 "jabber:iq:version"));

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return null;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param element
	 * @param elementWriter
	 *
	 * @return
	 *
	 * @throws PubSubException
	 */
	@Override
	public List<Element> process(Element element, ElementWriter elementWriter)
					throws PubSubException {
		List<Element> result = new ArrayList<Element>();
		Element iq           = new Element("iq", new String[] { "to", "from", "id", "type" },
																			 new String[] {
																				 element.getAttributeStaticStr("from"),
						element.getAttributeStaticStr("to"), element.getAttributeStaticStr("id"),
						"result" });
		Element query = new Element("query", new String[] { "xmlns" },
																new String[] { "jabber:iq:version" });

		query.addChild(new Element("name", "Tigase PubSub"));
		query.addChild(new Element("version", PubSubVersion.getVersion()));
		query.addChild(new Element("os",
															 System.getProperty("os.name") + "-" +
															 System.getProperty("os.arch") + "-" +
															 System.getProperty("os.version") + ", " +
															 System.getProperty("java.vm.name") + "-" +
															 System.getProperty("java.version") + " " +
															 System.getProperty("java.vm.vendor")));
		iq.addChild(query);
		result.add(iq);

		return result;
	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
