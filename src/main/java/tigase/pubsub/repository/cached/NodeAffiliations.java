package tigase.pubsub.repository.cached;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.xmpp.BareJID;

public class NodeAffiliations extends tigase.pubsub.repository.NodeAffiliations {

	protected final Map<BareJID, UsersAffiliation> changedAffs = new HashMap<BareJID, UsersAffiliation>();

	private NodeAffiliations() {
	}

	public NodeAffiliations(tigase.pubsub.repository.NodeAffiliations nodeAffiliations) {
		affs.putAll(nodeAffiliations.getAffiliationsMap());
	}

	@Override
	public void addAffiliation(BareJID bareJid, Affiliation affiliation) {
		UsersAffiliation a = new UsersAffiliation(bareJid, affiliation);
		changedAffs.put(bareJid, a);
	}

	@Override
	public void changeAffiliation(BareJID bareJid, Affiliation affiliation) {
		UsersAffiliation a = this.get(bareJid);
		if (a != null) {
			a.setAffiliation(affiliation);
			changedAffs.put(bareJid, a);
		} else {
			a = new UsersAffiliation(bareJid, affiliation);
			changedAffs.put(bareJid, a);
		}
	}

	@Override
	public NodeAffiliations clone() throws CloneNotSupportedException {
		NodeAffiliations clone = new NodeAffiliations();
		for (UsersAffiliation a : this.affs.values()) {
			clone.affs.put(a.getJid(), a.clone());
		}
		for (UsersAffiliation a : this.changedAffs.values()) {
			clone.changedAffs.put(a.getJid(), a.clone());
		}
		return clone;
	}

	@Override
	protected UsersAffiliation get(BareJID bareJid) {
		UsersAffiliation us = changedAffs.get(bareJid);
		if (us == null) {
			us = affs.get(bareJid);
			if (us != null)
				try {
					return us.clone();
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
		}
		return us;
	}

	@Override
	public UsersAffiliation[] getAffiliations() {
		final Set<UsersAffiliation> result = new HashSet<UsersAffiliation>();
		result.addAll(this.affs.values());
		result.addAll(this.changedAffs.values());
		return result.toArray(new UsersAffiliation[] {});
	}

	@Override
	public boolean isChanged() {
		return changedAffs.size() > 0;
	}

	public void merge() {
		affs.putAll(changedAffs);
		changedAffs.clear();
	}

	@Override
	public void resetChangedFlag() {
		changedAffs.clear();
	}

}
