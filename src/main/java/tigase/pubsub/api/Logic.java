/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.pubsub.api;

import java.util.List;
import java.util.Map;

import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * Class responsible for pubsub logic. We should have at least two of them by
 * default: PubSubDefaultLogic, PepDefaultLogic
 * 
 * It should be possible to add new implementations. It should be possible to
 * stack logic in similar way to UserRepository is stacked, so that for each
 * service jid and maybe for each node we should be able to pick other logic.
 * 
 * If any additional data is need, ie. user roster, instance of class should be
 * passed to classes implementing this interface - maybe new setter methods
 * should be addded to interface
 * 
 * Maybe we should added possibility to add events before/after calling logic
 * methods in classes which call methods? Or maybe implementation of this class
 * should fire this events if thay were added?
 * 
 * @author andrzej
 */
public interface Logic {

	/**
	 * Configures node of service
	 * 
	 * @param from
	 * @param service
	 * @param node
	 * @param config
	 */
	void configureNode(JID from, BareJID service, String node, Element config) throws RepositoryException;

	/**
	 * Creates node for service
	 * 
	 * @param from
	 * @param service
	 * @param node
	 */
	void createNode(JID from, BareJID service, String node) throws RepositoryException;

	String getId();

	/**
	 * Method returns elements containing info about subnodes to passed node
	 * 
	 * @param from
	 * @param service
	 * @param node
	 *            - parent node, can be null for root node
	 * @return
	 */
	List<Element> getNodes(JID from, BareJID service, String node) throws RepositoryException;

	/**
	 * Method should return true if this node can be handled by this logic
	 * instance ie. PEP could have multiple logic instances handling diffrent
	 * nodes?
	 * 
	 * @param from
	 * @param service
	 * @param node
	 * @return
	 */
	boolean isSupported(JID from, BareJID service, String node);

	/**
	 * Method adds item to store using logic specific for this service and node
	 * 
	 * but how to send notifications to about publish if item should exists in
	 * more than one node?
	 * 
	 * @param from
	 *            - source jid of stanza
	 * @param service
	 * @param node
	 * @param item
	 * @return
	 */
	String publish(JID from, BareJID service, String node, Element item) throws RepositoryException;

	/**
	 * Method removes item from store using logic specific for this service and
	 * node
	 * 
	 * @param from
	 *            - source jid of stanza
	 * @param service
	 * @param node
	 * @param id
	 */
	void retract(JID from, BareJID service, String node, String id) throws RepositoryException;

	/**
	 * Method retrieves list of items for node starting with offset and no more
	 * than limit
	 * 
	 * @param from
	 *            - source jid of stanza
	 * @param service
	 * @param node
	 * @param offset
	 *            - offset of items to return, can be null
	 * @param limit
	 *            - max number of items to return, can be null
	 * @return
	 */
	List<Element> retrieve(JID from, BareJID service, String node, Integer offset, Integer limit) throws RepositoryException;

	/**
	 * Method retrieves item with supplied id from store using specific logic
	 * for this service and node
	 * 
	 * @param from
	 *            - source jid of stanza
	 * @param service
	 * @param node
	 * @param id
	 * @return
	 */
	List<Element> retrieve(JID from, BareJID service, String node, String id) throws RepositoryException;

	/**
	 * Method sets configuration
	 * 
	 * @param props
	 */
	void setProperties(Map<String, Object> props);

}
