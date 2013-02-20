/*
 * PresenceCollectorModule.java
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

//~--- non-JDK imports --------------------------------------------------------

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;

import tigase.pubsub.ElementWriter;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.Module;

import tigase.util.JIDUtils;

import tigase.xml.Element;

//~--- JDK imports ------------------------------------------------------------

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Class description
 *
 *
 * @version        Enter version here..., 13/02/20
 * @author         Enter your name here...
 */
public class PresenceCollectorModule
				implements Module {
	private static final Criteria CRIT = ElementCriteria.name("presence");

	//~--- fields ---------------------------------------------------------------

	/** Field description */
	protected Logger log                             =
		Logger.getLogger(this.getClass().getName());
	private final Map<String, Set<String>> resources = new HashMap<String, Set<String>>();

	//~--- methods --------------------------------------------------------------

	// private final Set<String> onlineUsers = new HashSet<String>();

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 *
	 * @return
	 */
	public synchronized boolean addJid(final String jid) {
		if (jid == null) {
			return false;
		}

		boolean added         = false;
		final String bareJid  = JIDUtils.getNodeID(jid);
		final String resource = JIDUtils.getNodeResource(jid);

		if (resource != null) {
			Set<String> resources = this.resources.get(bareJid);

			if (resources == null) {
				resources = new HashSet<String>();
				this.resources.put(bareJid, resources);
			}
			added = resources.add(resource);
			log.finest("Contact " + jid + " is collected.");
		}

		// onlineUsers.add(jid);
		return added;
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public List<String> getAllAvailableJids() {
		ArrayList<String> result = new ArrayList<String>();

		for (Entry<String, Set<String>> entry : this.resources.entrySet()) {
			for (String reource : entry.getValue()) {
				result.add(entry.getKey() + "/" + reource);
			}
		}

		return result;
	}

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 *
	 * @return
	 */
	public List<String> getAllAvailableResources(final String jid) {
		final String bareJid        = JIDUtils.getNodeID(jid);
		final List<String> result   = new ArrayList<String>();
		final Set<String> resources = this.resources.get(bareJid);

		if (resources != null) {
			for (String reource : resources) {
				result.add(bareJid + "/" + reource);
			}
		}

		return result;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#presence-notifications" };
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
	 * @param jid
	 *
	 * @return
	 */
	public boolean isJidAvailable(final String jid) {
		final String bareJid        = JIDUtils.getNodeID(jid);
		final Set<String> resources = this.resources.get(bareJid);

		return (resources != null) && (resources.size() > 0);
	}

	//~--- methods --------------------------------------------------------------

	private Element preparePresence(final Element presence, String type) {
		String to = presence.getAttributeStaticStr("from");

		if (to != null) {
			String jid = JIDUtils.getNodeID(to);
			Element p  = new Element("presence", new String[] { "to", "from" },
															 new String[] { jid,
							presence.getAttributeStaticStr("to") });

			if (type != null) {
				p.setAttribute("type", type);
			}

			return p;
		}

		return null;
	}

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
	public List<Element> process(Element element, ElementWriter elementWriter)
					throws PubSubException {
		final String type    = element.getAttributeStaticStr("type");
		final String jid     = element.getAttributeStaticStr("from");
		List<Element> result = new ArrayList<Element>();

		if (type == null) {
			boolean added = addJid(jid);

			if (added) {
				Element p = new Element("presence", new String[] { "to", "from" },
																new String[] { jid,
								element.getAttributeStaticStr("to") });

				result.add(p);
			}
		} else if ("unavailable".equals(type)) {
			removeJid(jid);

			Element p = new Element("presence", new String[] { "to", "from", "type" },
															new String[] { jid,
							element.getAttributeStaticStr("to"), "unavailable" });

			result.add(p);
		} else if ("subscribe".equals(type)) {
			log.finest("Contact " + jid + " wants to subscribe PubSub");

			Element presence = preparePresence(element, "subscribed");

			if (presence != null) {
				result.add(presence);
			}
			presence = preparePresence(element, "subscribe");
			if (presence != null) {
				result.add(presence);
			}
		} else if ("unsubscribe".equals(type) || "unsubscribed".equals(type)) {
			log.finest("Contact " + jid + " wants to unsubscribe PubSub");

			Element presence = preparePresence(element, "unsubscribed");

			if (presence != null) {
				result.add(presence);
			}
			presence = preparePresence(element, "unsubscribe");
			if (presence != null) {
				result.add(presence);
			}
		}

		return (result.size() == 0)
					 ? null
					 : result;
	}

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 *
	 * @return
	 */
	protected synchronized boolean removeJid(final String jid) {
		if (jid == null) {
			return false;
		}

		final String bareJid  = JIDUtils.getNodeID(jid);
		final String resource = JIDUtils.getNodeResource(jid);
		boolean removed       = false;

		// onlineUsers.remove(jid);
		if (resource == null) {
			resources.remove(bareJid);
		} else {
			Set<String> resources = this.resources.get(bareJid);

			if (resources != null) {
				removed = resources.remove(resource);
				log.finest("Contact " + jid + " is removed from collection.");
				if (resources.size() == 0) {
					this.resources.remove(bareJid);
				}
			}
		}

		return removed;
	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
