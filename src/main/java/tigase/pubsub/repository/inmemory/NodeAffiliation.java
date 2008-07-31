package tigase.pubsub.repository.inmemory;

import tigase.pubsub.Affiliation;
import tigase.util.JIDUtils;

class NodeAffiliation {

	private Affiliation affiliation;

	private final String jid;

	public NodeAffiliation(final String jid) {
		this.affiliation = Affiliation.none;
		this.jid = JIDUtils.getNodeID(jid);
	}

	public NodeAffiliation(final String jid, final Affiliation affiliation) {
		this.affiliation = affiliation;
		this.jid = JIDUtils.getNodeID(jid);
	}

	public Affiliation getAffiliation() {
		return affiliation;
	}

	public String getJid() {
		return jid;
	}

	public void setAffiliation(Affiliation affiliation) {
		this.affiliation = affiliation;
	}

}
