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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import tigase.adhoc.AdHocResponse.State;
import tigase.util.SimpleCache;
import tigase.xml.Element;

public class AdHocCommandManager {

	private final Map<String, AdHocCommand> commands = new HashMap<String, AdHocCommand>();

	private final SimpleCache<String, AdHocSession> sessions = new SimpleCache<String, AdHocSession>(100, 10 * 1000);

	public Collection<AdHocCommand> getAllCommands() {
		return this.commands.values();
	}

	public Element process(Element element) throws AdHocCommandException {
		final String senderJid = element.getAttribute("from");
		final Element command = element.getChild("command", "http://jabber.org/protocol/commands");
		final String node = command.getAttribute("node");
		final String action = command.getAttribute("action");
		final String sessionId = command.getAttribute("sessionid");

		AdHocCommand adHocCommand = this.commands.get(node);
		if (adHocCommand == null) {

		} else {
			Element iqResult = new Element("iq", new String[] { "from", "to", "id", "type" }, new String[] {
					element.getAttribute("to"), senderJid, element.getAttribute("id"), "result" });

			State currentState = null;

			final AdhHocRequest request = new AdhHocRequest(element, command, node, senderJid, action, sessionId);
			final AdHocResponse response = new AdHocResponse(sessionId, currentState);
			final AdHocSession session = sessionId == null ? new AdHocSession() : this.sessions.get(sessionId);

			adHocCommand.execute(request, response);

			Element commandResult = new Element("command", new String[] { "xmlns", "node", }, new String[] {
					"http://jabber.org/protocol/commands", node });

			commandResult.addAttribute("status", response.getNewState().name());

			if (response.getCurrentState() == null && response.getNewState() == State.executing) {
				this.sessions.put(response.getSessionid(), session);
			} else if (response.getSessionid() != null
					&& (response.getNewState() == State.canceled || response.getNewState() == State.completed)) {
				this.sessions.remove(response.getSessionid());
			}

			if (response.getSessionid() != null) {
				commandResult.addAttribute("sessionid", response.getSessionid());
			}

			for (Element r : response.getElements()) {
				commandResult.addChild(r);
			}

			iqResult.addChild(commandResult);
			return iqResult;
		}
		return null;
	}

	public void registerCommand(AdHocCommand command) {
		this.commands.put(command.getNode(), command);
	}

}
