package tigase.pubsub.repository.cached;

import java.util.HashMap;
import java.util.Map;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.stateless.UsersAffiliation;

class NodeAffiliations extends tigase.pubsub.repository.NodeAffiliations {

	protected final Map<String, UsersAffiliation> changedAffs = new HashMap<String, UsersAffiliation>();

	private NodeAffiliations() {
	}

	NodeAffiliations(tigase.pubsub.repository.NodeAffiliations nodeAffiliations) {
		affs.putAll(nodeAffiliations.getAffiliationsMap());
	}

	@Override
	public void addAffiliation(String jid, Affiliation affiliation) {
		// TODO Auto-generated method stub
		super.addAffiliation(jid, affiliation);
	}

	@Override
	public void changeAffiliation(String jid, Affiliation affiliation) {
		// TODO Auto-generated method stub
		super.changeAffiliation(jid, affiliation);
	}

	@Override
	public NodeAffiliations clone() throws CloneNotSupportedException {
		NodeAffiliations clone = new NodeAffiliations();
		for (UsersAffiliation a : this.affs.values()) {
			clone.affs.put(a.getJid(), a.clone());
		}
		clone.changed = changed;
		return clone;
	}

	@Override
	public UsersAffiliation[] getAffiliations() {
		// TODO Auto-generated method stub
		return super.getAffiliations();
	}

	@Override
	public Map<String, UsersAffiliation> getAffiliationsMap() {
		// TODO Auto-generated method stub
		return super.getAffiliationsMap();
	}

	@Override
	public UsersAffiliation getSubscriberAffiliation(String jid) {
		// TODO Auto-generated method stub
		return super.getSubscriberAffiliation(jid);
	}

	@Override
	public boolean isChanged() {
		// TODO Auto-generated method stub
		return super.isChanged();
	}

	@Override
	public void parse(String data) {
		// TODO Auto-generated method stub
		super.parse(data);
	}

	@Override
	public void replaceBy(IAffiliations nodeAffiliations) {
		// TODO Auto-generated method stub
		super.replaceBy(nodeAffiliations);
	}

	@Override
	public void resetChangedFlag() {
		// TODO Auto-generated method stub
		super.resetChangedFlag();
	}

	@Override
	public String serialize() {
		// TODO Auto-generated method stub
		return super.serialize();
	}

}
