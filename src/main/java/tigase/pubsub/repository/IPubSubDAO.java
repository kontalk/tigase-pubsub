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
package tigase.pubsub.repository;

import java.util.Date;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.xml.Element;

public interface IPubSubDAO {

	void addListener(PubSubRepositoryListener listener);

	public void addToRootCollection(String nodeName) throws RepositoryException;

	public abstract void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType,
			String collection) throws RepositoryException;

	public abstract void deleteItem(String nodeName, String id) throws RepositoryException;

	public abstract void deleteNode(String nodeName) throws RepositoryException;

	String[] getBuddyGroups(String owner, String bareJid) throws RepositoryException;

	String getBuddySubscription(String owner, String buddy) throws RepositoryException;

	Element getItem(String nodeName, String id) throws RepositoryException;

	public abstract Date getItemCreationDate(final String nodeName, final String id) throws RepositoryException;

	String[] getItemsIds(String nodeName) throws RepositoryException;

	public abstract Date getItemUpdateDate(final String nodeName, final String id) throws RepositoryException;

	public NodeAffiliations getNodeAffiliations(String nodeName) throws RepositoryException;

	public AbstractNodeConfig getNodeConfig(final String nodeName) throws RepositoryException;

	public abstract String[] getNodesList() throws RepositoryException;

	NodeSubscriptions getNodeSubscriptions(String nodeName) throws RepositoryException;

	public String[] getRootNodes() throws RepositoryException;

	String[] getUserRoster(String owner) throws RepositoryException;

	public void init();

	public void removeFromRootCollection(String nodeName) throws RepositoryException;

	void removeListener(PubSubRepositoryListener listener);

	public abstract void update(final String nodeName, final AbstractNodeConfig nodeConfig) throws RepositoryException;

	public void update(String nodeName, IAffiliations affiliations) throws RepositoryException;

	void update(String nodeName, ISubscriptions subscriptions) throws RepositoryException;

	public abstract void writeItem(final String nodeName, long timeInMilis, final String id, final String publisher,
			final Element item) throws RepositoryException;

}