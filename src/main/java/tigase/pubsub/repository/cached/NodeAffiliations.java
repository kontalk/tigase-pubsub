package tigase.pubsub.repository.cached;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.xmpp.BareJID;

public class NodeAffiliations extends tigase.pubsub.repository.NodeAffiliations {

	protected final ThreadLocal<Map<BareJID, UsersAffiliation>> changedAffs = new ThreadLocal<Map<BareJID, UsersAffiliation>>();

	public NodeAffiliations() {
	}

	public NodeAffiliations(tigase.pubsub.repository.NodeAffiliations nodeAffiliations) {
		affs.putAll(nodeAffiliations.getAffiliationsMap());
	}

	@Override
	public void addAffiliation(BareJID bareJid, Affiliation affiliation) {
		UsersAffiliation a = new UsersAffiliation(bareJid, affiliation);
		changedAffs().put(bareJid, a);
	}

	@Override
	public void changeAffiliation(BareJID bareJid, Affiliation affiliation) {
		UsersAffiliation a = this.get(bareJid);
		Map<BareJID, UsersAffiliation> changedAffs = changedAffs();
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
		Map<BareJID, UsersAffiliation> changedAffs = changedAffs();
		Map<BareJID, UsersAffiliation> cloneChangedAffs = clone.changedAffs();
		for (UsersAffiliation a : changedAffs.values()) {
			cloneChangedAffs.put(a.getJid(), a.clone());
		}
		return clone;
	}

	@Override
	protected UsersAffiliation get(BareJID bareJid) {
		Map<BareJID, UsersAffiliation> changedAffs = changedAffs();
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
		result.addAll(this.changedAffs().values());
		return result.toArray(new UsersAffiliation[] {});
	}

	public Map<BareJID,UsersAffiliation> getChanged() {
		return changedAffs();
	}
	
	public void init(Queue<UsersAffiliation> data) {
		UsersAffiliation a = null;
		while ((a = data.poll()) != null) {
			affs.put(a.getJid(), a);
		}		
	}
	
	@Override
	public boolean isChanged() {
		return changedAffs().size() > 0;
	}

	public void merge() {
		Map<BareJID, UsersAffiliation> changedAffs = changedAffs();
		for (Map.Entry<BareJID, UsersAffiliation> entry : changedAffs.entrySet()) {
			if (entry.getValue().getAffiliation() == Affiliation.none) {
				affs.remove(entry.getKey());
			} else {
				affs.put(entry.getKey(), entry.getValue());
			}
		}
		changedAffs.clear();

	}

	@Override
	public void resetChangedFlag() {
		changedAffs().clear();
	}

	private Map<BareJID, UsersAffiliation> changedAffs() {
		Map<BareJID, UsersAffiliation> changedAffs = this.changedAffs.get();
		
		if (changedAffs == null) {
			changedAffs = new HashMap<BareJID, UsersAffiliation>();
			this.changedAffs.set(changedAffs);
		}
		
		return changedAffs;
	}	
}
