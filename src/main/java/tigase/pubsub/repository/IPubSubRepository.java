package tigase.pubsub.repository;

//~--- non-JDK imports --------------------------------------------------------

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.xmpp.BareJID;

//~--- interfaces -------------------------------------------------------------

/**
 * Interface description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:20:15 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public interface IPubSubRepository {

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
	 * 
	 * @throws RepositoryException
	 */
	public abstract void deleteNode(String nodeName) throws RepositoryException;

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 */
	public void destroy();

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @throws RepositoryException
	 */
	public abstract void forgetConfiguration(String nodeName) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param owner
	 * @param bareJid
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public abstract String[] getBuddyGroups(BareJID owner, String bareJid) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @param owner
	 * @param buddy
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public abstract String getBuddySubscription(BareJID owner, String buddy) throws RepositoryException;

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
	public IAffiliations getNodeAffiliations(String nodeName) throws RepositoryException;

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
	public abstract AbstractNodeConfig getNodeConfig(String nodeName) throws RepositoryException;

	// ~--- methods
	// --------------------------------------------------------------

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
	public IItems getNodeItems(String nodeName) throws RepositoryException;

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
	public ISubscriptions getNodeSubscriptions(String nodeName) throws RepositoryException;

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public abstract IPubSubDAO getPubSubDAO();

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public abstract String[] getRootCollection() throws RepositoryException;

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param owner
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public abstract String[] getUserRoster(BareJID owner) throws RepositoryException;

	/**
	 * Method description
	 * 
	 */
	public abstract void init();

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @throws RepositoryException
	 */
	public void removeFromRootCollection(String nodeName) throws RepositoryException;

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param nodeConfig
	 * 
	 * @throws RepositoryException
	 */
	public abstract void update(String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException;

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
	 * @param subscriptions
	 * 
	 * @throws RepositoryException
	 */
	public void update(String nodeName, ISubscriptions subscriptions) throws RepositoryException;
}

// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com
