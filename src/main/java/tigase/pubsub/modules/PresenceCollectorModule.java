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

import tigase.component2.PacketWriter;
import tigase.component2.eventbus.Event;
import tigase.component2.eventbus.EventHandler;
import tigase.component2.eventbus.EventType;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.PresenceCollectorModule.BuddyVisibilityHandler.BuddyVisibilityEvent;
import tigase.pubsub.modules.PresenceCollectorModule.PresenceChangeHandler.PresenceChangeEvent;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

/**
 * Class description
 * 
 * 
 */
public class PresenceCollectorModule extends AbstractPubSubModule {

	public interface BuddyVisibilityHandler extends EventHandler {

		public static class BuddyVisibilityEvent extends Event<BuddyVisibilityHandler> {

			public static final EventType<BuddyVisibilityHandler> TYPE = new EventType<BuddyVisibilityHandler>();

			private final boolean becomeOnline;
			private final BareJID buddyJID;

			public BuddyVisibilityEvent(BareJID buddyJID, boolean becomeOnline) {
				super(TYPE);
				this.buddyJID = buddyJID;
				this.becomeOnline = becomeOnline;
			}

			@Override
			protected void dispatch(BuddyVisibilityHandler handler) {
				handler.onBuddyVisibilityChange(buddyJID, becomeOnline);
			}

		}

		void onBuddyVisibilityChange(BareJID buddyJID, boolean becomeOnline);
	}

	public interface PresenceChangeHandler extends EventHandler {

		public static class PresenceChangeEvent extends Event<PresenceChangeHandler> {

			public static final EventType<PresenceChangeHandler> TYPE = new EventType<PresenceChangeHandler>();

			private Packet packet;

			public PresenceChangeEvent(Packet packet) {
				super(TYPE);
				this.packet = packet;
			}

			@Override
			protected void dispatch(PresenceChangeHandler handler) {
				handler.onPresenceChange(packet);
			}

		}

		void onPresenceChange(Packet packet);
	}

	private static final Criteria CRIT = ElementCriteria.name("presence");

	private final Map<BareJID, Set<String>> resources = new HashMap<BareJID, Set<String>>();

	public PresenceCollectorModule(PubSubConfig config, PacketWriter packetWriter) {
		super(config, packetWriter);
	}

	public void addBuddyVisibilityHandler(BuddyVisibilityHandler handler) {
		config.getEventBus().addHandler(BuddyVisibilityHandler.BuddyVisibilityEvent.TYPE, handler);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * 
	 * @return
	 */
	public synchronized boolean addJid(final JID jid) {
		if (jid == null) {
			return false;
		}

		boolean added = false;
		final BareJID bareJid = jid.getBareJID();
		final String resource = jid.getResource();

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

	public void addPresenceChangeHandler(PresenceChangeHandler handler) {
		config.getEventBus().addHandler(PresenceChangeEvent.TYPE, handler);
	}

	private void firePresenceChangeEvent(Packet packet) {
		PresenceChangeEvent event = new PresenceChangeEvent(packet);
		config.getEventBus().fire(event);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public List<JID> getAllAvailableJids() {
		ArrayList<JID> result = new ArrayList<JID>();

		for (Entry<BareJID, Set<String>> entry : this.resources.entrySet()) {
			for (String reource : entry.getValue()) {
				result.add(JID.jidInstanceNS(entry.getKey(), reource));
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
	public List<JID> getAllAvailableResources(final BareJID bareJid) {
		final List<JID> result = new ArrayList<JID>();
		final Set<String> resources = this.resources.get(bareJid);

		if (resources != null) {
			for (String reource : resources) {
				result.add(JID.jidInstanceNS(bareJid, reource));
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
	public boolean isJidAvailable(final BareJID bareJid) {
		final Set<String> resources = this.resources.get(bareJid);

		return (resources != null) && (resources.size() > 0);
	}

	private Packet preparePresence(final Packet presence, StanzaType type) {
		JID to = presence.getTo();

		if (to != null) {
			JID jid = to.copyWithoutResource();
			Element p = new Element("presence", new String[] { "to", "from" }, new String[] { jid.toString(), to.toString() });

			if (type != null) {
				p.setAttribute("type", type.toString());
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

		PresenceChangeEvent event = new PresenceChangeEvent(packet);
		config.getEventBus().fire(event, this);

		if (type == null || type == StanzaType.available) {
			boolean added = addJid(jid);
			firePresenceChangeEvent(packet);
			if (added) {
				Packet p = new Presence(new Element("presence", new String[] { "to", "from" }, new String[] { jid.toString(),
						toJid.toString() }), toJid, jid);

				packetWriter.write(p);
			}
		} else if (StanzaType.unavailable == type) {
			removeJid(jid);
			firePresenceChangeEvent(packet);
			Packet p = new Presence(new Element("presence", new String[] { "to", "from", "type" }, new String[] {
					jid.toString(), toJid.toString(), StanzaType.unavailable.toString() }), toJid, jid);

			packetWriter.write(p);
		} else if (StanzaType.subscribe == type) {
			log.finest("Contact " + jid + " wants to subscribe PubSub");

			Packet presence = preparePresence(packet, StanzaType.subscribed);

			if (presence != null) {
				packetWriter.write(presence);
			}
			presence = preparePresence(packet, StanzaType.subscribe);
			if (presence != null) {
				packetWriter.write(presence);
			}
		} else if (StanzaType.unsubscribe == type || StanzaType.unsubscribed == type) {
			log.finest("Contact " + jid + " wants to unsubscribe PubSub");

			Packet presence = preparePresence(packet, StanzaType.unsubscribed);

			if (presence != null) {
				packetWriter.write(presence);
			}
			presence = preparePresence(packet, StanzaType.unsubscribe);
			if (presence != null) {
				packetWriter.write(presence);
			}
		}

	}

	public void removeBuddyVisibilityHandler(BuddyVisibilityHandler handler) {
		config.getEventBus().remove(BuddyVisibilityHandler.BuddyVisibilityEvent.TYPE, handler);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * 
	 * @return
	 */
	protected synchronized boolean removeJid(final JID jid) {
		if (jid == null) {
			return false;
		}

		final BareJID bareJid = jid.getBareJID();
		final String resource = jid.getResource();
		boolean removed = false;

		// onlineUsers.remove(jid);
		if (resource == null) {
			resources.remove(bareJid);
			BuddyVisibilityEvent event = new BuddyVisibilityEvent(bareJid, false);
			config.getEventBus().fire(event);
		} else {
			Set<String> resources = this.resources.get(bareJid);

			if (resources != null) {
				removed = resources.remove(resource);
				log.finest("Contact " + jid + " is removed from collection.");
				if (resources.size() == 0) {
					this.resources.remove(bareJid);
					BuddyVisibilityEvent event = new BuddyVisibilityEvent(bareJid, false);
					config.getEventBus().fire(event);
				}
			}
		}

		return removed;
	}

	public void removePresenceChangeHandler(PresenceChangeHandler handler) {
		config.getEventBus().remove(PresenceChangeEvent.TYPE, handler);
	}
}
