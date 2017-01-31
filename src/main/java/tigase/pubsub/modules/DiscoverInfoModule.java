/*
 * DiscoverInfoModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import tigase.component2.PacketWriter;
import tigase.component2.modules.ModulesManager;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.INodeMeta;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * Class description
 * 
 * 
 */
public class DiscoverInfoModule extends AbstractPubSubModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("query", "http://jabber.org/protocol/disco#info"));

	private final SimpleDateFormat formatter;
	private final ModulesManager modulesManager;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param packetWriter
	 * @param modulesManager
	 */
	public DiscoverInfoModule(PubSubConfig config, PacketWriter packetWriter, ModulesManager modulesManager) {
		super(config, packetWriter);
		this.modulesManager = modulesManager;
		this.formatter = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" );
		this.formatter.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
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
				INodeMeta nodeMeta = getRepository().getNodeMeta(packet.getStanzaTo().getBareJID(), nodeName);
				if (nodeMeta == null) {
					throw new PubSubException(Authorization.ITEM_NOT_FOUND);
				}

				AbstractNodeConfig nodeConfigClone = nodeMeta.getNodeConfig().clone();

				boolean allowed = ((senderJid == null) || (nodeConfigClone == null)) ? true : Utils.isAllowedDomain(
						senderJid.getBareJID(), nodeConfigClone.getDomains());

				if (!allowed) {
					throw new PubSubException(Authorization.FORBIDDEN);
				}
				resultQuery.addChild(new Element("identity", new String[] { "category", "type" }, new String[] { "pubsub",
						nodeConfigClone.getNodeType().name() }));
				resultQuery.addChild(new Element("feature", new String[] { "var" },
						new String[] { "http://jabber.org/protocol/pubsub" }));

				Form form = nodeConfigClone.getForm();

				form.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/pubsub#meta-data"));
				
				List<String> owners = new ArrayList<>();
				List<String> publishers = new ArrayList<>();
				
				IAffiliations affiliations = getRepository().getNodeAffiliations(packet.getStanzaTo().getBareJID(), nodeName);
				for (UsersAffiliation affiliation : affiliations.getAffiliations()) {
					if (affiliation.getAffiliation() == null) {
						continue;
					}
					
					switch (affiliation.getAffiliation()) {
						case owner:
							owners.add(affiliation.getJid().toString());
							break;
						case publisher:
							publishers.add(affiliation.getJid().toString());
							break;
						default:
							break;
					}
				}
				form.addField(Field.fieldJidMulti("pubsub#owner", owners.toArray(new String[owners.size()]), "Node owners"));
				form.addField(Field.fieldJidMulti("pubsub#publisher", publishers.toArray(new String[publishers.size()]), "Publishers to this node"));

				BareJID creator = nodeMeta.getCreator();
				String creationDateStr = "";
				if (nodeMeta.getCreationTime() != null) {
					synchronized (formatter) {
						creationDateStr = formatter.format(nodeMeta.getCreationTime());
					}
				}
				form.addField(Field.fieldJidSingle("pubsub#creator", creator != null ? creator.toString() : "", "Node creator"));
				form.addField(Field.fieldTextSingle("pubsub#creation_date", creationDateStr, "Creation date"));
				
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
