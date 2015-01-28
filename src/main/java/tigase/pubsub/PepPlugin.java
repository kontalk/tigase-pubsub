/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007-2014 "Tigase, Inc." <office@tigase.com>
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

package tigase.pubsub;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.util.DNSResolver;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.JID;
import tigase.xmpp.NoConnectionIdException;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPException;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPProcessorIfc;
import tigase.xmpp.XMPPResourceConnection;

/**
 * Implements PubSub support for every local user account on it's bare jid using
 * local version of PubSub component.
 * 
 * @author andrzej
 */
public class PepPlugin extends XMPPProcessor implements XMPPProcessorIfc {

	private static final Logger log = Logger.getLogger(PepPlugin.class.getCanonicalName());
	
	protected static final String DISCO_INFO_XMLNS = "http://jabber.org/protocol/disco#info";
	protected static final String DISCO_ITEMS_XMLNS = "http://jabber.org/protocol/disco#items";
	protected static final String PUBSUB_XMLNS = "http://jabber.org/protocol/pubsub";
	
	private static final String ID = "pep";
	
	protected static final Element[] DISCO_FEATURES = { new Element("feature", new String[] {
			"var" }, new String[] { PUBSUB_XMLNS }),
			new Element("feature", new String[] { "var" }, new String[] { PUBSUB_XMLNS + "#owner" }),
			new Element("feature", new String[] { "var" }, new String[] { PUBSUB_XMLNS +
					"#publish" }),
			new Element("identity", new String[] { "category", "type" }, new String[] {
					"pubsub",
					"pep" }), };	
	
	protected static final String[][] ELEMENTS = { Iq.IQ_PUBSUB_PATH, new String[] { Presence.ELEM_NAME }, Iq.IQ_QUERY_PATH, Iq.IQ_QUERY_PATH };
	
	protected static final String[] XMLNSS = { PUBSUB_XMLNS, Presence.CLIENT_XMLNS, DISCO_ITEMS_XMLNS, DISCO_INFO_XMLNS };
	
	protected JID pubsubJid = null;
	
	protected boolean simplePepEnabled = false;
	protected final Set<String> simpleNodes = new HashSet<String>();
	
	@Override
	public void init(Map<String, Object> settings) throws TigaseDBException {
		super.init(settings);

		this.simpleNodes.add("http://jabber.org/protocol/tune");
		this.simpleNodes.add("http://jabber.org/protocol/mood");
		this.simpleNodes.add("http://jabber.org/protocol/activity");
		this.simpleNodes.add("http://jabber.org/protocol/geoloc");
		this.simpleNodes.add("urn:xmpp:avatar:data");
		this.simpleNodes.add("urn:xmpp:avatar:metadata");
		
		String defHost = DNSResolver.getDefaultHostname();
		pubsubJid = JID.jidInstanceNS("pubsub", defHost, null);
		
		if (settings.containsKey("simplePepEnabled")) {
			this.simplePepEnabled = (Boolean) settings.get("simple-pep-enabled");
		}
	}
	
	@Override
	public String id() {
		return ID;
	}

	@Override
	public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings) throws XMPPException {
		if (packet.getElemName() == Iq.ELEM_NAME) {
			processIq(packet, session, results);
		}
		if (packet.getElemName() == Presence.ELEM_NAME) {
			processPresence(packet, session, results);
		}
	}

	@Override
	public Element[] supDiscoFeatures(final XMPPResourceConnection session) {
		return DISCO_FEATURES;
	}
	
	@Override
	public String[][] supElementNamePaths() {
		return ELEMENTS;
	}

	@Override
	public String[] supNamespaces() {
		return XMLNSS;
	}

	protected void processIq(Packet packet, XMPPResourceConnection session, Queue<Packet> results) throws XMPPException {
		if (session != null && session.isServerSession()) {
			return;
		}
		
		Element pubsubEl = packet.getElement().findChildStaticStr(Iq.IQ_PUBSUB_PATH);
		if (pubsubEl != null && simplePepEnabled) {
			List<Element> children = pubsubEl.getChildren();
			boolean simple = false;
			for (Element child : children) {
				String node = child.getAttributeStaticStr("node");
				simple |= simpleNodes.contains(node);
			}
			if (simple) {
				// if simple and simple support is enabled we are leaving support
				// for this node to default pep plugin (not presistent pep)
				return;
			}
		}
		
		// forwarding packet to particular resource
		if (packet.getStanzaTo() != null && packet.getStanzaTo().getResource() != null) {	
			if (pubsubEl != null && pubsubEl.getXMLNS() == PUBSUB_XMLNS) {
				Packet result = null;
				if (session != null) {
					XMPPResourceConnection con = session.getParentSession().getResourceForResource(
							packet.getStanzaTo().getResource());
					
					if (con != null) {
						result = packet.copyElementOnly();
						result.setPacketTo(con.getConnectionId());
						
						// In most cases this might be skept, however if there is a
						// problem during packet delivery an error might be sent back						
						result.setPacketFrom(packet.getTo());
					}
				}
				// if result was not generated yet, this means that session is null or
				// connection is null, so recipient is unavailable
				// in theory we could skip generation of error for performance reason
				// as sending iq/error for iq/result will make no difference for component
				// but for now let's send response to be compatible with specification
				if (result == null) {
					result = Authorization.RECIPIENT_UNAVAILABLE.getResponseMessage(packet, 
								"The recipient is no longer available.", true);
				}
				results.offer(result);
			}
			return;
		}
		
		// if packet is not for this session then we need to forward it
		if (session != null && packet.getStanzaTo() != null && !session.isUserId(packet.getStanzaTo().getBareJID())) {
			results.offer(packet.copyElementOnly());
			return;
		}
		
		if (packet.getStanzaTo() == null) {
			// we should not forward disco#info or disco#items with no "to" set as they 
			// need to be processed only by server
			if (pubsubEl == null || pubsubEl.getXMLNS() != PUBSUB_XMLNS) {
				// ignoring - disco#info or disco#items to server
				log.log(Level.FINEST, "got <iq/> packet with no 'to' attribute = {0}", packet);
				return;
			}
		} else if (packet.getStanzaTo().getResource() == null && packet.getType() == StanzaType.error) {
			// we are dropping packet of type error if they are sent in from user
			return;
		}
				
		// this packet is to local user (offline or not) - forwarding to PubSub component
		Packet result = packet.copyElementOnly();
		if (packet.getStanzaTo() == null && session != null) {
			// in case if packet is from local user without from/to
			JID userJid = JID.jidInstance(session.getBareJID());
			result.initVars(packet.getStanzaFrom() != null ? packet.getStanzaFrom() : session.getJID(), userJid);
		}
		result.setPacketTo(pubsubJid);
		
		results.offer(result);
	}
	
	protected void processPresence(Packet packet, XMPPResourceConnection session, Queue<Packet> results) throws NotAuthorizedException {
		// is there a point in forwarding <presence/> of type error? we should forward only online/offline
		if (packet.getType() != null && packet.getType() != StanzaType.available && packet.getType() != StanzaType.unavailable) 
			return;
		
		// if presence is to local user then forward it to PubSub component
		if (session == null || packet.getStanzaTo() == null 
				|| (session.isUserId(packet.getStanzaTo().getBareJID())) && packet.getStanzaTo().getResource() == null) {
			
			Packet result = packet.copyElementOnly();
			if (packet.getStanzaTo() == null && session != null) {
				// in case if packet is from local user without from/to
				JID userJid = JID.jidInstance(session.getBareJID());
				result.initVars(session.getJID(), userJid);
			}			
			result.setPacketTo(pubsubJid);
			results.offer(result);
		}
	}
	
}
