package tigase.pubsub.repository;

import java.util.HashMap;
import java.util.Map;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.util.JIDUtils;

public class NodeAffiliations implements IAffiliations {

	protected final static String DELIMITER = ";";

	public static NodeAffiliations create(String data) {
		NodeAffiliations a = new NodeAffiliations();
		try {
			a.parse(data);
			return a;
		} catch (Exception e) {
			return new NodeAffiliations();
		}
	}

	protected final Map<String, UsersAffiliation> affs = new HashMap<String, UsersAffiliation>();

	private boolean changed = false;

	protected NodeAffiliations() {
	}

	@Override
	public void addAffiliation(String jid, Affiliation affiliation) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersAffiliation a = new UsersAffiliation(bareJid, affiliation);
		synchronized (this.affs) {
			affs.put(bareJid, a);
		}
		changed = true;
	}

	@Override
	public void changeAffiliation(String jid, Affiliation affiliation) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersAffiliation a = this.get(bareJid);
		if (a != null) {
			a.setAffiliation(affiliation);
			changed = true;
		} else {
			a = new UsersAffiliation(bareJid, affiliation);
			synchronized (this.affs) {
				affs.put(bareJid, a);
			}
			changed = true;
		}
	}

	@Override
	public NodeAffiliations clone() throws CloneNotSupportedException {
		synchronized (this.affs) {
			NodeAffiliations clone = new NodeAffiliations();
			for (UsersAffiliation a : this.affs.values()) {
				clone.affs.put(a.getJid(), a.clone());
			}
			clone.changed = changed;
			return clone;
		}
	}

	protected UsersAffiliation get(final String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		synchronized (this.affs) {
			UsersAffiliation s = this.affs.get(bareJid);
			return s;
		}
	}

	@Override
	public UsersAffiliation[] getAffiliations() {
		synchronized (this.affs) {
			return this.affs.values().toArray(new UsersAffiliation[] {});
		}
	}

	public Map<String, UsersAffiliation> getAffiliationsMap() {
		return affs;
	}

	@Override
	public UsersAffiliation getSubscriberAffiliation(String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersAffiliation a = this.get(bareJid);
		if (a == null) {
			a = new UsersAffiliation(bareJid, Affiliation.none);
		}
		return a;
	}

	@Override
	public boolean isChanged() {
		return changed;
	}

	public void parse(String data) {
		synchronized (this.affs) {
			String[] tokens = data.split(DELIMITER);
			affs.clear();
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
					affs.put(jid, b);
					jid = null;
					state = null;
					c = 0;
				}
			}
		}
	}

	public void replaceBy(final IAffiliations nodeAffiliations) {
		synchronized (this.affs) {
			if (nodeAffiliations instanceof NodeAffiliations) {
				NodeAffiliations na = (NodeAffiliations) nodeAffiliations;

				this.changed = true;
				affs.clear();
				for (UsersAffiliation a : na.affs.values()) {
					affs.put(a.getJid(), a);
				}
			}
		}
	}

	public void resetChangedFlag() {
		this.changed = false;
	}

	@Override
	public String serialize() {
		StringBuilder sb = new StringBuilder();
		synchronized (this.affs) {
			for (UsersAffiliation a : this.affs.values()) {
				if (a.getAffiliation() != Affiliation.none) {
					sb.append(a.getJid());
					sb.append(DELIMITER);
					sb.append(a.getAffiliation().name());
					sb.append(DELIMITER);
				}
			}
		}
		return sb.toString();
	}

}
