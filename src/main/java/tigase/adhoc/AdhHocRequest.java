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
package tigase.adhoc;

import tigase.xml.Element;

public class AdhHocRequest {

	private final String action;

	private final Element command;

	private final Element iq;

	private final String node;

	private final String sender;

	private final String sessionId;

	AdhHocRequest(Element iq, Element command, String node, String sender, String action, String sessionId) {
		super();
		this.iq = iq;
		this.command = command;
		this.node = node;
		this.action = action;
		this.sessionId = sessionId;
		this.sender = sender;
	}

	public String getAction() {
		return action;
	}

	public Element getCommand() {
		return command;
	}

	public Element getIq() {
		return iq;
	}

	public String getNode() {
		return node;
	}

	public String getSender() {
		return sender;
	}

	public String getSessionId() {
		return sessionId;
	}

}
