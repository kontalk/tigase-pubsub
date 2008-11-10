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
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.inmemory.NodeAffiliation;
import tigase.pubsub.repository.inmemory.Subscriber;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class ManageSubscriptionModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.name("iq").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("subscriptions"));

	private static Element createAffiliationNotification(String fromJid, String toJid, String nodeName, Subscription subscription) {
		Element message = new Element("message", new String[] { "from", "to" }, new String[] { fromJid, toJid });
		Element pubsub = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub" });
		message.addChild(pubsub);
		Element affilations = new Element("subscriptions", new String[] { "node" }, new String[] { nodeName });
		pubsub.addChild(affilations);
		affilations.addChild(new Element("subscription", new String[] { "jid", "subscription" }, new String[] { toJid,
				subscription.name() }));
		return message;
	}

	public ManageSubscriptionModule(PubSubConfig config, IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#manage-subscriptions" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		try {
			Element pubsub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
			Element subscriptions = pubsub.getChild("subscriptions");
			String nodeName = subscriptions.getAttribute("node");
			String type = element.getAttribute("type");

			if (type == null || !type.equals("get") && !type.equals("set")) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}

			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}
			AbstractNodeConfig nodeConfig = this.repository.getNodeConfig(nodeName);
			if (nodeConfig == null) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			}

			ISubscriptions nodeSubscriptions = this.repository.getNodeSubscriptions(nodeName);
			IAffiliations nodeAffiliations = this.repository.getNodeAffiliations(nodeName);

			String senderJid = element.getAttribute("from");

			if (!this.config.isAdmin(JIDUtils.getNodeID(senderJid))) {
				NodeAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid);
				if (senderAffiliation.getAffiliation() != Affiliation.owner) {
					throw new PubSubException(element, Authorization.FORBIDDEN);
				}
			}

			List<Element> result = new ArrayList<Element>();
			if (type.equals("get")) {
				result = processGet(element, subscriptions, nodeName, nodeSubscriptions);
			} else if (type.equals("set")) {
				result = processSet(element, subscriptions, nodeName, nodeSubscriptions);
			}
			if (nodeSubscriptions.isChanged()) {
				this.repository.update(nodeName, nodeSubscriptions);
			}
			return result;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private List<Element> processGet(Element element, Element subscriptions, String nodeName, final ISubscriptions nodeSubscriptions)
			throws RepositoryException {
		List<Element> result = new ArrayList<Element>();
		Element iq = createResultIQ(element);
		Element ps = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub#owner" });
		iq.addChild(ps);
		Element afr = new Element("subscriptions", new String[] { "node" }, new String[] { nodeName });
		ps.addChild(afr);

		Subscriber[] subscribers = nodeSubscriptions.getSubscriptions();
		if (subscribers != null) {
			for (Subscriber subscriber : subscribers) {
				if (subscriber.getSubscription() == Subscription.none) {
					continue;
				}
				Element subscription = new Element("subscription", new String[] { "jid", "subscription" }, new String[] {
						subscriber.getJid(), subscriber.getSubscription().name() });
				afr.addChild(subscription);
			}
		}

		result.add(iq);
		return result;
	}

	private List<Element> processSet(Element element, Element subscriptions, String nodeName, final ISubscriptions nodeSubscriptions)
			throws PubSubException, RepositoryException {
		List<Element> result = new ArrayList<Element>();
		Element iq = createResultIQ(element);
		result.add(iq);
		List<Element> subss = subscriptions.getChildren();
		for (Element a : subss) {
			if (!"subscription".equals(a.getName()))
				throw new PubSubException(Authorization.BAD_REQUEST);
		}
		for (Element af : subss) {
			String strSubscription = af.getAttribute("subscription");
			String jid = af.getAttribute("jid");
			if (strSubscription == null)
				continue;
			Subscription newSubscription = Subscription.valueOf(strSubscription);
			Subscription oldSubscription = nodeSubscriptions.getSubscription(jid);

			oldSubscription = oldSubscription == null ? Subscription.none : oldSubscription;

			if (oldSubscription == Subscription.none && newSubscription != Subscription.none) {
				nodeSubscriptions.addSubscriberJid(jid, newSubscription);
				result.add(createAffiliationNotification(element.getAttribute("to"), jid, nodeName, newSubscription));
			} else {
				nodeSubscriptions.changeSubscription(jid, newSubscription);
				result.add(createAffiliationNotification(element.getAttribute("to"), jid, nodeName, newSubscription));
			}

		}
		return result;
	}
}
