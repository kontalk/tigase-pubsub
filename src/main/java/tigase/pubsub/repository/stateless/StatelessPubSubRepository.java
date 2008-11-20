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

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;

public class StatelessPubSubRepository implements IPubSubRepository {

	private final IPubSubDAO dao;

	public StatelessPubSubRepository(IPubSubDAO pubSubDB, PubSubConfig pubSubConfig) {
		this.dao = pubSubDB;
	}

	public void addToRootCollection(String nodeName) throws RepositoryException {
		dao.addToRootCollection(nodeName);
	}

	@Override
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		this.dao.createNode(nodeName, ownerJid, nodeConfig, nodeType, collection);
	}

	@Override
	public void deleteNode(String nodeName) throws RepositoryException {
		this.dao.deleteNode(nodeName);
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
	public IAffiliations getNodeAffiliations(String nodeName) throws RepositoryException {
		return dao.getNodeAffiliations(nodeName);
	}

	@Override
	public AbstractNodeConfig getNodeConfig(String nodeName) throws RepositoryException {
		return this.dao.getNodeConfig(nodeName);
	}

	@Override
	public IItems getNodeItems(String nodeName) throws RepositoryException {
		return new Items(nodeName, this.dao);
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

}
