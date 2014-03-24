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

import java.util.Date;
import java.util.List;
import java.util.Map;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.Subscription;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.NodeAffiliations;
import tigase.pubsub.repository.NodeSubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.impl.roster.RosterElement;

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
	public void addToRootCollection(BareJID serviceJid, String nodeName) throws RepositoryException;

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
	public abstract long createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
			NodeType nodeType, Long collectionId) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param id
	 * 
	 * @throws RepositoryException
	 */
	public abstract void deleteItem(BareJID serviceJid, long nodeId, String id) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @throws RepositoryException
	 */
	public abstract void deleteNode(BareJID serviceJid, long nodeId) throws RepositoryException;

	/**
	 * Method description
	 * 
	 */
	public void destroy();

	String[] getAllNodesList(BareJID serviceJid) throws RepositoryException;
	
	@Deprecated
	String[] getBuddyGroups(BareJID owner, BareJID bareJid) throws RepositoryException;
	@Deprecated
	String getBuddySubscription(BareJID owner, BareJID buddy) throws RepositoryException;

	Element getItem(BareJID serviceJid, long nodeId, String id) throws RepositoryException;

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
	public abstract Date getItemCreationDate(BareJID serviceJid, long nodeId, final String id)
			throws RepositoryException;

	String[] getItemsIds(BareJID serviceJid, long nodeId) throws RepositoryException;

	String[] getItemsIdsSince(BareJID serviceJid, long nodeId, Date since) throws RepositoryException;

	List<IItems.ItemMeta> getItemsMeta(BareJID serviceJid, long nodeId, String nodeName)
			throws RepositoryException;
	
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
	public abstract Date getItemUpdateDate(BareJID serviceJid, long nodeId, final String id)
			throws RepositoryException;

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
	public NodeAffiliations getNodeAffiliations(BareJID serviceJid, long nodeId) throws RepositoryException;

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
	public String getNodeConfig(BareJID serviceJid, long nodeId) throws RepositoryException;

	public long getNodeId(BareJID serviceJid, String nodeName) throws RepositoryException;
	
	/**
	 * Method description
	 * 
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public abstract String[] getNodesList(BareJID serviceJid, String nodeName) throws RepositoryException;

	NodeSubscriptions getNodeSubscriptions(BareJID serviceJid, long nodeId) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public String[] getChildNodes(BareJID serviceJid, String nodeName) throws RepositoryException;

	Map<BareJID, RosterElement> getUserRoster(BareJID owner) throws RepositoryException;

	Map<String, UsersAffiliation> getUserAffiliations(BareJID serviceJid, BareJID jid) throws RepositoryException;
	Map<String, UsersSubscription> getUserSubscriptions(BareJID serviceJid, BareJID jid) throws RepositoryException;
	
	/**
	 * Method description
	 * 
	 * 
	 * @throws RepositoryException
	 */
	public void init() throws RepositoryException;

	public AbstractNodeConfig parseConfig(String nodeName, String cfgData) throws RepositoryException;
	
	public void removeAllFromRootCollection(BareJID serviceJid) throws RepositoryException;
	
	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @throws RepositoryException
	 */
	public void removeFromRootCollection(BareJID serviceJid, long nodeId) throws RepositoryException;

	public void removeNodeSubscription(BareJID serviceJid, long nodeId, BareJID jid) throws RepositoryException;
	
	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param nodeConfig
	 * 
	 * @throws RepositoryException
	 */
	public abstract void updateNodeConfig(BareJID serviceJid, final long nodeId, final String serializedData, Long collectionId)
			throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param affiliations
	 * 
	 * @throws RepositoryException
	 */
	public void updateNodeAffiliation(BareJID serviceJid, long nodeId, UsersAffiliation userAffiliation) throws RepositoryException;

	public void updateNodeSubscription(BareJID serviceJid, long nodeId, UsersSubscription userSubscription) throws RepositoryException;
	
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
	public abstract void writeItem(BareJID serviceJid, long nodeId, long timeInMilis, final String id,
			final String publisher, final Element item) throws RepositoryException;
}
