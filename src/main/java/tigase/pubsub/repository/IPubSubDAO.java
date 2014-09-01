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
import tigase.db.Repository;
import tigase.db.UserRepository;
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
public interface IPubSubDAO<T> extends Repository {

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
	public abstract T createNode(BareJID serviceJid, String nodeName, BareJID ownerJid, AbstractNodeConfig nodeConfig,
			NodeType nodeType, T collectionId) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param id
	 * 
	 * @throws RepositoryException
	 */
	public abstract void deleteItem(BareJID serviceJid, T nodeId, String id) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @throws RepositoryException
	 */
	public abstract void deleteNode(BareJID serviceJid, T nodeId) throws RepositoryException;

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

	Element getItem(BareJID serviceJid, T nodeId, String id) throws RepositoryException;

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
	public abstract Date getItemCreationDate(BareJID serviceJid, T nodeId, final String id)
			throws RepositoryException;

	String[] getItemsIds(BareJID serviceJid, T nodeId) throws RepositoryException;

	String[] getItemsIdsSince(BareJID serviceJid, T nodeId, Date since) throws RepositoryException;

	List<IItems.ItemMeta> getItemsMeta(BareJID serviceJid, T nodeId, String nodeName)
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
	public abstract Date getItemUpdateDate(BareJID serviceJid, T nodeId, final String id)
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
	public NodeAffiliations getNodeAffiliations(BareJID serviceJid, T nodeId) throws RepositoryException;

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
	public String getNodeConfig(BareJID serviceJid, T nodeId) throws RepositoryException;

	public T getNodeId(BareJID serviceJid, String nodeName) throws RepositoryException;
	
	/**
	 * Method description
	 * 
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public abstract String[] getNodesList(BareJID serviceJid, String nodeName) throws RepositoryException;

	NodeSubscriptions getNodeSubscriptions(BareJID serviceJid, T nodeId) throws RepositoryException;

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
	 * Method initilizes implementation of this interface which will internally call {@link initReposiotry()} method
	 * to initialize repository.
	 * 
	 * @param resource_uri
	 * @param params
	 * @param userRepository
	 * @throws RepositoryException 
	 */
	public void init(String resource_uri, Map<String, String> params, UserRepository userRepository)  throws RepositoryException;	
	
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
	public void removeFromRootCollection(BareJID serviceJid, T nodeId) throws RepositoryException;

	public void removeNodeSubscription(BareJID serviceJid, T nodeId, BareJID jid) throws RepositoryException;
	
	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param nodeConfig
	 * 
	 * @throws RepositoryException
	 */
	public abstract void updateNodeConfig(BareJID serviceJid, final T nodeId, final String serializedData, T collectionId)
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
	public void updateNodeAffiliation(BareJID serviceJid, T nodeId, String nodeName, UsersAffiliation userAffiliation) throws RepositoryException;

	public void updateNodeSubscription(BareJID serviceJid, T nodeId, String nodeName, UsersSubscription userSubscription) throws RepositoryException;
	
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
	public abstract void writeItem(BareJID serviceJid, T nodeId, long timeInMilis, final String id,
			final String publisher, final Element item) throws RepositoryException;
}
