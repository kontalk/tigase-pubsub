package tigase.pubsub.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.Module;
import tigase.pubsub.exceptions.PubSubException;
import tigase.util.JIDUtils;
import tigase.xml.Element;

public class PresenceCollectorModule implements Module {

	private static final Criteria CRIT = ElementCriteria.name("presence");

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private final Map<String, Set<String>> resources = new HashMap<String, Set<String>>();

	// private final Set<String> onlineUsers = new HashSet<String>();

	public synchronized boolean addJid(final String jid) {
		if (jid == null)
			return false;
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

	public List<String> getAllAvailableJids() {
		ArrayList<String> result = new ArrayList<String>();

		for (Entry<String, Set<String>> entry : this.resources.entrySet()) {
			for (String reource : entry.getValue()) {
				result.add(entry.getKey() + "/" + reource);
			}

		}

		return result;
	}

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

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#presence-notifications" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	public boolean isJidAvailable(final String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		final Set<String> resources = this.resources.get(bareJid);
		return resources != null && resources.size() > 0;
	}

	private Element preparePresence(final Element presence, String type) {
		String to = presence.getAttribute("from");
		if (to != null) {
			String jid = JIDUtils.getNodeID(to);
			Element p = new Element("presence", new String[] { "to", "from" },
					new String[] { jid, presence.getAttribute("to") });
			if (type != null) {
				p.setAttribute("type", type);
			}

			return p;
		}
		return null;
	}

	@Override
	public List<Element> process(Element element, ElementWriter elementWriter) throws PubSubException {
		final String type = element.getAttribute("type");
		final String jid = element.getAttribute("from");

		List<Element> result = new ArrayList<Element>();
		if (type == null) {
			boolean added = addJid(jid);
			if (added) {
				Element p = new Element("presence", new String[] { "to", "from" }, new String[] { jid,
						element.getAttribute("to") });
				result.add(p);
			}
		} else if ("unavailable".equals(type)) {
			removeJid(jid);
			Element p = new Element("presence", new String[] { "to", "from", "type" }, new String[] { jid,
					element.getAttribute("to"), "unavailable" });
			result.add(p);
		} else if ("subscribe".equals(type)) {
			log.finest("Contact " + jid + " wants to subscribe PubSub");
			Element presence = preparePresence(element, "subscribed");
			if (presence != null)
				result.add(presence);
			presence = preparePresence(element, "subscribe");
			if (presence != null)
				result.add(presence);
		} else if ("unsubscribe".equals(type) || "unsubscribed".equals(type)) {
			log.finest("Contact " + jid + " wants to unsubscribe PubSub");
			Element presence = preparePresence(element, "unsubscribed");
			if (presence != null)
				result.add(presence);
			presence = preparePresence(element, "unsubscribe");
			if (presence != null)
				result.add(presence);
		}

		return result.size() == 0 ? null : result;
	}

	protected synchronized boolean removeJid(final String jid) {
		if (jid == null)
			return false;
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
