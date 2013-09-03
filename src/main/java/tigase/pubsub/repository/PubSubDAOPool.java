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

import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

public class PubSubDAOPool extends PubSubDAO {

	private static final Logger log = Logger.getLogger(PubSubDAOPool.class.getName());

	private LinkedBlockingQueue<PubSubDAO> daoPool = new LinkedBlockingQueue<PubSubDAO>();

	public PubSubDAOPool(UserRepository userRepository, PubSubConfig config) {
		super(userRepository, config);
	}

	public void addDao(PubSubDAO dao) {
		daoPool.offer(dao);
	}

	@Override
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.addToRootCollection(serviceJid, nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
			NodeType nodeType, String collection) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.createNode(serviceJid, nodeName, ownerJid, nodeConfig, nodeType, collection);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void deleteItem(BareJID serviceJid, String nodeName, String id) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.deleteItem(serviceJid, nodeName, id);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void deleteNode(BareJID serviceJid, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.deleteNode(serviceJid, nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void destroy() {
	}

	@Override
	public Element getItem(BareJID serviceJid, String nodeName, String id) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getItem(serviceJid, nodeName, id);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public Date getItemCreationDate(BareJID serviceJid, final String nodeName, final String id) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getItemCreationDate(serviceJid, nodeName, id);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public String[] getItemsIds(BareJID serviceJid, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getItemsIds(serviceJid, nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public Date getItemUpdateDate(BareJID serviceJid, String nodeName, String id) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getItemUpdateDate(serviceJid, nodeName, id);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public NodeAffiliations getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getNodeAffiliations(serviceJid, nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public String[] getNodesList(BareJID serviceJid) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getNodesList(serviceJid);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public NodeSubscriptions getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getNodeSubscriptions(serviceJid, nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public String[] getRootNodes(BareJID serviceJid) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getRootNodes(serviceJid);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public void init() throws RepositoryException {
	}

	@Override
	protected String readNodeConfigFormData(BareJID serviceJid, final String nodeName) throws TigaseDBException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.readNodeConfigFormData(serviceJid, nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public void removeAllFromRootCollection(BareJID serviceJid) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.removeAllFromRootCollection(serviceJid);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void removeFromRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.removeFromRootCollection(serviceJid, nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void removeSubscriptions(BareJID serviceJid, String nodeName, int changedIndex) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.removeSubscriptions(serviceJid, nodeName, changedIndex);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	public PubSubDAO takeDao() {
		try {
			return daoPool.take();
		} catch (InterruptedException ex) {
			log.log(Level.WARNING, "Couldn't obtain PubSub DAO from the pool", ex);
		}
		return null;
	}

	@Override
	public void updateAffiliations(BareJID serviceJid, String nodeName, String serializedData) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.updateAffiliations(serviceJid, nodeName, serializedData);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void updateNodeConfig(final BareJID serviceJid, final String nodeName, final String serializedData)
			throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.updateNodeConfig(serviceJid, nodeName, serializedData);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void updateSubscriptions(BareJID serviceJid, String nodeName, int changedIndex, String serializedData)
			throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.updateSubscriptions(serviceJid, nodeName, changedIndex, serializedData);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void writeItem(final BareJID serviceJid, final String nodeName, long timeInMilis, final String id,
			final String publisher, final Element item) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.writeItem(serviceJid, nodeName, timeInMilis, id, publisher, item);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

}
