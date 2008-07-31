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
package tigase.pubsub;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.util.JIDUtils;
import tigase.xml.Element;

public abstract class AbstractModule implements Module {

	public static Element createResultIQ(Element iq) {
		return new Element("iq", new String[] { "type", "from", "to", "id" }, new String[] { "result", iq.getAttribute("to"),
				iq.getAttribute("from"), iq.getAttribute("id") });
	}

	public static List<Element> createResultIQArray(Element iq) {
		return makeArray(createResultIQ(iq));
	}

	public static List<Element> makeArray(Element... elements) {
		ArrayList<Element> result = new ArrayList<Element>();
		for (Element element : elements) {
			result.add(element);

		}
		return result;
	}

	protected final PubSubConfig config;

	protected Logger log = Logger.getLogger(this.getClass().getName());

	protected final IPubSubRepository repository;

	public AbstractModule(final PubSubConfig config, final IPubSubRepository pubsubRepository) {
		this.config = config;
		this.repository = pubsubRepository;
	}

	protected String findBestJid(final String[] allSubscribers, final String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		String best = null;
		for (String j : allSubscribers) {
			if (j.equals(jid)) {
				return j;
			} else if (bareJid.equals(j)) {
				best = j;
			}
		}
		return best;
	}

	public String[] getActiveSubscribers(final String nodeName) throws RepositoryException {
		return getActiveSubscribers(repository.getSubscriptions(nodeName), nodeName);
	}

	public String[] getActiveSubscribers(final String[] jids, final String nodeName) throws RepositoryException {
		List<String> result = new ArrayList<String>();
		if (jids != null) {
			for (String jid : jids) {
				Affiliation affiliation = repository.getSubscriberAffiliation(nodeName, jid);
				if (affiliation != Affiliation.outcast && affiliation != Affiliation.none) {
					Subscription subscription = repository.getSubscription(nodeName, jid);
					if (subscription == Subscription.subscribed) {
						result.add(jid);
					}
				}

			}
		}
		return result.toArray(new String[] {});
	}

	protected boolean hasSenderSubscription(final String jid, final String nodeName) throws RepositoryException {
		final String[] subscribers = this.repository.getSubscriptions(nodeName);
		final String bareJid = JIDUtils.getNodeID(jid);
		for (String owner : subscribers) {
			Affiliation affiliation = this.repository.getSubscriberAffiliation(nodeName, owner);
			if (affiliation != Affiliation.owner)
				continue;
			if (bareJid.equals(owner))
				return true;
			String[] buddies = this.repository.getUserRoster(owner);
			for (String buddy : buddies) {
				if (bareJid.equals(buddy)) {
					String s = this.repository.getBuddySubscription(owner, bareJid);
					if (s != null && ("from".equals(s) || "both".equals(s))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	protected boolean isSenderInRosterGroup(String jid, String nodeName) throws RepositoryException {
		final String[] subscribers = this.repository.getSubscriptions(nodeName);
		final String bareJid = JIDUtils.getNodeID(jid);
		final String[] groupsAllowed = this.repository.getNodeConfig(nodeName).getRosterGroupsAllowed();
		if (groupsAllowed == null || groupsAllowed.length == 0)
			return true;
		for (String owner : subscribers) {
			Affiliation affiliation = this.repository.getSubscriberAffiliation(nodeName, owner);
			if (affiliation != Affiliation.owner)
				continue;
			if (bareJid.equals(owner))
				return true;
			String[] buddies = this.repository.getUserRoster(owner);
			for (String buddy : buddies) {
				if (bareJid.equals(buddy)) {
					String[] groups = this.repository.getBuddyGroups(owner, bareJid);
					for (String group : groups) {
						if (Utils.contain(group, groupsAllowed))
							return true;
					}
				}
			}
		}
		return false;
	}

}
