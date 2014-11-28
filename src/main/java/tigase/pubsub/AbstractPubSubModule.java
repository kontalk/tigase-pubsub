/*
 * AbstractModule.java
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

package tigase.pubsub;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import tigase.component2.PacketWriter;
import tigase.component2.eventbus.EventBus;
import tigase.component2.modules.Module;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterAbstract.SubscriptionType;
import tigase.xmpp.impl.roster.RosterElement;

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:24:03 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public abstract class AbstractPubSubModule implements Module {

	/**
	 * Method description
	 * 
	 * 
	 * @param iq
	 * 
	 * @return
	 */
	public static Element createResultIQ(Element iq) {
		Element e = new Element("iq");
		e.setXMLNS(Iq.CLIENT_XMLNS);
		String id = iq.getAttributeStaticStr("id");
		String from = iq.getAttributeStaticStr("from");
		String to = iq.getAttributeStaticStr("to");

		e.addAttribute("type", "result");
		if (to != null) {
			e.addAttribute("from", to);
		}
		if (from != null) {
			e.addAttribute("to", from);
		}
		if (id != null) {
			e.addAttribute("id", id);
		}

		return e;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param iq
	 * 
	 * @return
	 */
	public static List<Element> createResultIQArray(Element iq) {
		return makeArray(createResultIQ(iq));
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param allSubscribers
	 * @param jid
	 * 
	 * @return
	 */
	@Deprecated
	protected static String findBestJid(final String[] allSubscribers, final String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		String best = null;

		for (String j : allSubscribers) {
			if (j.equals(jid)) {
				return j;
			} else {
				if (bareJid.equals(j)) {
					best = j;
				}
			}
		}

		return best;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeConfig
	 * @param jids
	 * @param affiliations
	 * @param subscriptions
	 * 
	 * @return
	 */
	public static Collection<BareJID> getActiveSubscribers(final AbstractNodeConfig nodeConfig, final BareJID[] jids,
			final IAffiliations affiliations, final ISubscriptions subscriptions) {
		Set<BareJID> result = new HashSet<BareJID>();
		final boolean presenceExpired = nodeConfig.isPresenceExpired();

		if (jids != null) {
			for (BareJID jid : jids) {
				if (presenceExpired) {
				}

				UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(jid);

				// /* && affiliation.getAffiliation() != Affiliation.none */
				if (affiliation.getAffiliation() != Affiliation.outcast) {
					Subscription subscription = subscriptions.getSubscription(jid);

					if (subscription == Subscription.subscribed) {
						result.add(jid);
					}
				}
			}
		}

		return result;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeConfig
	 * @param affiliations
	 * @param subscriptions
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public static Collection<BareJID> getActiveSubscribers(final AbstractNodeConfig nodeConfig,
			final IAffiliations affiliations, final ISubscriptions subscriptions) throws RepositoryException {
		UsersSubscription[] subscribers = subscriptions.getSubscriptionsForPublish();

		if (subscribers == null) {
			return Collections.emptyList();
		}

		BareJID[] jids = new BareJID[subscribers.length];

		for (int i = 0; i < subscribers.length; i++) {
			jids[i] = subscribers[i].getJid();
		}

		return getActiveSubscribers(nodeConfig, jids, affiliations, subscriptions);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param elements
	 * 
	 * @return
	 */
	public static List<Element> makeArray(Element... elements) {
		LinkedList<Element> result = new LinkedList<Element>();

		for (Element element : elements) {
			result.add(element);
		}

		return result;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param elements
	 * 
	 * @return
	 */
	public static List<Packet> makeArray(Packet... packets) {
		LinkedList<Packet> result = new LinkedList<Packet>();

		for (Packet packet : packets) {
			result.add(packet);
		}

		return result;
	}

	/** Field description */
	protected final PubSubConfig config;

	/** Field description */
	protected final Logger log = Logger.getLogger(this.getClass().getName());

	protected final PacketWriter packetWriter;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 * @param packetWriter
	 *            TODO
	 */
	public AbstractPubSubModule(final PubSubConfig config, PacketWriter packetWriter) {
		this.config = config;
		this.packetWriter = packetWriter;
	}

	protected EventBus getEventBus() {
		return config.getEventBus();
	}

	protected IPubSubRepository getRepository() {
		return config.getPubSubRepository();
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * @param affiliations
	 * @param subscriptions
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	protected boolean hasSenderSubscription(final BareJID bareJid, final IAffiliations affiliations,
			final ISubscriptions subscriptions) throws RepositoryException {
		final UsersSubscription[] subscribers = subscriptions.getSubscriptions();

		for (UsersSubscription owner : subscribers) {
			UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(owner.getJid());

			if (affiliation.getAffiliation() != Affiliation.owner) {
				continue;
			}
			if (bareJid.equals(owner.getJid())) {
				return true;
			}

			Map<BareJID,RosterElement> buddies = getRepository().getUserRoster(owner.getJid());
			RosterElement re = buddies.get(bareJid);
			if (re != null) {
				if (re.getSubscription() == SubscriptionType.both || re.getSubscription() == SubscriptionType.from
						|| re.getSubscription() == SubscriptionType.from_pending_out)
					return true;
			}
		}

		return false;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * @param nodeConfig
	 * @param affiliations
	 * @param subscriptions
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	protected boolean isSenderInRosterGroup(BareJID bareJid, AbstractNodeConfig nodeConfig, IAffiliations affiliations,
			final ISubscriptions subscriptions) throws RepositoryException {
		final UsersSubscription[] subscribers = subscriptions.getSubscriptions();
		final String[] groupsAllowed = nodeConfig.getRosterGroupsAllowed();

		if ((groupsAllowed == null) || (groupsAllowed.length == 0)) {
			return true;
		}
		
		// @TODO - is there a way to optimize this?
		for (UsersSubscription owner : subscribers) {
			UsersAffiliation affiliation = affiliations.getSubscriberAffiliation(owner.getJid());

			if (affiliation.getAffiliation() != Affiliation.owner) {
				continue;
			}
			if (bareJid.equals(owner)) {
				return true;
			}

			Map<BareJID,RosterElement> buddies = getRepository().getUserRoster(owner.getJid());
			RosterElement re = buddies.get(bareJid);
			if (re != null && re.getGroups() != null) {
				for (String group : groupsAllowed) {
					if (Utils.contain(group, groupsAllowed)) {
						return true;
					}				
				}
			}			
		}

		return false;
	}
}
