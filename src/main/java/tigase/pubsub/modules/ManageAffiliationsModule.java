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
package tigase.pubsub.modules;

import java.util.ArrayList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.Affiliation;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class ManageAffiliationsModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.name("iq").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("affiliations"));

	private static Element createAffiliationNotification(String fromJid, String toJid, String nodeName, Affiliation affilation) {
		Element message = new Element("message", new String[] { "from", "to" }, new String[] { fromJid, toJid });
		Element pubsub = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub" });
		message.addChild(pubsub);
		Element affilations = new Element("affiliations", new String[] { "node" }, new String[] { nodeName });
		pubsub.addChild(affilations);
		affilations.addChild(new Element("affilation", new String[] { "jid", "affiliation" }, new String[] { toJid,
				affilation.name() }));
		return message;
	}

	public ManageAffiliationsModule(PubSubConfig config, IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#modify-affiliations" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		try {
			Element pubsub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
			Element affiliations = pubsub.getChild("affiliations");
			String nodeName = affiliations.getAttribute("node");
			String type = element.getAttribute("type");

			if (type == null || !type.equals("get") && !type.equals("set")) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}

			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}
			NodeType nodeType = this.repository.getNodeType(nodeName);
			if (nodeType == null) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			}
			String senderJid = element.getAttribute("from");
			Affiliation senderAffiliation = NodeDeleteModule.getUserAffiliation(this.repository, nodeName, senderJid);
			if (senderAffiliation != Affiliation.owner) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			if (type.equals("get")) {
				return processGet(element, affiliations, nodeName);
			} else if (type.equals("set")) {
				return processSet(element, affiliations, nodeName);
			}
			throw new PubSubException(Authorization.INTERNAL_SERVER_ERROR);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private List<Element> processGet(Element element, Element affiliations, String nodeName) throws RepositoryException {
		List<Element> result = new ArrayList<Element>();
		Element iq = createResultIQ(element);
		Element ps = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub#owner" });
		iq.addChild(ps);
		Element afr = new Element("affiliations", new String[] { "node" }, new String[] { nodeName });
		ps.addChild(afr);

		String[] subscribers = this.repository.getSubscribersJid(nodeName);
		if (subscribers != null) {
			for (String jid : subscribers) {
				Affiliation ja = this.repository.getSubscriberAffiliation(nodeName, jid);
				if (ja == Affiliation.none) {
					continue;
				}
				Element affiliation = new Element("affiliation", new String[] { "jid", "affiliation" }, new String[] { jid,
						ja.name() });
				afr.addChild(affiliation);
			}
		}

		result.add(iq);
		return result;
	}

	private List<Element> processSet(Element element, Element affiliations, String nodeName) throws PubSubException,
			RepositoryException {
		List<Element> result = new ArrayList<Element>();
		Element iq = createResultIQ(element);
		result.add(iq);
		List<Element> affs = affiliations.getChildren();
		for (Element a : affs) {
			if (!"affiliation".equals(a.getName()))
				throw new PubSubException(Authorization.BAD_REQUEST);
		}
		for (Element af : affs) {
			String strAfiliation = af.getAttribute("affiliation");
			String jid = af.getAttribute("jid");
			if (strAfiliation == null)
				continue;
			Affiliation newAffiliation = Affiliation.valueOf(strAfiliation);
			Affiliation oldAffiliation = this.repository.getSubscriberAffiliation(nodeName, jid);
			oldAffiliation = oldAffiliation == null ? Affiliation.none : oldAffiliation;

			if (oldAffiliation == Affiliation.none && newAffiliation != Affiliation.none) {
				this.repository.addSubscriberJid(nodeName, jid, newAffiliation, Subscription.none);
				result.add(createAffiliationNotification(element.getAttribute("to"), jid, nodeName, newAffiliation));
			} else if (oldAffiliation != Affiliation.none && newAffiliation == Affiliation.none) {
				this.repository.removeSubscriber(nodeName, jid);
				result.add(createAffiliationNotification(element.getAttribute("to"), jid, nodeName, newAffiliation));
			} else {
				this.repository.changeAffiliation(nodeName, jid, newAffiliation);
				result.add(createAffiliationNotification(element.getAttribute("to"), jid, nodeName, newAffiliation));
			}

		}
		return result;
	}
}
