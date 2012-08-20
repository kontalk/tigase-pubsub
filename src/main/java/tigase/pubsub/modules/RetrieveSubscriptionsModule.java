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

//~--- non-JDK imports --------------------------------------------------------

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

//~--- classes ----------------------------------------------------------------

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:27:10 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class RetrieveSubscriptionsModule extends AbstractModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("subscriptions"));

	// ~--- constructors
	// ---------------------------------------------------------

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 */
	public RetrieveSubscriptionsModule(PubSubConfig config, IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#retrieve-subscriptions" };
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

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param element
	 * @param elementWriter
	 * 
	 * @return
	 * 
	 * @throws PubSubException
	 */
	@Override
	public List<Element> process(Element element, ElementWriter elementWriter) throws PubSubException {
		try {
			final Element pubsub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
			final Element subscriptions = pubsub.getChild("subscriptions");
			final String nodeName = subscriptions.getAttribute("node");
			final String senderJid = element.getAttribute("from");
			final BareJID senderBareJid = BareJID.bareJIDInstanceNS(senderJid);
			final Element result = createResultIQ(element);
			final Element pubsubResult = new Element("pubsub", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/pubsub" });

			result.addChild(pubsubResult);

			final Element subscriptionsResult = new Element("subscriptions");

			pubsubResult.addChild(subscriptionsResult);

			if (nodeName == null) {
				IPubSubDAO directRepo = this.repository.getPubSubDAO();
				String[] nodes = directRepo.getNodesList();

				if (nodes != null) {
					for (String node : nodes) {
						final ISubscriptions subscribers = directRepo.getNodeSubscriptions(node);

						if (subscribers != null) {
							for (UsersSubscription usersSubscription : subscribers.getSubscriptions()) {
								if (senderBareJid.equals(usersSubscription.getJid())) {
									ISubscriptions ns = directRepo.getNodeSubscriptions(nodeName);
									Subscription subscription = ns.getSubscription(usersSubscription.getJid().toString());
									Element a = new Element("subscription", new String[] { "node", "jid", "subscription" },
											new String[] { node, usersSubscription.getJid().toString(), subscription.name() });

									subscriptionsResult.addChild(a);
								}
							}
						}
					}
				}
			} else {
				ISubscriptions nodeSubscriptions = repository.getNodeSubscriptions(nodeName);

				subscriptionsResult.addAttribute("node", nodeName);

				UsersSubscription[] subscribers = nodeSubscriptions.getSubscriptions();

				for (final UsersSubscription usersSubscription : subscribers) {
					Element s = new Element("subscription", new String[] { "jid", "subscription", "subid" }, new String[] {
							usersSubscription.getJid().toString(), usersSubscription.getSubscription().name(),
							usersSubscription.getSubid() });

					subscriptionsResult.addChild(s);
				}
			}

			return makeArray(result);
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}

// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com
