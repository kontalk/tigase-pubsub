/*
 * AbstractComponent.java
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

package tigase.component2;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component2.eventbus.DefaultEventBus;
import tigase.component2.eventbus.EventBus;
import tigase.component2.exceptions.ComponentException;
import tigase.component2.modules.Module;
import tigase.component2.modules.ModulesManager;
import tigase.conf.ConfigurationException;
import tigase.disco.XMPPService;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.StanzaType;

/**
 * Class description
 *
 *
 * @param <T>
 *
 */
public abstract class AbstractComponent<T extends ComponentConfig> extends AbstractMessageReceiver implements XMPPService {

	/** Field description */
	protected final T componentConfig;

	private final PacketWriter DEFAULT_WRITER = new PacketWriter() {
		@Override
		public void write(Collection<Packet> elements) {
			if (elements != null) {
				for (Packet element : elements) {
					if (element != null) {
						write(element);
					}
				}
			}
		}

		@Override
		public void write(Packet packet) {
			if (log.isLoggable(Level.FINER)) {
				if (log.isLoggable(Level.FINEST)) {
					// in case of most detail logging level add check for missing XMLNS in packets
					// this is only on lowest level as it is expensive as we need stacktrace to
					// make it easy to find source of this packet
					if (packet.getXMLNS() == null) {
						try {
							throw new Exception("Missing XMLNS");
						} catch (Exception ex) {
							log.log(Level.WARNING, "sending packet with not XMLNS set, should not occur, packet = " + packet.toString(), ex);
						}
					}
				}
				log.finer("Sent: " + packet.getElement());
			}
			addOutPacket(packet);
		}

	};

	protected final EventBus eventBus = new DefaultEventBus();

	/** Field description */
	protected final Logger log = Logger.getLogger(this.getClass().getName());

	/** Field description */
	protected final ModulesManager modulesManager = new ModulesManager();

	private final PacketWriter writer;

	/**
	 * Constructs ...
	 *
	 */
	public AbstractComponent() {
		this(null);
	}

	/**
	 * Constructs ...
	 *
	 *
	 * @param writer
	 */
	public AbstractComponent(PacketWriter writer) {
		this.writer = (writer != null) ? writer : DEFAULT_WRITER;
		this.componentConfig = createComponentConfigInstance(this);
	}

	/**
	 * Method description
	 *
	 *
	 * @param abstractComponent
	 *
	 * @return
	 */
	protected abstract T createComponentConfigInstance(AbstractComponent<?> abstractComponent);

	/**
	 * Method description
	 *
	 *
	 * @param params
	 *
	 * @return
	 */
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		final Map<String, Object> props = super.getDefaults(params);
		Map<String, Object> x = componentConfig.getDefaults(props);

		if (x != null) {
			props.putAll(x);
		}

		return props;
	}

	;

	public EventBus getEventBus() {
		return eventBus;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	protected PacketWriter getWriter() {
		return writer;
	}

	/**
	 * Is this component discoverable by disco#items for domain by non admin
	 * users
	 *
	 * @return true - if yes
	 */
	public abstract boolean isDiscoNonAdmin();

	public boolean isRegistered(final Class<? extends Module> moduleClass) {
		return this.modulesManager.isRegistered(moduleClass);
	}

	/**
	 * @param packet
	 */
	protected void processCommandPacket(Packet packet) {
		Queue<Packet> results = new ArrayDeque<Packet>();

		processScriptCommand(packet, results);
		if (results.size() > 0) {
			for (Packet res : results) {

				// No more recurrential calls!!
				addOutPacketNB(res);

				// processPacket(res);
			} // end of for ()
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 */
	@Override
	public void processPacket(Packet packet) {
		// if (packet.isCommand()) {
		// processCommandPacket(packet);
		// } else {
		processStanzaPacket(packet);
		// }
	}

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 */
	protected void processStanzaPacket(final Packet packet) {
		try {
			boolean handled = this.modulesManager.process(packet, getWriter());

			if (!handled) {
				final String t = packet.getElement().getAttributeStaticStr(Packet.TYPE_ATT);
				final StanzaType type = (t == null) ? null : StanzaType.valueof(t);

				if (type != StanzaType.error) {
					throw new ComponentException(Authorization.FEATURE_NOT_IMPLEMENTED);
				} else {
					if (log.isLoggable(Level.FINER)) {
						log.finer(packet.getElemName() + " stanza with type='error' ignored");
					}
				}
			}
		} catch (TigaseStringprepException e) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, e.getMessage() + " when processing " + packet.toString(), e);
			}
			sendException(packet, new ComponentException(Authorization.JID_MALFORMED));
		} catch (ComponentException e) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, e.getMessageWithPosition() + " when processing " + packet.toString(), e);
			}
			sendException(packet, e);
		} catch (Exception e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage() + " when processing " + packet.toString(), e);
			}
			sendException(packet, new ComponentException(Authorization.INTERNAL_SERVER_ERROR));
		}
	}

	public <M extends Module> M registerModule(final M module) {
		return this.modulesManager.register(module, false);
	}

	public <M extends Module> M registerModule(final M module, boolean skipIfRegistered) {
		return this.modulesManager.register(module, skipIfRegistered);
	}

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 * @param e
	 */
	protected void sendException(final Packet packet, final ComponentException e) {
		try {
			final String t = packet.getElement().getAttributeStaticStr(Packet.TYPE_ATT);

			if ((t != null) && (t == "error")) {
				if (log.isLoggable(Level.FINER)) {
					log.finer(packet.getElemName() + " stanza already with type='error' ignored");
				}

				return;
			}

			Packet result = e.makeElement(packet, true);
			Element el = result.getElement();

			el.setXMLNS( Packet.CLIENT_XMLNS);
			el.setAttribute("from", BareJID.bareJIDInstance(el.getAttributeStaticStr(Packet.FROM_ATT)).toString());
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Sending back: " + result.toString());
			}
			getWriter().write(result);
		} catch (Exception e1) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Problem during generate error response", e1);
			}
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param props
	 * @throws tigase.conf.ConfigurationException
	 */
	@Override
	public void setProperties(Map<String, Object> props) throws ConfigurationException {
		super.setProperties(props);
		componentConfig.setProperties(props);
	}

	@Override
	public void updateServiceEntity() {
		super.updateServiceEntity();
		this.updateServiceDiscoveryItem(getName(), null, getDiscoDescription(), !isDiscoNonAdmin());
	}

}
