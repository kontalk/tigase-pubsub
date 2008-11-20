package tigase.pubsub.repository;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.stateless.UsersAffiliation;

public interface IAffiliations {

	public abstract void addAffiliation(String jid, Affiliation affiliation);

	public abstract void changeAffiliation(String jid, Affiliation affiliation);

	public abstract UsersAffiliation[] getAffiliations();

	public abstract UsersAffiliation getSubscriberAffiliation(String jid);

	public boolean isChanged();

	public abstract String serialize();

}
