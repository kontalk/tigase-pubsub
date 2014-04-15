/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2009 "Tomasz Sterna" <tomek@xiaoka.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev: $
 * Last modified by $Author: $
 * $Date: $
 */
package tigase.pubsub.repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.NodeAffiliations;
import tigase.pubsub.repository.NodeSubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

public class PubSubDAOPool extends PubSubDAO {

	private static final Logger log = Logger.getLogger(PubSubDAOPool.class.getName());

	private final Map<BareJID, LinkedBlockingQueue<PubSubDAO>> pools = new HashMap<BareJID, LinkedBlockingQueue<PubSubDAO>>();

	public PubSubDAOPool(UserRepository userRepository) {
		super(userRepository);
	}

	public void addDao(BareJID domain, PubSubDAO dao) {
		LinkedBlockingQueue<PubSubDAO> ee = pools.get(domain);
		if (ee == null) {
			ee = new LinkedBlockingQueue<PubSubDAO>();
			pools.put(domain, ee);
		}
		ee.offer(dao);
	}

	@Override
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.addToRootCollection(serviceJid, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public long createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
			NodeType nodeType, Long collectionId) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.createNode(serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collectionId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
			return 0;
		}
	}

	@Override
	public void deleteItem(BareJID serviceJid, long nodeId, String id) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.deleteItem(serviceJid, nodeId, id);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void deleteNode(BareJID serviceJid, long nodeId) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.deleteNode(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void destroy() {
		Set<BareJID> keys = new HashSet<BareJID>(pools.keySet());
		for (BareJID serviceJid : keys) {
			List<PubSubDAO> list = new ArrayList<PubSubDAO>(pools.get(serviceJid));
			for (PubSubDAO dao : list) {
				try {
					dao.destroy();
				} finally {
				}
			}
		}
	}

	@Override
	public String[] getAllNodesList(BareJID serviceJid) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getAllNodesList(serviceJid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public Element getItem(BareJID serviceJid, long nodeId, String id) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItem(serviceJid, nodeId, id);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public Date getItemCreationDate(BareJID serviceJid, final long nodeId, final String id) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItemCreationDate(serviceJid, nodeId, id);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public String[] getItemsIds(BareJID serviceJid, long nodeId) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItemsIds(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public String[] getItemsIdsSince(BareJID serviceJid, long nodeId, Date since) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItemsIdsSince(serviceJid, nodeId, since);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public List<IItems.ItemMeta> getItemsMeta(BareJID serviceJid, long nodeId, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItemsMeta(serviceJid, nodeId, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public Date getItemUpdateDate(BareJID serviceJid, long nodeId, String id) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getItemUpdateDate(serviceJid, nodeId, id);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public NodeAffiliations getNodeAffiliations(BareJID serviceJid, long nodeId) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodeAffiliations(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public String getNodeConfig(BareJID serviceJid, long nodeId) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodeConfig(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
			return null;
		}
	}

	@Override
	public long getNodeId(BareJID serviceJid, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodeId(serviceJid, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
			return 0;
		}
	}

	@Override
	public String[] getNodesList(BareJID serviceJid, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodesList(serviceJid, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public NodeSubscriptions getNodeSubscriptions(BareJID serviceJid, long nodeId) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getNodeSubscriptions(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	protected String getPoolDetails(BareJID serviceJid) {
		String result = "";

		LinkedBlockingQueue<PubSubDAO> ee;
		if (pools.containsKey(serviceJid)) {
			result += serviceJid + " pool ";
			ee = this.pools.get(serviceJid);
		} else {
			result += "default pool ";
			ee = this.pools.get(null);
		}

		result += "has " + ee.size() + " element(s).";

		return result;
	}

	@Override
	public String[] getChildNodes(BareJID serviceJid, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getChildNodes(serviceJid, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getUserAffiliations(serviceJid, jid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID jid) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.getUserSubscriptions(serviceJid, jid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}

	@Override
	public void init() throws RepositoryException {
		Set<BareJID> keys = new HashSet<BareJID>(pools.keySet());
		for (BareJID serviceJid : keys) {
			List<PubSubDAO> list = new ArrayList<PubSubDAO>(pools.get(serviceJid));
			for (PubSubDAO dao : list) {
				try {
					dao.init();
				} finally {
				}
			}
		}
	}

	protected void offerDao(BareJID serviceJid, PubSubDAO dao) {
		LinkedBlockingQueue<PubSubDAO> ee = this.pools.containsKey(serviceJid) ? this.pools.get(serviceJid)
				: this.pools.get(null);
		ee.offer(dao);
	}

/*	//@Override
	protected String readNodeConfigFormData(BareJID serviceJid, final long nodeId) throws TigaseDBException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				return dao.readNodeConfigFormData(serviceJid, nodeName);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
		return null;
	}*/

	@Override
	public void removeAllFromRootCollection(BareJID serviceJid) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.removeAllFromRootCollection(serviceJid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void removeFromRootCollection(BareJID serviceJid, long nodeId) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.removeFromRootCollection(serviceJid, nodeId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void removeNodeSubscription(BareJID serviceJid, long nodeId, BareJID jid) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.removeNodeSubscription(serviceJid, nodeId, jid);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	public PubSubDAO takeDao(BareJID serviceJid) {
		try {
			LinkedBlockingQueue<PubSubDAO> ee = this.pools.containsKey(serviceJid) ? this.pools.get(serviceJid)
					: this.pools.get(null);
			return ee.take();
		} catch (InterruptedException ex) {
			log.log(Level.WARNING, "Couldn't obtain PubSub DAO from the pool", ex);
		}
		return null;
	}

	@Override
	public void updateNodeAffiliation(BareJID serviceJid, long nodeId, UsersAffiliation affiliation) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.updateNodeAffiliation(serviceJid, nodeId, affiliation);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void updateNodeConfig(final BareJID serviceJid, final long nodeId, final String serializedData, final Long collectionId)
			throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.updateNodeConfig(serviceJid, nodeId, serializedData, collectionId);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void updateNodeSubscription(BareJID serviceJid, long nodeId, UsersSubscription subscription)
			throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.updateNodeSubscription(serviceJid, nodeId, subscription);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

	@Override
	public void writeItem(final BareJID serviceJid, long nodeId, long timeInMilis, final String id,
			final String publisher, final Element item) throws RepositoryException {
		PubSubDAO dao = takeDao(serviceJid);
		if (dao != null) {
			try {
				dao.writeItem(serviceJid, nodeId, timeInMilis, id, publisher, item);
			} finally {
				offerDao(serviceJid, dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + getPoolDetails(serviceJid));
		}
	}

}
