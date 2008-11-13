package tigase.pubsub.repository;

import java.util.HashMap;
import java.util.Map;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.util.JIDUtils;

public class NodeAffiliations implements IAffiliations {

	private final static String DELIMITER = ";";

	public static NodeAffiliations create(String data) {
		NodeAffiliations a = new NodeAffiliations();
		try {
			String[] tokens = data.split(DELIMITER);

			int c = 0;
			String jid = null;
			String state = null;
			for (String t : tokens) {
				if (c == 1) {
					state = t;
					++c;
				} else if (c == 0) {
					jid = t;
					++c;
				}
				if (c == 2) {
					UsersAffiliation b = new UsersAffiliation(jid, Affiliation.valueOf(state));
					a.affs.put(jid, b);
					jid = null;
					state = null;
					c = 0;
				}
			}
			return a;
		} catch (Exception e) {
			return new NodeAffiliations();
		}
	}

	private final Map<String, UsersAffiliation> affs = new HashMap<String, UsersAffiliation>();

	private boolean changed = false;

	@Override
	public void addAffiliation(String jid, Affiliation affiliation) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersAffiliation a = new UsersAffiliation(bareJid, affiliation);
		affs.put(bareJid, a);
		changed = true;
	}

	@Override
	public void changeAffiliation(String jid, Affiliation affiliation) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersAffiliation a = this.affs.get(bareJid);
		if (a != null) {
			a.setAffiliation(affiliation);
			changed = true;
		} else {
			a = new UsersAffiliation(bareJid, affiliation);
			affs.put(bareJid, a);
			changed = true;
		}
	}

	@Override
	public UsersAffiliation[] getAffiliations() {
		return this.affs.values().toArray(new UsersAffiliation[] {});
	}

	@Override
	public UsersAffiliation getSubscriberAffiliation(String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersAffiliation a = this.affs.get(bareJid);
		if (a == null) {
			a = new UsersAffiliation(bareJid, Affiliation.none);
		}
		return a;
	}

	public boolean isChanged() {
		return changed;
	}

	public String serialize() {
		return serialize(false);
	}

	public String serialize(boolean resetChangeFlag) {
		StringBuilder sb = new StringBuilder();
		for (UsersAffiliation a : this.affs.values()) {
			if (a.getAffiliation() != Affiliation.none) {
				sb.append(a.getJid());
				sb.append(DELIMITER);
				sb.append(a.getAffiliation().name());
				sb.append(DELIMITER);
			}
		}
		return sb.toString();
	}

}
