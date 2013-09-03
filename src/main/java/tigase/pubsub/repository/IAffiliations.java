package tigase.pubsub.repository;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.xmpp.BareJID;

public interface IAffiliations {

	public abstract void addAffiliation(BareJID jid, Affiliation affiliation);

	public abstract void changeAffiliation(BareJID jid, Affiliation affiliation);

	public abstract UsersAffiliation[] getAffiliations();

	public abstract UsersAffiliation getSubscriberAffiliation(BareJID jid);

	public boolean isChanged();

	public abstract String serialize();

}
