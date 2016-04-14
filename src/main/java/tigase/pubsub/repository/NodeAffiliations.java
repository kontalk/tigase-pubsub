package tigase.pubsub.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.stateless.UsersAffiliation;

import tigase.xmpp.BareJID;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class NodeAffiliations implements IAffiliations {

	private static final Logger LOG = Logger.getLogger( NodeAffiliations.class.getName() );

	protected final static String DELIMITER = ";";

	public static tigase.pubsub.repository.cached.NodeAffiliations create(String data) {
		tigase.pubsub.repository.cached.NodeAffiliations a = new tigase.pubsub.repository.cached.NodeAffiliations();
		try {
			a.parse(data);
			return a;
		} catch (Exception e) {
			return new tigase.pubsub.repository.cached.NodeAffiliations();
		}
	}
	
	public static tigase.pubsub.repository.cached.NodeAffiliations create(Queue<UsersAffiliation> data) {
		tigase.pubsub.repository.cached.NodeAffiliations a = new tigase.pubsub.repository.cached.NodeAffiliations();
		if (data == null)
			return a;
		
		a.init(data);
		return a;
	}

	protected final ConcurrentMap<BareJID, UsersAffiliation> affs = new ConcurrentHashMap<BareJID, UsersAffiliation>(16, 0.9f, 8);

	private boolean changed = false;

	protected NodeAffiliations() {
	}

	@Override
	public void addAffiliation(BareJID bareJid, Affiliation affiliation) {
		UsersAffiliation a = new UsersAffiliation(bareJid, affiliation);
		affs.put(bareJid, a);
		changed = true;
		if ( LOG.isLoggable( Level.FINEST ) ){
			LOG.log( Level.FINEST, "Added affiliation for {0} as {1} (changed: {2})", new Object[] { bareJid, a, changed } );
		}
	}

	@Override
	public void changeAffiliation(BareJID bareJid, Affiliation affiliation) {
		UsersAffiliation a = this.get(bareJid);
		if (a != null) {
			a.setAffiliation(affiliation);
			changed = true;
		} else if (affiliation != Affiliation.none) {
			a = new UsersAffiliation(bareJid, affiliation);
			affs.put(bareJid, a);
			changed = true;
		}
		if (LOG.isLoggable( Level.FINEST )) {
			LOG.log( Level.FINEST, "Changed affiliation for {0} as {1} (changed: {2})", new Object[] {bareJid, a, changed});
		}
	}

	@Override
	public NodeAffiliations clone() throws CloneNotSupportedException {
		NodeAffiliations clone = new tigase.pubsub.repository.cached.NodeAffiliations();
		for (UsersAffiliation a : this.affs.values()) {
			clone.affs.put(a.getJid(), a.clone());
		}
		clone.changed = changed;
		return clone;
	}

	@Override
	public String toString() {
		return "NodeAffiliations:" + affs;
	}

	protected UsersAffiliation get(final BareJID bareJid) {
		final UsersAffiliation a = this.affs.get(bareJid);
		if (LOG.isLoggable( Level.FINEST )) {
			LOG.log( Level.FINEST, "Affiliation for {0} is {1}", new Object[] {bareJid, a});
		}
		return a;
	}

	@Override
	public UsersAffiliation[] getAffiliations() {
		final UsersAffiliation[] a = this.affs.values().toArray(new UsersAffiliation[] {});
		if (LOG.isLoggable( Level.FINEST )) {
			LOG.log( Level.FINEST, "Affiliation for {0} is {1}", new Object[] {Arrays.asList( a ) });
		}
		return a;
	}

	public Map<BareJID, UsersAffiliation> getAffiliationsMap() {
		return affs;
	}

	@Override
	public UsersAffiliation getSubscriberAffiliation(BareJID bareJid) {
		UsersAffiliation a = this.get(bareJid);
		if (a == null) {
			a = new UsersAffiliation(bareJid, Affiliation.none);
		}

		LOG.log( Level.FINEST, "Affiliation for {0} is {1}", new Object[] {bareJid, a});

		return a;
	}

	@Override
	public boolean isChanged() {
		return changed;
	}

	public void parse(String data) {
		String[] tokens = data.split(DELIMITER);
		affs.clear();
		int c = 0;
		BareJID jid = null;
		String state = null;
		for (String t : tokens) {
			if (c == 1) {
				state = t;
				++c;
			} else if (c == 0) {
				jid = BareJID.bareJIDInstanceNS(t);
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
