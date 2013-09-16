package tigase.pubsub.modules.ext.presence;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import tigase.component.PacketWriter;
import tigase.component.eventbus.Event;
import tigase.component.eventbus.EventHandler;
import tigase.component.eventbus.EventType;
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

			private final JID jid;

			private final String node;

			private final Packet presenceStanza;

			public LoginToNodeEvent(String node, Packet presenceStanza) {
				super(TYPE);
				this.jid = presenceStanza.getStanzaFrom();
				this.node = node;
				this.presenceStanza = presenceStanza;
			}

			@Override
			protected void dispatch(LoginToNodeHandler handler) {
				handler.onLoginToNode(jid, node, presenceStanza);
			}

			public JID getJid() {
				return jid;
			}

			public String getNode() {
				return node;
			}

			public Packet getPresenceStanza() {
				return presenceStanza;
			}

		}

		void onLoginToNode(JID jid, String node, Packet presenceStanza);
	}

	public interface LogoffFromNodeHandler extends EventHandler {

		public static class LogoffFromNodeEvent extends Event<LogoffFromNodeHandler> {

			public static final EventType<LogoffFromNodeHandler> TYPE = new EventType<LogoffFromNodeHandler>();

			private final JID jid;

			private final String node;

			private final Packet presenceStanza;

			public LogoffFromNodeEvent(JID jid, String node, Packet presenceStanza) {
				super(TYPE);
				this.jid = jid;
				this.node = node;
				this.presenceStanza = presenceStanza;
			}

			@Override
			protected void dispatch(LogoffFromNodeHandler handler) {
				handler.onLogoffFromNode(jid, node, presenceStanza);
			}

			public JID getJid() {
				return jid;
			}

			public String getNode() {
				return node;
			}

			public Packet getPresenceStanza() {
				return presenceStanza;
			}

		}

		void onLogoffFromNode(JID jid, String node, Packet presenceStanza);
	}

	public interface UpdatePresenceHandler extends EventHandler {

		public static class UpdatePresenceEvent extends Event<UpdatePresenceHandler> {

			public static final EventType<UpdatePresenceHandler> TYPE = new EventType<UpdatePresenceHandler>();

			private final JID jid;

			private final String node;

			private final Packet presenceStanza;

			public UpdatePresenceEvent(String node, Packet presenceStanza) {
				super(TYPE);
				this.jid = presenceStanza.getStanzaFrom();
				this.node = node;
				this.presenceStanza = presenceStanza;
			}

			@Override
			protected void dispatch(UpdatePresenceHandler handler) {
				handler.onPresenceUpdate(jid, node, presenceStanza);
			}

			public JID getJid() {
				return jid;
			}

			public String getNode() {
				return node;
			}

			public Packet getPresenceStanza() {
				return presenceStanza;
			}

		}

		void onPresenceUpdate(JID jid, String node, Packet presenceStanza);
	}

	public static final String XMLNS_EXTENSION = "tigase:pubsub:1";

	/**
	 * (NodeName, {OccupantJID})
	 */
	private final Map<String, Set<JID>> occupants = new ConcurrentHashMap<String, Set<JID>>();

	private final PresenceChangeHandler presenceChangeHandler = new PresenceChangeHandler() {

		@Override
		public void onPresenceChange(Packet packet) {
			process(packet);
		}
	};

	/**
	 * (BareJID, (Resource, (PubSubNodeName, PresencePacket)))
	 */
	private final Map<BareJID, Map<String, Map<String, Packet>>> presences = new ConcurrentHashMap<BareJID, Map<String, Map<String, Packet>>>();

	private PubSubConfig pubsubContext;

	private PacketWriter writer;

	public PresencePerNodeExtension(PubSubConfig config, PacketWriter packetWriter) {
		this.pubsubContext = config;
		this.writer = packetWriter;

		this.pubsubContext.getEventBus().addHandler(PresenceCollectorModule.PresenceChangeHandler.PresenceChangeEvent.TYPE,
				presenceChangeHandler);
	}

	private void addJidToOccupants(String nodeName, JID jid) {
		Set<JID> occs = occupants.get(nodeName);
		if (occs == null) {
			occs = new HashSet<JID>();
			occupants.put(nodeName, occs);
		}
		occs.add(jid);
	}

	public void addLoginToNodeHandler(LoginToNodeHandler handler) {
		pubsubContext.getEventBus().addHandler(LoginToNodeEvent.TYPE, handler);
	}

	public void addLogoffFromNodeHandler(LogoffFromNodeHandler handler) {
		pubsubContext.getEventBus().addHandler(LogoffFromNodeEvent.TYPE, handler);
	}

	private void addPresence(String nodeName, Packet packet) {
		final JID sender = packet.getPacketFrom();
		Map<String, Map<String, Packet>> resources = this.presences.get(sender.getBareJID());
		if (resources == null) {
			resources = new ConcurrentHashMap<String, Map<String, Packet>>();
			this.presences.put(sender.getBareJID(), resources);
		}
		Map<String, Packet> nodesPresence = resources.get(sender.getResource());
		if (nodesPresence == null) {
			nodesPresence = new ConcurrentHashMap<String, Packet>();
			resources.put(sender.getResource(), nodesPresence);
		}

		boolean isUpdate = nodesPresence.containsKey(nodeName);
		nodesPresence.put(nodeName, packet);
		addJidToOccupants(nodeName, sender);

		Event<?> event = isUpdate ? new UpdatePresenceEvent(nodeName, packet) : new LoginToNodeEvent(nodeName, packet);
		pubsubContext.getEventBus().fire(event, PresencePerNodeExtension.this);
	}

	public void addUpdatePresenceHandler(UpdatePresenceHandler handler) {
		pubsubContext.getEventBus().addHandler(UpdatePresenceEvent.TYPE, handler);
	}

	public Collection<JID> getNodeOccupants(String nodeName) {
		Set<JID> occs = occupants.get(nodeName);
		if (occs == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableCollection(occs);
	}

	private void intProcessLogoffFrom(BareJID bareJID, Map<String, Map<String, Packet>> resources, Packet presenceStanza) {
		if (resources == null)
			return;
		for (Entry<String, Map<String, Packet>> res : resources.entrySet()) {
			final JID jid = JID.jidInstanceNS(bareJID, res.getKey());
			Map<String, Packet> nodes = res.getValue();
			intProcessLogoffFrom(jid, nodes, presenceStanza);
		}
	}

	private void intProcessLogoffFrom(JID sender, Map<String, Packet> nodes, Packet presenceStanza) {
		if (nodes == null)
			return;
		for (String node : nodes.keySet()) {
			removeJidFromOccupants(node, sender);

			LogoffFromNodeEvent event = new LogoffFromNodeEvent(sender, node, presenceStanza);
			pubsubContext.getEventBus().fire(event, PresencePerNodeExtension.this);
		}
	}

	protected void process(Packet packet) {
		Element pubsubExtElement = packet.getElement().getChild("pubsub", XMLNS_EXTENSION);
		if (pubsubExtElement == null)
			return;

		final String nodeName = pubsubExtElement.getAttributeStaticStr("node");
		final StanzaType type = packet.getType();

		if (type == null || type == StanzaType.available) {
			addPresence(nodeName, packet);
		} else if (StanzaType.unavailable == type) {
			removePresence(nodeName, packet.getStanzaFrom(), packet);
		}
	}

	private void removeJidFromOccupants(String node, JID jid) {
		Set<JID> occs = occupants.get(node);
		if (occs != null) {
			occs.remove(jid);
		}
		if (occs.isEmpty()) {
			occupants.remove(node);
		}
	}

	public void removeLoginToNodeHandler(LoginToNodeHandler handler) {
		pubsubContext.getEventBus().remove(LoginToNodeEvent.TYPE, handler);
	}

	public void removeLogoffFromNodeHandler(LogoffFromNodeHandler handler) {
		pubsubContext.getEventBus().remove(LogoffFromNodeEvent.TYPE, handler);
	}

	private void removePresence(String nodeName, JID sender, Packet presenceStanza) {
		if (sender.getResource() == null) {
			// barejid is gone. logoff from everything
			Map<String, Map<String, Packet>> removed = this.presences.remove(sender.getBareJID());
			intProcessLogoffFrom(sender.getBareJID(), removed, presenceStanza);
		} else {
			// resource is gone
			Map<String, Map<String, Packet>> resources = this.presences.get(sender.getBareJID());
			if (resources != null) {
				Map<String, Packet> nodesPresence = resources.get(sender.getResource());
				if (nodesPresence != null && nodeName != null) {
					// logoff from node
					nodesPresence.remove(nodeName);
					removeJidFromOccupants(nodeName, sender);
					LogoffFromNodeEvent event = new LogoffFromNodeEvent(sender, nodeName, presenceStanza);
					pubsubContext.getEventBus().fire(event, PresencePerNodeExtension.this);
					if (nodeName.isEmpty()) {
						resources.remove(sender.getResource());
					}
				} else if (nodesPresence != null) {
					// resource is gone. logoff from all related nodes
					Map<String, Packet> removed = resources.remove(sender.getResource());
					intProcessLogoffFrom(sender, removed, presenceStanza);
				}
				if (resources.isEmpty()) {
					this.presences.remove(sender.getBareJID());
				}
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
