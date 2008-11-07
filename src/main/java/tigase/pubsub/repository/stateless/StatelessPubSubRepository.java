/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub.repository.stateless;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.PubSubRepositoryListener;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;

public class StatelessPubSubRepository implements IPubSubRepository {

	private final IPubSubDAO dao;

	private final List<PubSubRepositoryListener> listeners = new ArrayList<PubSubRepositoryListener>();

	public StatelessPubSubRepository(IPubSubDAO pubSubDB, PubSubConfig pubSubConfig) {
		this.dao = pubSubDB;
	}

	@Override
	public void addListener(PubSubRepositoryListener listener) {
		this.listeners.add(listener);
	}

	public void addToRootCollection(String nodeName) throws RepositoryException {
		dao.addToRootCollection(nodeName);
	}

	@Override
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		this.dao.createNode(nodeName, ownerJid, nodeConfig, nodeType, collection);
		if (!"".equals(nodeConfig.getCollection())) {
			fireNewNodeCollection(nodeName, null, collection);
		}
	}

	@Override
	public void deleteItem(String nodeName, String id) throws RepositoryException {
		this.dao.deleteItem(nodeName, id);
	}

	@Override
	public void deleteNode(String nodeName) throws RepositoryException {
		this.dao.deleteNode(nodeName);
	}

	private void fireNewNodeCollection(String nodeName, String oldCollectionName, String newCollectionName) {
		for (PubSubRepositoryListener listener : this.listeners) {
			listener.onChangeCollection(nodeName, oldCollectionName, newCollectionName);
		}
	}

	@Override
	public void forgetConfiguration(String nodeName) throws RepositoryException {
	}

	@Override
	public String[] getBuddyGroups(String owner, String bareJid) throws RepositoryException {
		return this.dao.getBuddyGroups(owner, bareJid);
	}

	@Override
	public String getBuddySubscription(String owner, String buddy) throws RepositoryException {
		return this.dao.getBuddySubscription(owner, buddy);
	}

	@Override
	public Element getItem(String nodeName, String id) throws RepositoryException {
		return this.dao.getItem(nodeName, id);
	}

	@Override
	public Date getItemCreationDate(String nodeName, String id) throws RepositoryException {
		return this.dao.getItemCreationDate(nodeName, id);
	}

	@Override
	public String[] getItemsIds(String nodeName) throws RepositoryException {
		return this.dao.getItemsIds(nodeName);
	}

	@Override
	public Date getItemUpdateDate(String nodeName, String id) throws RepositoryException {
		return this.dao.getItemUpdateDate(nodeName, id);
	}

	@Override
	public IAffiliations getNodeAffiliations(String nodeName) throws RepositoryException {
		return dao.getNodeAffiliations(nodeName);
	}

	@Override
	public AbstractNodeConfig getNodeConfig(String nodeName) throws RepositoryException {
		return this.dao.getNodeConfig(nodeName);
	}

	@Override
	public ISubscriptions getNodeSubscriptions(String nodeName) throws RepositoryException {
		return dao.getNodeSubscriptions(nodeName);
	}

	@Override
	public IPubSubDAO getPubSubDAO() {
		return this.dao;
	}

	@Override
	public String[] getRootCollection() throws RepositoryException {
		String[] result = this.dao.getRootNodes();
		return result == null ? new String[] {} : result;
	}

	@Override
	public String[] getUserRoster(String owner) throws RepositoryException {
		return this.dao.getUserRoster(owner);
	}

	@Override
	public void init() {
	}

	public void removeFromRootCollection(String nodeName) throws RepositoryException {
		dao.removeFromRootCollection(nodeName);
	}

	@Override
	public void removeListener(PubSubRepositoryListener listener) {
		this.listeners.remove(listener);
	}

	@Override
	public void update(String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException {
		this.dao.update(nodeName, nodeConfig);
	}

	@Override
	public void update(String nodeName, IAffiliations affiliations) throws RepositoryException {
		this.dao.update(nodeName, affiliations);
	}

	@Override
	public void update(String nodeName, ISubscriptions subscriptions) throws RepositoryException {
		dao.update(nodeName, subscriptions);
	}

	@Override
	public void writeItem(String nodeName, long timeInMilis, String id, String publisher, Element item) throws RepositoryException {
		this.dao.writeItem(nodeName, timeInMilis, id, publisher, item);
	}

}
