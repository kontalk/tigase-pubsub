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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import tigase.component.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

/**
 * Class description
 * 
 * 
 */
public class PresenceCollectorModule extends AbstractPubSubModule {

	private static final Criteria CRIT = ElementCriteria.name("presence");

	private final Map<String, Set<String>> resources = new HashMap<String, Set<String>>();

	public PresenceCollectorModule(PubSubConfig config, IPubSubRepository pubsubRepository, PacketWriter packetWriter) {
		super(config, pubsubRepository, packetWriter);
	}

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

		boolean added = false;
		final String bareJid = JIDUtils.getNodeID(jid);
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
		final String bareJid = JIDUtils.getNodeID(jid);
		final List<String> result = new ArrayList<String>();
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
		final String bareJid = JIDUtils.getNodeID(jid);
		final Set<String> resources = this.resources.get(bareJid);

		return (resources != null) && (resources.size() > 0);
	}

	private Packet preparePresence(final Packet presence, String type) {
		JID to = presence.getTo();

		if (to != null) {
			JID jid = to.copyWithoutResource();
			Element p = new Element("presence", new String[] { "to", "from" }, new String[] { jid.toString(), to.toString() });

			if (type != null) {
				p.setAttribute("type", type);
			}

			return new Presence(p, jid, presence.getStanzaTo());
		}

		return null;
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
	public void process(Packet packet) throws PubSubException {
		final StanzaType type = packet.getType();
		final JID jid = packet.getStanzaFrom();
		final JID toJid = packet.getStanzaTo();

		if (type == null) {
			boolean added = addJid(jid.toString());

			if (added) {
				Packet p = new Presence(new Element("presence", new String[] { "to", "from" }, new String[] { jid.toString(),
						toJid.toString() }), toJid, jid);

				packetWriter.write(p);
			}
		} else if ("unavailable".equals(type)) {
			removeJid(jid.toString());

			Packet p = new Presence(new Element("presence", new String[] { "to", "from", "type" }, new String[] {
					jid.toString(), toJid.toString(), "unavailable" }), toJid, jid);

			packetWriter.write(p);
		} else if ("subscribe".equals(type)) {
			log.finest("Contact " + jid + " wants to subscribe PubSub");

			Packet presence = preparePresence(packet, "subscribed");

			if (presence != null) {
				packetWriter.write(presence);
			}
			presence = preparePresence(packet, "subscribe");
			if (presence != null) {
				packetWriter.write(presence);
			}
		} else if ("unsubscribe".equals(type) || "unsubscribed".equals(type)) {
			log.finest("Contact " + jid + " wants to unsubscribe PubSub");

			Packet presence = preparePresence(packet, "unsubscribed");

			if (presence != null) {
				packetWriter.write(presence);
			}
			presence = preparePresence(packet, "unsubscribe");
			if (presence != null) {
				packetWriter.write(presence);
			}
		}

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

		final String bareJid = JIDUtils.getNodeID(jid);
		final String resource = JIDUtils.getNodeResource(jid);
		boolean removed = false;

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
