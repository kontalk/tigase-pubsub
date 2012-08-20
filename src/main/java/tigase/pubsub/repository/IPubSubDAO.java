/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
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
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */

package tigase.pubsub.repository;

//~--- non-JDK imports --------------------------------------------------------

import java.util.Date;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

//~--- interfaces -------------------------------------------------------------

/**
 * Interface description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:16:25 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public interface IPubSubDAO {

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @throws RepositoryException
	 */
	public void addToRootCollection(String nodeName) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param ownerJid
	 * @param nodeConfig
	 * @param nodeType
	 * @param collection
	 * 
	 * @throws RepositoryException
	 */
	public abstract void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType,
			String collection) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param id
	 * 
	 * @throws RepositoryException
	 */
	public abstract void deleteItem(String nodeName, String id) throws RepositoryException;

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @throws RepositoryException
	 */
	public abstract void deleteNode(String nodeName) throws RepositoryException;

	/**
	 * Method description
	 * 
	 */
	public void destroy();

	String[] getBuddyGroups(BareJID owner, String bareJid) throws RepositoryException;

	// ~--- methods
	// --------------------------------------------------------------

	String getBuddySubscription(BareJID owner, String buddy) throws RepositoryException;

	Element getItem(String nodeName, String id) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param id
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public abstract Date getItemCreationDate(final String nodeName, final String id) throws RepositoryException;

	String[] getItemsIds(String nodeName) throws RepositoryException;

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param id
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public abstract Date getItemUpdateDate(final String nodeName, final String id) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public NodeAffiliations getNodeAffiliations(String nodeName) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public AbstractNodeConfig getNodeConfig(final String nodeName) throws RepositoryException;

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public abstract String[] getNodesList() throws RepositoryException;

	NodeSubscriptions getNodeSubscriptions(String nodeName) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public String[] getRootNodes() throws RepositoryException;

	// ~--- get methods
	// ----------------------------------------------------------

	String[] getUserRoster(BareJID owner) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @throws RepositoryException
	 */
	public void init() throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @throws RepositoryException
	 */
	public void removeFromRootCollection(String nodeName) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param nodeConfig
	 * 
	 * @throws RepositoryException
	 */
	public abstract void update(final String nodeName, final AbstractNodeConfig nodeConfig) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param affiliations
	 * 
	 * @throws RepositoryException
	 */
	public void update(String nodeName, IAffiliations affiliations) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param timeInMilis
	 * @param id
	 * @param publisher
	 * @param item
	 * 
	 * @throws RepositoryException
	 */
	public abstract void writeItem(final String nodeName, long timeInMilis, final String id, final String publisher,
			final Element item) throws RepositoryException;
}

// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com
