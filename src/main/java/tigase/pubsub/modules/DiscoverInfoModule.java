/*
 * DiscoverInfoModule.java
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

import tigase.component.PacketWriter;
import tigase.component.modules.ModulesManager;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubException;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.JID;

/**
 * Class description
 * 
 * 
 */
public class DiscoverInfoModule extends AbstractPubSubModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("query", "http://jabber.org/protocol/disco#info"));

	private final ModulesManager modulesManager;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 * @param modules
	 */
	public DiscoverInfoModule(PubSubConfig config, PacketWriter packetWriter, ModulesManager modulesManager) {
		super(config, packetWriter);
		this.modulesManager = modulesManager;
	}

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

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 * @return
	 * 
	 * @throws PubSubException
	 */
	@Override
	public void process(Packet packet) throws PubSubException {
		try {
			final Element element = packet.getElement();
			final JID senderJid = packet.getStanzaFrom();
			final Element query = element.getChild("query", "http://jabber.org/protocol/disco#info");
			final String nodeName = query.getAttributeStaticStr("node");
			Element resultQuery = new Element("query", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/disco#info" });

			Packet resultIq = packet.okResult(resultQuery, 0);

			if (nodeName == null) {
				resultQuery.addChild(new Element("identity", new String[] { "category", "type", "name" }, new String[] {
						"pubsub", "service", "Publish-Subscribe" }));
				for (String f : modulesManager.getFeatures()) {
					resultQuery.addChild(new Element("feature", new String[] { "var" }, new String[] { f }));
				}
			} else {
				AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(packet.getStanzaTo().getBareJID(), nodeName);

				if (nodeConfig == null) {
					throw new PubSubException(Authorization.ITEM_NOT_FOUND);
				}

				boolean allowed = ((senderJid == null) || (nodeConfig == null)) ? true : Utils.isAllowedDomain(
						senderJid.getBareJID(), nodeConfig.getDomains());

				if (!allowed) {
					throw new PubSubException(Authorization.FORBIDDEN);
				}
				resultQuery.addChild(new Element("identity", new String[] { "category", "type" }, new String[] { "pubsub",
						nodeConfig.getNodeType().name() }));
				resultQuery.addChild(new Element("feature", new String[] { "var" },
						new String[] { "http://jabber.org/protocol/pubsub" }));

				Form form = new Form("result", null, null);

				form.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/pubsub#meta-data"));
				form.addField(Field.fieldTextSingle("pubsub#title", nodeConfig.getTitle(), "A short name for the node"));
				resultQuery.addChild(form.getElement());
			}

			packetWriter.write(resultIq);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}
