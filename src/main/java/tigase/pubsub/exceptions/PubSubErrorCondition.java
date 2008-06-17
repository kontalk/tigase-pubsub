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
package tigase.pubsub.exceptions;

import tigase.xml.Element;

public class PubSubErrorCondition {

	public static final PubSubErrorCondition INVALID_JID = new PubSubErrorCondition("invalid-jid");  
	
	protected static final String XMLNS = "http://jabber.org/protocol/pubsub#errors";

	private final String condition;

	protected PubSubErrorCondition(String condition) {
		this.condition = condition;
	}

	public Element getElement() {
		Element result = new Element(condition);
		result.addAttribute("xmlns", XMLNS);
		
		return result;
	}
	
}
