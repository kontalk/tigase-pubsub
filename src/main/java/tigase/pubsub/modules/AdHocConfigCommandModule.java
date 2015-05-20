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

import tigase.server.Packet;

import tigase.xmpp.Authorization;
import tigase.xmpp.JID;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocCommandManager;
import tigase.adhoc.AdHocScriptCommandManager;
import tigase.component2.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.xml.Element;

import java.util.ArrayList;
import java.util.List;

public class AdHocConfigCommandModule extends AbstractPubSubModule {

	private static final String[] COMMAND_PATH = { "iq", "command" };

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("command", "http://jabber.org/protocol/commands"));

	private final AdHocCommandManager commandsManager = new AdHocCommandManager();
	private AdHocScriptCommandManager scriptCommandManager;

	public AdHocConfigCommandModule(PubSubConfig config, PacketWriter packetWriter,
			AdHocScriptCommandManager scriptCommandManager) {
		super(config, packetWriter);
		this.scriptCommandManager = scriptCommandManager;
	}

	public List<Element> getCommandListItems(final JID senderJid, final JID toJid) {
		ArrayList<Element> commandsList = new ArrayList<Element>();
		for (AdHocCommand command : this.commandsManager.getAllCommands()) {
//			if (config.isAdmin(senderJid)) {
			if ( scriptCommandManager.canCallCommand( senderJid, command.getNode() ) ){
				commandsList.add(new Element("item", new String[] { "jid", "node", "name" }, new String[] { toJid.toString(),
						command.getNode(), command.getName() }));
			}
		}

		List<Element> scriptCommandsList = scriptCommandManager.getCommandListItems(senderJid, toJid);
		if (scriptCommandsList != null) {
			commandsList.addAll(scriptCommandsList);
		}
		return commandsList;
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/commands" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet packet) throws PubSubException {
		String node = packet.getAttributeStaticStr(COMMAND_PATH, "node");
		JID stanzaFrom = packet.getStanzaFrom();
		
		if ( !scriptCommandManager.canCallCommand( stanzaFrom, node ) ){
			throw new PubSubException(Authorization.NOT_AUTHORIZED,
					"You don't have enought privileges to execute the command");
		}

		if (commandsManager.hasCommand(node)) {
			try {
				final Packet result = this.commandsManager.process(packet);
				result.setXMLNS( Packet.CLIENT_XMLNS );
				packetWriter.write(result);
			} catch (AdHocCommandException e) {
				throw new PubSubException(e.getErrorCondition(), e.getMessage());
			}
		} else {
			final List<Packet> result = scriptCommandManager.process(packet);
			for ( Packet res : result) {
				res.setXMLNS( Packet.CLIENT_XMLNS );
			}
			packetWriter.write(result);
		}
	}

	public void register(AdHocCommand command) {
		this.commandsManager.registerCommand(command);
	}

}
