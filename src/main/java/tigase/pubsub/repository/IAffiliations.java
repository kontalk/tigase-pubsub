package tigase.pubsub.repository;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.inmemory.NodeAffiliation;

public interface IAffiliations {

	public abstract void addAffiliation(String jid, Affiliation affiliation);

	public abstract void changeAffiliation(String jid, Affiliation affiliation);

	public abstract NodeAffiliation[] getAffiliations();

	public abstract NodeAffiliation getSubscriberAffiliation(String jid);

	public boolean isChanged();

	public abstract String serialize(boolean b);

}
