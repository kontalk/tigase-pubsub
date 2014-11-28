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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
import tigase.pubsub.modules.PresenceCollectorModule.CapsChangeHandler.CapsChangeEvent;
import tigase.pubsub.modules.PresenceCollectorModule.PresenceChangeHandler.PresenceChangeEvent;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;
import tigase.xmpp.impl.PresenceCapabilitiesManager;

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

	public interface CapsChangeHandler extends EventHandler {
		
		public static class CapsChangeEvent extends Event<CapsChangeHandler> {

			public static final EventType<CapsChangeHandler> TYPE = new EventType<CapsChangeHandler>();
			
			private final BareJID serviceJid;
			private final JID buddyJid;
			private final String[] newCaps;
			private final String[] oldCaps;
			private final Set<String> newFeatures;
			
			public CapsChangeEvent(BareJID serviceJid, JID buddyJid, String[] newCaps, String[] oldCaps, Set<String> newFeatures) {
				super(TYPE);
				
				this.serviceJid = serviceJid;
				this.buddyJid = buddyJid;
				this.newCaps = newCaps;
				this.oldCaps = oldCaps;
				this.newFeatures = newFeatures;
			}
			
			@Override
			protected void dispatch(CapsChangeHandler handler) {
				handler.onCapsChange(serviceJid, buddyJid, newCaps, oldCaps, newFeatures);
			}
			
		}
		
		void onCapsChange(BareJID serviceJid, JID buddyJid, String[] newCaps, String[] oldCaps, Set<String> newFeatures);	
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

	private static final String[] EMPTY_CAPS = {};

	private static final ConcurrentMap<String,String[]> CAPS_MAP = new ConcurrentHashMap<String,String[]>();
	
//	private final Map<BareJID, Set<String>> resources = new HashMap<BareJID, Set<String>>();

	private final ConcurrentMap<BareJID, ConcurrentMap<BareJID, Map<String,String[]>>> presenceByService = new ConcurrentHashMap<>();
	private final CapsModule capsModule;
	
	public PresenceCollectorModule(PubSubConfig config, PacketWriter packetWriter, CapsModule capsModule) {
		super(config, packetWriter);
		this.capsModule = capsModule;
	}

	public void addBuddyVisibilityHandler(BuddyVisibilityHandler handler) {
		config.getEventBus().addHandler(BuddyVisibilityHandler.BuddyVisibilityEvent.TYPE, handler);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param serviceJid
	 * @param jid
	 * @param caps
	 * 
	 * @return
	 */
	public boolean addJid(final BareJID serviceJid, final JID jid, String[] caps) {
		if (jid == null) {
			return false;
		}

		// here we are using CAPS_MAP to cache instances of CAPS to reduce memory footprint
		if (caps == null || caps.length == 0) {
			caps = EMPTY_CAPS;
		}
		else {
			StringBuilder sb = new StringBuilder();
			for (String item : caps) {
				sb.append(item);
			}
			String key = sb.toString();
			String[] cachedCaps = CAPS_MAP.putIfAbsent(key, caps);
			if (cachedCaps != null) {
				caps = cachedCaps;
			}
		}
		
		boolean added = false;
		final BareJID bareJid = jid.getBareJID();
		final String resource = jid.getResource();

		ConcurrentMap<BareJID, Map<String,String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			ConcurrentMap<BareJID, Map<String,String[]>> tmp = new ConcurrentHashMap<>();
			presenceByUser = presenceByService.putIfAbsent(serviceJid, tmp);
			if (presenceByUser == null) {
				presenceByUser = tmp;
			}
		}
		
		if (resource != null) {
			Map<String,String[]> resources = presenceByUser.get(bareJid);

			if (resources == null) {
				Map<String,String[]> tmp = new HashMap<>();				
				resources = presenceByUser.putIfAbsent(bareJid, tmp);
				if (resources == null)
					resources = tmp;
			}
			
			String[] oldCaps;
			synchronized (resources) {
				oldCaps = resources.put(resource, caps);
				added = oldCaps == null;
			}
			log.finest("for service " + serviceJid + " - Contact " + jid + " is collected.");
			
			// we are firing CapsChangeEvent only for PEP services
			if (this.config.isPepPeristent() && this.config.isSendLastPublishedItemOnPresence()
					&& serviceJid.getLocalpart() != null && oldCaps != caps && caps != null) {
				// calculating new features and firing event
				Set<String> newFeatures = new HashSet<String>();
				for (String node : caps) {
					// ignore searching for features if same node exists in old caps
					if (oldCaps != null && Arrays.binarySearch(oldCaps, node) >= 0)
						continue;
					
					String[] features = PresenceCapabilitiesManager.getNodeFeatures(node);
					if (features != null) {
						for (String feature : features) {
							newFeatures.add(feature);
						}
					}
				}
				if (oldCaps != null) {
					for (String node : oldCaps) {
						// ignore searching for features if same node exists in new caps
						if (Arrays.binarySearch(caps, node) >= 0)
							continue;
						String[] features = PresenceCapabilitiesManager.getNodeFeatures(node);
						if (features != null) {
							for (String feature : features) {
								newFeatures.remove(feature);
							}
						}
					}
				}

				if (!newFeatures.isEmpty()) {
					this.config.getEventBus().fire(new CapsChangeEvent(serviceJid, jid, caps, oldCaps, newFeatures));
				}
			}
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
	public List<JID> getAllAvailableJids(final BareJID serviceJid) {
		ArrayList<JID> result = new ArrayList<JID>();

		ConcurrentMap<BareJID,Map<String,String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser != null) {
			for (Entry<BareJID, Map<String,String[]>> entry : presenceByUser.entrySet()) {
				for (String reource : entry.getValue().keySet()) {
					JID jid = JID.jidInstanceNS(entry.getKey(), reource);
					if (isAvailableLocally(jid))
						result.add(jid);
				}
			}			
		}
	
		return result;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param bareJid
	 * @return
	 */
	public List<JID> getAllAvailableResources(final BareJID serviceJid, final BareJID bareJid) {
		final List<JID> result = new ArrayList<JID>();
		ConcurrentMap<BareJID,Map<String,String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return result;
		}
		
		final Map<String,String[]> jid_resources = presenceByUser.get(bareJid);

		if (jid_resources != null) {
			for (String reource : jid_resources.keySet()) {
				JID jid = JID.jidInstanceNS(bareJid, reource);
				if (isAvailableLocally(jid))
					result.add(jid);
			}
		}

		return result;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param serviceJid
	 * @param bareJid
	 * @param feature
	 * @return
	 */
	public List<JID> getAllAvailableJidsWithFeature(final BareJID serviceJid, final String feature) {
		final List<JID> result = new ArrayList<>();
		ConcurrentMap<BareJID,Map<String,String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return result;
		}
			
		Set<String> nodesWithFeature = PresenceCapabilitiesManager.getNodesWithFeature(feature);
		for (Map.Entry<BareJID,Map<String,String[]>> pe : presenceByUser.entrySet()) {
			Map<String,String[]> jid_resources = pe.getValue();
			if (jid_resources != null) {
				synchronized (jid_resources) {
					for (Map.Entry<String, String[]> e : jid_resources.entrySet()) {
						String[] caps = e.getValue();
						boolean match = false;
						for (String node : caps) {
							match |= nodesWithFeature.contains(node);
						}
						if (match) {
							JID jid = JID.jidInstanceNS(pe.getKey(), e.getKey());
							if (isAvailableLocally(jid))
								result.add(jid);
						}
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

	protected boolean isAvailableLocally(JID jid) {
		return true;
	}
	
	/**
	 * Method description
	 * 
	 * 
	 * @param bareJid 
	 * @return
	 */
	public boolean isJidAvailable(final BareJID serviceJid, final BareJID bareJid) {
		ConcurrentMap<BareJID,Map<String,String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null) {
			return false;
		}		
		final Map<String,String[]> resources = presenceByUser.get(bareJid);

		return (resources != null) && (resources.size() > 0);
	}

	private Packet preparePresence(final Packet presence, StanzaType type) {
		JID to = presence.getTo();
		JID from = presence.getStanzaFrom();

		if ( from != null && to != null && !((from.getBareJID()).equals( to.getBareJID())) ){
			JID jid = from.copyWithoutResource();
			Element p = new Element( "presence", new String[] { "to", "from", Packet.XMLNS_ATT },
															 new String[] { jid.toString(), to.toString(), Packet.CLIENT_XMLNS } );

			if ( type != null ){
				p.setAttribute("type", type.toString());
			}

			return new Presence(p, to, from);
		}

		return null;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 * @throws PubSubException
	 */
	@Override
	public void process( Packet packet ) throws PubSubException {
		final StanzaType type = packet.getType();
		final JID jid = packet.getStanzaFrom();
		final JID toJid = packet.getStanzaTo();
		// why it is here if it is also below?
//		PresenceChangeEvent event = new PresenceChangeEvent( packet );
//		config.getEventBus().fire( event, this );

		if ( type == null || type == StanzaType.available ){
			String[] caps = config.isPepPeristent() ? capsModule.processPresence(packet) : null;
			boolean added = addJid( toJid.getBareJID(), jid, caps );
			firePresenceChangeEvent( packet );
			if ( added && packet.getStanzaTo().getLocalpart() == null ){
				Packet p = new Presence( new Element( "presence", new String[] { "to", "from", Packet.XMLNS_ATT },
																							new String[] { jid.toString(), toJid.toString(), Packet.CLIENT_XMLNS } ),
																 toJid, jid );

				packetWriter.write( p );
			}
		} else if ( StanzaType.unavailable == type ){
			removeJid( toJid.getBareJID(), jid );
			firePresenceChangeEvent( packet );
			if (packet.getStanzaTo().getLocalpart() == null) {
				Packet p = new Presence( new Element( "presence", new String[] { "to", "from", "type", Packet.XMLNS_ATT }, new String[] {
					jid.toString(), toJid.toString(), StanzaType.unavailable.toString(), Packet.CLIENT_XMLNS } ), toJid, jid );

				packetWriter.write( p );
			}
		} else if ( StanzaType.subscribe == type ){
			log.finest( "Contact " + jid + " wants to subscribe PubSub" );

			Packet presence = preparePresence( packet, StanzaType.subscribed );

			if ( presence != null ){
				packetWriter.write( presence );
			}
			presence = preparePresence( packet, StanzaType.subscribe );
			if ( presence != null ){
				packetWriter.write( presence );
			}
		} else if ( StanzaType.unsubscribe == type || StanzaType.unsubscribed == type ){
			log.finest( "Contact " + jid + " wants to unsubscribe PubSub" );

			Packet presence = preparePresence( packet, StanzaType.unsubscribed );

			if ( presence != null ){
				packetWriter.write( presence );
			}
			presence = preparePresence( packet, StanzaType.unsubscribe );
			if ( presence != null ){
				packetWriter.write( presence );
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
	protected boolean removeJid(final BareJID serviceJid, final JID jid) {
		if (jid == null) {
			return false;
		}

		final BareJID bareJid = jid.getBareJID();
		final String resource = jid.getResource();
		boolean removed = false;

		ConcurrentMap<BareJID,Map<String,String[]>> presenceByUser = presenceByService.get(serviceJid);
		if (presenceByUser == null)
			return false;
		
		// onlineUsers.remove(jid);
		if (resource == null) {
			presenceByUser.remove(bareJid);
			BuddyVisibilityEvent event = new BuddyVisibilityEvent(bareJid, false);
			config.getEventBus().fire(event);
		} else {
			Map<String,String[]> resources = presenceByUser.get(bareJid);

			if (resources != null) {
				synchronized (resources) {
					removed = resources.remove(resource) != null;
					log.finest("for service " + serviceJid + " - Contact " + jid + " is removed from collection.");
					if (resources.isEmpty()) {
						presenceByUser.remove(bareJid);
						BuddyVisibilityEvent event = new BuddyVisibilityEvent(bareJid, false);
						config.getEventBus().fire(event);
					}
				}
			}
		}

		return removed;
	}

	public void removePresenceChangeHandler(PresenceChangeHandler handler) {
		config.getEventBus().remove(PresenceChangeEvent.TYPE, handler);
	}
}
