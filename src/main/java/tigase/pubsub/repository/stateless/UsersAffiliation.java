package tigase.pubsub.repository.stateless;

import tigase.xmpp.BareJID;

import tigase.pubsub.Affiliation;

public class UsersAffiliation implements Cloneable {

	private Affiliation affiliation;

	private final BareJID jid;

	public UsersAffiliation(final BareJID jid) {
		this.affiliation = Affiliation.none;
		this.jid = jid;
	}

	public UsersAffiliation(final BareJID jid, final Affiliation affiliation) {
		this.affiliation = affiliation == null ? Affiliation.none : affiliation;
		this.jid = jid;
	}

	@Override
	public UsersAffiliation clone() throws CloneNotSupportedException {
		UsersAffiliation a = new UsersAffiliation(jid, affiliation);
		return a;
	}

	public Affiliation getAffiliation() {
		return affiliation;
	}

	public BareJID getJid() {
		return jid;
	}

	public void setAffiliation(Affiliation affiliation) {
		this.affiliation = affiliation;
	}

	@Override
	public String toString() {
		return "UsersAffiliation{" + "affiliation=" + affiliation + ", jid=" + jid + '}';
	}
}
