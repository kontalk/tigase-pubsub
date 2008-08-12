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
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.inmemory.NodeAffiliation;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class ManageSubscriptionModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.name("message").add(
			ElementCriteria.name("x", new String[] { "xmlns", "type" }, new String[] { "jabber:x:data", "submit" })).add(
			ElementCriteria.name("field", new String[] { "var" }, new String[] { "FORM_TYPE" })).add(
			ElementCriteria.name("value", "http://jabber.org/protocol/pubsub#subscribe_authorization", null, null));

	public ManageSubscriptionModule(PubSubConfig config, IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#get-pending" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(Element message) throws PubSubException {
		try {
			Form x = new Form(message.getChild("x", "jabber:x:data"));
			final String subId = x.getAsString("pubsub#subid");
			final String node = x.getAsString("pubsub#node");
			final String subscriberJid = x.getAsString("pubsub#subscriber_jid");
			final Boolean allow = x.getAsBoolean("pubsub#allow");

			if (allow == null)
				return null;

			AbstractNodeConfig nodeConfig = repository.getNodeConfig(node);
			if (nodeConfig == null) {
				throw new PubSubException(message, Authorization.ITEM_NOT_FOUND);
			}
			String jid = message.getAttribute("from");
			NodeAffiliation senderAffiliation = this.repository.getSubscriberAffiliation(node, jid);
			if (senderAffiliation.getAffiliation() != Affiliation.owner) {
				throw new PubSubException(message, Authorization.FORBIDDEN);
			}
			String userSubId = this.repository.getSubscriptionId(node, subscriberJid);
			if (subId != null && !subId.equals(userSubId)) {
				throw new PubSubException(message, Authorization.NOT_ACCEPTABLE, PubSubErrorCondition.INVALID_SUBID);
			}

			Subscription subscription = this.repository.getSubscription(node, subscriberJid);
			if (subscription != Subscription.pending)
				return null;
			Affiliation affiliation = this.repository.getSubscriberAffiliation(node, jid).getAffiliation();
			if (allow) {
				subscription = Subscription.subscribed;
				affiliation = Affiliation.member;
				this.repository.changeSubscription(node, subscriberJid, subscription);
				this.repository.changeAffiliation(node, subscriberJid, affiliation);
			} else {
				subscription = Subscription.none;
				this.repository.removeSubscriber(node, subscriberJid);
			}

			Element msg = new Element("message", new String[] { "from", "to", "id" }, new String[] { message.getAttribute("to"),
					subscriberJid, Utils.createUID() });
			msg.addChild(SubscribeNodeModule.makeSubscription(node, subscriberJid, subscription, null));
			return makeArray(msg);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public List<Element> sendAuthorizationRequest(final String nodeName, final String fromJid, final String subID,
			final String subscriberJid) throws RepositoryException {
		Form x = new Form("form", "PubSub subscriber request",
				"To approve this entity's subscription request, click the OK button. To deny the request, click the cancel button.");
		x.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/pubsub#subscribe_authorization"));
		x.addField(Field.fieldHidden("pubsub#subid", subID));
		x.addField(Field.fieldTextSingle("pubsub#node", nodeName, "Node ID"));
		x.addField(Field.fieldJidSingle("pusub#subscriber_jid", subscriberJid, "Subscriber Address"));
		x.addField(Field.fieldBoolean("pubsub#allow", Boolean.FALSE, "Allow this JID to subscribe to this pubsub node?"));

		List<Element> result = new ArrayList<Element>();
		NodeAffiliation[] affiliations = this.repository.getAffiliations(nodeName);
		if (affiliations != null) {
			for (NodeAffiliation affiliation : affiliations) {
				if (affiliation.getAffiliation() == Affiliation.owner) {
					Element message = new Element("message", new String[] { "id", "to", "from" }, new String[] { Utils.createUID(),
							affiliation.getJid(), fromJid });
					message.addChild(x.getElement());
					result.add(message);
				}
			}
		}
		return result;
	}
}
