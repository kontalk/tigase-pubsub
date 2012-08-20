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
	public void addToRootCollection(String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.addToRootCollection(nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.createNode(nodeName, ownerJid, nodeConfig, nodeType, collection);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void deleteItem(String nodeName, String id) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.deleteItem(nodeName, id);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void deleteNode(String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.deleteNode(nodeName);
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
	public Element getItem(String nodeName, String id) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getItem(nodeName, id);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public Date getItemCreationDate(final String nodeName, final String id) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getItemCreationDate(nodeName, id);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public String[] getItemsIds(String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getItemsIds(nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public Date getItemUpdateDate(String nodeName, String id) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getItemUpdateDate(nodeName, id);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public NodeAffiliations getNodeAffiliations(String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getNodeAffiliations(nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public String[] getNodesList() throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getNodesList();
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public NodeSubscriptions getNodeSubscriptions(String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getNodeSubscriptions(nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public String[] getRootNodes() throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.getRootNodes();
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
	protected String readNodeConfigFormData(final String nodeName) throws TigaseDBException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				return dao.readNodeConfigFormData(nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
		return null;
	}

	@Override
	public void removeAllFromRootCollection() throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.removeAllFromRootCollection();
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void removeFromRootCollection(String nodeName) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.removeFromRootCollection(nodeName);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void removeSubscriptions(String nodeName, int changedIndex) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.removeSubscriptions(nodeName, changedIndex);
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
	public void updateAffiliations(String nodeName, String serializedData) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.updateAffiliations(nodeName, serializedData);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void updateNodeConfig(final String nodeName, final String serializedData) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.updateNodeConfig(nodeName, serializedData);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void updateSubscriptions(String nodeName, int changedIndex, String serializedData) throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.updateSubscriptions(nodeName, changedIndex, serializedData);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

	@Override
	public void writeItem(final String nodeName, long timeInMilis, final String id, final String publisher, final Element item)
			throws RepositoryException {
		PubSubDAO dao = takeDao();
		if (dao != null) {
			try {
				dao.writeItem(nodeName, timeInMilis, id, publisher, item);
			} finally {
				addDao(dao);
			}
		} else {
			log.warning("dao is NULL, pool empty? - " + daoPool.size());
		}
	}

}
