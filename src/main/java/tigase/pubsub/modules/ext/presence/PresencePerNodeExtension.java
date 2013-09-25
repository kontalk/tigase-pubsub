package tigase.pubsub.modules.ext.presence;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component2.PacketWriter;
import tigase.component2.eventbus.Event;
import tigase.component2.eventbus.EventHandler;
import tigase.component2.eventbus.EventType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.modules.PresenceCollectorModule;
import tigase.pubsub.modules.PresenceCollectorModule.PresenceChangeHandler;
import tigase.pubsub.modules.ext.presence.PresencePerNodeExtension.LoginToNodeHandler.LoginToNodeEvent;
import tigase.pubsub.modules.ext.presence.PresencePerNodeExtension.LogoffFromNodeHandler.LogoffFromNodeEvent;
import tigase.pubsub.modules.ext.presence.PresencePerNodeExtension.UpdatePresenceHandler.UpdatePresenceEvent;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

public class PresencePerNodeExtension {

	public interface LoginToNodeHandler extends EventHandler {

		public static class LoginToNodeEvent extends Event<LoginToNodeHandler> {

			public static final EventType<LoginToNodeHandler> TYPE = new EventType<LoginToNodeHandler>();

			private final String node;

			private final JID occupantJID;

			private final Packet presenceStanza;

			private final BareJID serviceJID;

			public LoginToNodeEvent(BareJID serviceJID, String node, Packet presenceStanza) {
				super(TYPE);
				this.occupantJID = presenceStanza.getStanzaFrom();
				this.node = node;
				this.presenceStanza = presenceStanza;
				this.serviceJID = serviceJID;
			}

			@Override
			protected void dispatch(LoginToNodeHandler handler) {
				handler.onLoginToNode(serviceJID, node, occupantJID, presenceStanza);
			}

			public String getNode() {
				return node;
			}

			public JID getOccupantJID() {
				return occupantJID;
			}

			public Packet getPresenceStanza() {
				return presenceStanza;
			}

			public BareJID getServiceJID() {
				return serviceJID;
			}

		}

		void onLoginToNode(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza);
	}

	public interface LogoffFromNodeHandler extends EventHandler {

		public static class LogoffFromNodeEvent extends Event<LogoffFromNodeHandler> {

			public static final EventType<LogoffFromNodeHandler> TYPE = new EventType<LogoffFromNodeHandler>();

			private final String node;

			private final JID occupantJID;

			private final Packet presenceStanza;

			private final BareJID serviceJID;

			public LogoffFromNodeEvent(BareJID serviceJID, String node, JID occupandJID, Packet presenceStanza) {
				super(TYPE);
				this.occupantJID = occupandJID;
				this.node = node;
				this.presenceStanza = presenceStanza;
				this.serviceJID = serviceJID;
			}

			@Override
			protected void dispatch(LogoffFromNodeHandler handler) {
				handler.onLogoffFromNode(serviceJID, node, occupantJID, presenceStanza);
			}

			public String getNode() {
				return node;
			}

			public JID getOccupantJID() {
				return occupantJID;
			}

			public Packet getPresenceStanza() {
				return presenceStanza;
			}

			public BareJID getServiceJID() {
				return serviceJID;
			}

		}

		void onLogoffFromNode(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza);
	}

	public interface UpdatePresenceHandler extends EventHandler {

		public static class UpdatePresenceEvent extends Event<UpdatePresenceHandler> {

			public static final EventType<UpdatePresenceHandler> TYPE = new EventType<UpdatePresenceHandler>();

			private final String node;

			private final JID occupantJID;

			private final Packet presenceStanza;

			private final BareJID serviceJID;

			public UpdatePresenceEvent(BareJID serviceJID, String node, Packet presenceStanza) {
				super(TYPE);
				this.occupantJID = presenceStanza.getStanzaFrom();
				this.node = node;
				this.presenceStanza = presenceStanza;
				this.serviceJID = serviceJID;
			}

			@Override
			protected void dispatch(UpdatePresenceHandler handler) {
				handler.onPresenceUpdate(serviceJID, node, occupantJID, presenceStanza);
			}

			public String getNode() {
				return node;
			}

			public JID getOccupantJID() {
				return occupantJID;
			}

			public Packet getPresenceStanza() {
				return presenceStanza;
			}

			public BareJID getServiceJID() {
				return serviceJID;
			}

		}

		void onPresenceUpdate(BareJID serviceJID, String node, JID occupantJID, Packet presenceStanza);
	}

	public static final String XMLNS_EXTENSION = "tigase:pubsub:1";

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	/**
	 * (ServiceJID, (NodeName, {OccupantJID}))
	 */
	private final Map<BareJID, Map<String, Set<JID>>> occupants = new ConcurrentHashMap<BareJID, Map<String, Set<JID>>>();

	private final PresenceChangeHandler presenceChangeHandler = new PresenceChangeHandler() {

		@Override
		public void onPresenceChange(Packet packet) {
			process(packet);
		}
	};

	/**
	 * (OccupantBareJID, (Resource, (ServiceJID, (PubSubNodeName,
	 * PresencePacket))))
	 */
	private final Map<BareJID, Map<String, Map<BareJID, Map<String, Packet>>>> presences = new ConcurrentHashMap<BareJID, Map<String, Map<BareJID, Map<String, Packet>>>>();

	private PubSubConfig pubsubContext;

	public PresencePerNodeExtension(PubSubConfig config, PacketWriter packetWriter) {
		this.pubsubContext = config;

		this.pubsubContext.getEventBus().addHandler(PresenceCollectorModule.PresenceChangeHandler.PresenceChangeEvent.TYPE,
				presenceChangeHandler);
	}

	void addJidToOccupants(BareJID serviceJID, String nodeName, JID jid) {
		Map<String, Set<JID>> services = occupants.get(serviceJID);
		if (services == null) {
			services = new ConcurrentHashMap<String, Set<JID>>();
			occupants.put(serviceJID, services);
		}
		Set<JID> occs = services.get(nodeName);
		if (occs == null) {
			occs = new HashSet<JID>();
			services.put(nodeName, occs);
		}
		occs.add(jid);
	}

	public void addLoginToNodeHandler(LoginToNodeHandler handler) {
		pubsubContext.getEventBus().addHandler(LoginToNodeEvent.TYPE, handler);
	}

	public void addLogoffFromNodeHandler(LogoffFromNodeHandler handler) {
		pubsubContext.getEventBus().addHandler(LogoffFromNodeEvent.TYPE, handler);
	}

	void addPresence(BareJID serviceJID, String nodeName, Packet packet) {
		final JID sender = packet.getStanzaFrom();

		Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(sender.getBareJID());
		if (resources == null) {
			resources = new ConcurrentHashMap<String, Map<BareJID, Map<String, Packet>>>();
			this.presences.put(sender.getBareJID(), resources);
		}

		Map<BareJID, Map<String, Packet>> services = resources.get(sender.getResource());
		if (services == null) {
			services = new ConcurrentHashMap<BareJID, Map<String, Packet>>();
			resources.put(sender.getResource(), services);
		}

		Map<String, Packet> nodesPresence = services.get(serviceJID);
		if (nodesPresence == null) {
			nodesPresence = new ConcurrentHashMap<String, Packet>();
			services.put(serviceJID, nodesPresence);
		}

		boolean isUpdate = nodesPresence.containsKey(nodeName);
		nodesPresence.put(nodeName, packet);
		addJidToOccupants(serviceJID, nodeName, sender);

		Event<?> event = isUpdate ? new UpdatePresenceEvent(serviceJID, nodeName, packet) : new LoginToNodeEvent(serviceJID,
				nodeName, packet);
		pubsubContext.getEventBus().fire(event, PresencePerNodeExtension.this);
	}

	public void addUpdatePresenceHandler(UpdatePresenceHandler handler) {
		pubsubContext.getEventBus().addHandler(UpdatePresenceEvent.TYPE, handler);
	}

	public Collection<JID> getNodeOccupants(BareJID serviceJID, String nodeName) {
		Map<String, Set<JID>> services = occupants.get(serviceJID);
		if (services == null)
			return Collections.emptyList();
		Set<JID> occs = services.get(nodeName);
		if (occs == null)
			return Collections.emptyList();
		return Collections.unmodifiableCollection(occs);
	}

	public Collection<Packet> getPresence(BareJID serviceJID, String nodeName, BareJID occupantJID) {
		Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(occupantJID);
		if (resources == null)
			return Collections.emptyList();

		Set<Packet> prs = new HashSet<Packet>();

		for (Map<BareJID, Map<String, Packet>> services : resources.values()) {
			Map<String, Packet> nodes = services.get(serviceJID);
			if (nodes != null && nodes.containsKey(nodeName)) {
				prs.add(nodes.get(nodeName));
			}
		}

		return prs;
	}

	public Packet getPresence(BareJID serviceJID, String nodeName, JID occupantJID) {
		Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(occupantJID.getBareJID());
		if (resources == null)
			return null;
		Map<BareJID, Map<String, Packet>> services = resources.get(occupantJID.getResource());
		if (services == null)
			return null;
		Map<String, Packet> nodes = services.get(serviceJID);
		if (nodes == null)
			return null;
		return nodes.get(nodeName);
	}

	private void intProcessLogoffFrom(BareJID serviceJID, JID sender, Map<String, Packet> nodes, Packet presenceStanza) {
		if (nodes == null)
			return;
		for (String node : nodes.keySet()) {
			removeJidFromOccupants(serviceJID, node, sender);

			LogoffFromNodeEvent event = new LogoffFromNodeEvent(serviceJID, node, sender, presenceStanza);
			pubsubContext.getEventBus().fire(event, PresencePerNodeExtension.this);
		}
	}

	protected void process(Packet packet) {
		Element pubsubExtElement = packet.getElement().getChild("pubsub", XMLNS_EXTENSION);
		if (pubsubExtElement == null)
			return;

		final String nodeName = pubsubExtElement.getAttributeStaticStr("node");
		final StanzaType type = packet.getType();
		final BareJID serviceJID = packet.getStanzaTo().getBareJID();

		if (type == null || type == StanzaType.available) {
			addPresence(serviceJID, nodeName, packet);
		} else if (StanzaType.unavailable == type) {
			removePresence(serviceJID, nodeName, packet.getStanzaFrom(), packet);
		}
	}

	void removeJidFromOccupants(BareJID serviceJID, String node, JID jid) {
		Map<String, Set<JID>> services = occupants.get(serviceJID);
		if (services != null) {
			Set<JID> occs = services.get(node);
			if (occs != null) {
				occs.remove(jid);
				if (occs.isEmpty()) {
					occupants.remove(node);
				}
			}
			if (services.isEmpty())
				occupants.remove(serviceJID);
		}
	}

	public void removeLoginToNodeHandler(LoginToNodeHandler handler) {
		pubsubContext.getEventBus().remove(LoginToNodeEvent.TYPE, handler);
	}

	public void removeLogoffFromNodeHandler(LogoffFromNodeHandler handler) {
		pubsubContext.getEventBus().remove(LogoffFromNodeEvent.TYPE, handler);
	}

	void removePresence(BareJID serviceJID, String nodeName, JID sender, Packet presenceStanza) {
		if (sender.getResource() == null) {
			if (log.isLoggable(Level.WARNING))
				log.warning("Skip processing presence from BareJID " + sender);
		} else {
			// resource gone
			Map<String, Map<BareJID, Map<String, Packet>>> resources = this.presences.get(sender.getBareJID());
			if (resources != null) {
				Map<BareJID, Map<String, Packet>> services = resources.get(sender.getResource());
				if (services != null) {
					Map<String, Packet> nodes = services.get(serviceJID);
					if (nodes != null && nodeName != null) {
						// manual logoff from specific node
						nodes.remove(nodeName);
						removeJidFromOccupants(serviceJID, nodeName, sender);

						LogoffFromNodeEvent event = new LogoffFromNodeEvent(serviceJID, nodeName, sender, presenceStanza);
						pubsubContext.getEventBus().fire(event, PresencePerNodeExtension.this);

						if (nodes.isEmpty())
							services.remove(serviceJID);

					} else if (nodes != null) {
						// resource is gone. logoff from all nodes
						Map<String, Packet> removed = services.remove(serviceJID);
						intProcessLogoffFrom(serviceJID, sender, removed, presenceStanza);
					}
					if (services.isEmpty())
						resources.remove(sender.getResource());
				}
				if (resources.isEmpty())
					this.presences.remove(sender.getBareJID());
			}
		}
	}

	public void removeUpdatePresenceHandler(UpdatePresenceHandler handler) {
		pubsubContext.getEventBus().remove(UpdatePresenceEvent.TYPE, handler);
	}

	/*
	 * <pubsub xmlns='tigase:pubsub:1' node='nazwa node'/>
	 */

}
