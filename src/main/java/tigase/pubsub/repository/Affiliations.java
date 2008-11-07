package tigase.pubsub.repository;

import java.util.HashMap;
import java.util.Map;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.inmemory.NodeAffiliation;
import tigase.util.JIDUtils;

public class Affiliations implements IAffiliations {

	private final static String DELIMITER = ";";

	public static Affiliations create(String data) {
		Affiliations a = new Affiliations();
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
					NodeAffiliation b = new NodeAffiliation(jid, Affiliation.valueOf(state));
					a.affs.put(jid, b);
					jid = null;
					state = null;
					c = 0;
				}
			}
			return a;
		} catch (Exception e) {
			return new Affiliations();
		}
	}

	public static void main(String[] args) {
		Affiliations aaa = new Affiliations();
		aaa.addAffiliation("bmalkow@malkowscy.net", Affiliation.member);
		aaa.addAffiliation("alice@sphere", Affiliation.none);
		aaa.addAffiliation("bob@sphere", Affiliation.owner);
		aaa.addAffiliation("carol@sphere", Affiliation.publisher);

		String data = aaa.serialize();
		System.out.println(data);
		Affiliations aaa2 = create(data);
		System.out.println(aaa2.serialize());
	}

	private final Map<String, NodeAffiliation> affs = new HashMap<String, NodeAffiliation>();

	private boolean changed = false;

	@Override
	public void addAffiliation(String jid, Affiliation affiliation) {
		final String bareJid = JIDUtils.getNodeID(jid);
		NodeAffiliation a = new NodeAffiliation(bareJid, affiliation);
		affs.put(bareJid, a);
		changed = true;
	}

	@Override
	public void changeAffiliation(String jid, Affiliation affiliation) {
		final String bareJid = JIDUtils.getNodeID(jid);
		NodeAffiliation a = this.affs.get(bareJid);
		if (a != null) {
			a.setAffiliation(affiliation);
			changed = true;
		} else {
			a = new NodeAffiliation(bareJid, affiliation);
			affs.put(bareJid, a);
			changed = true;
		}
	}

	@Override
	public NodeAffiliation[] getAffiliations() {
		return this.affs.values().toArray(new NodeAffiliation[] {});
	}

	@Override
	public NodeAffiliation getSubscriberAffiliation(String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		NodeAffiliation a = this.affs.get(bareJid);
		if (a == null) {
			a = new NodeAffiliation(bareJid, Affiliation.none);
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
		for (NodeAffiliation a : this.affs.values()) {
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
