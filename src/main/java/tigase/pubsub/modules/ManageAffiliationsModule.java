/*
 * ManageAffiliationsModule.java
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

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.PacketWriter;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

/**
 * Class description
 * 
 * 
 * @version Enter version here..., 13/02/20
 * @author Enter your name here...
 */
public class ManageAffiliationsModule extends AbstractModule {
	private static final Criteria CRIT = ElementCriteria.name("iq").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("affiliations"));

	private static Packet createAffiliationNotification(JID fromJid, JID toJid, String nodeName, Affiliation affilation) {
		Packet message = Message.getMessage(fromJid, toJid, null, null, null, null, null);
		Element pubsub = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub" });

		message.getElement().addChild(pubsub);

		Element affilations = new Element("affiliations", new String[] { "node" }, new String[] { nodeName });

		pubsub.addChild(affilations);
		affilations.addChild(new Element("affilation", new String[] { "jid", "affiliation" }, new String[] { toJid.toString(),
				affilation.name() }));

		return message;
	}

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 */
	public ManageAffiliationsModule(PubSubConfig config, IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#modify-affiliations" };
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
	 * @param packetWriter
	 * 
	 * @return
	 * 
	 * @throws PubSubException
	 */
	@Override
	public List<Packet> process(Packet packet, PacketWriter packetWriter) throws PubSubException {
		try {
			BareJID toJid = packet.getStanzaTo().getBareJID();
			Element element = packet.getElement();
			Element pubsub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
			Element affiliations = pubsub.getChild("affiliations");
			String nodeName = affiliations.getAttributeStaticStr("node");
			StanzaType type = packet.getType();

			if ((type == null) || (type != StanzaType.get && type != StanzaType.set)) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}
			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}

			final AbstractNodeConfig nodeConfig = this.repository.getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			}

			final IAffiliations nodeAffiliations = this.repository.getNodeAffiliations(toJid, nodeName);
			String senderJid = element.getAttributeStaticStr("from");

			if (!this.config.isAdmin(JIDUtils.getNodeID(senderJid))) {
				UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid);

				if (senderAffiliation.getAffiliation() != Affiliation.owner) {
					throw new PubSubException(element, Authorization.FORBIDDEN);
				}
			}
			if (type == StanzaType.get) {
				processGet(packet, affiliations, nodeName, nodeAffiliations, packetWriter);
			} else if (type == StanzaType.set) {
				processSet(packet, affiliations, nodeName, nodeConfig, nodeAffiliations, packetWriter);
			}
			if (nodeAffiliations.isChanged()) {
				repository.update(toJid, nodeName, nodeAffiliations);
			}

			return null;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	private void processGet(Packet packet, Element affiliations, String nodeName, final IAffiliations nodeAffiliations,
			PacketWriter packetWriter) throws RepositoryException {
		Element ps = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub#owner" });

		Packet iq = packet.okResult(ps, 0);

		Element afr = new Element("affiliations", new String[] { "node" }, new String[] { nodeName });

		ps.addChild(afr);

		UsersAffiliation[] affiliationsList = nodeAffiliations.getAffiliations();

		if (affiliationsList != null) {
			for (UsersAffiliation affi : affiliationsList) {
				if (affi.getAffiliation() == Affiliation.none) {
					continue;
				}

				Element affiliation = new Element("affiliation", new String[] { "jid", "affiliation" }, new String[] {
						affi.getJid(), affi.getAffiliation().name() });

				afr.addChild(affiliation);
			}
		}
		packetWriter.write(iq);
	}

	private void processSet(final Packet packet, final Element affiliations, final String nodeName,
			final AbstractNodeConfig nodeConfig, final IAffiliations nodeAffiliations, PacketWriter packetWriter)
			throws PubSubException, RepositoryException {
		List<Element> affs = affiliations.getChildren();

		for (Element a : affs) {
			if (!"affiliation".equals(a.getName())) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}
		}
		for (Element af : affs) {
			String strAfiliation = af.getAttributeStaticStr("affiliation");
			String jidStr = af.getAttributeStaticStr("jid");
			JID jid = JID.jidInstanceNS(jidStr);

			if (strAfiliation == null) {
				continue;
			}

			Affiliation newAffiliation = Affiliation.valueOf(strAfiliation);
			Affiliation oldAffiliation = nodeAffiliations.getSubscriberAffiliation(jidStr).getAffiliation();

			oldAffiliation = (oldAffiliation == null) ? Affiliation.none : oldAffiliation;
			if ((oldAffiliation == Affiliation.none) && (newAffiliation != Affiliation.none)) {
				nodeAffiliations.addAffiliation(jidStr, newAffiliation);
				if (nodeConfig.isTigaseNotifyChangeSubscriptionAffiliationState()) {
					packetWriter.write(createAffiliationNotification(packet.getStanzaTo(), jid, nodeName, newAffiliation));
				}
			} else {
				nodeAffiliations.changeAffiliation(jidStr, newAffiliation);
				if (nodeConfig.isTigaseNotifyChangeSubscriptionAffiliationState()) {
					packetWriter.write(createAffiliationNotification(packet.getStanzaTo(), jid, nodeName, newAffiliation));
				}
			}
		}
		Packet iq = packet.okResult((Element) null, 0);
		packetWriter.write(iq);
	}
}
