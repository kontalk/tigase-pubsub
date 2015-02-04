package tigase.pubsub.repository.cached;

import java.util.Date;
import java.util.List;

import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;

import tigase.xmpp.BareJID;

import java.util.logging.Level;
import java.util.logging.Logger;

class Items<T> implements IItems {

	private static final Logger log = Logger.getLogger(Items.class.getName());

	private final IPubSubDAO<T> dao;

	private final T nodeId;
	
	private final String nodeName;

	private final BareJID serviceJid;

	public Items(T nodeId, BareJID serviceJid, String nodeName, IPubSubDAO dao) {
		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "Constructing Items, serviceJid: {0}, nodeName: {1}, nodeId: {2}, dao: {3}",
							 new Object[] { serviceJid, nodeName, nodeId, dao } );
		}
		this.nodeId = nodeId;
		this.dao = dao;
		this.nodeName = nodeName;
		this.serviceJid = serviceJid;
	}

	@Override
	public void deleteItem(String id) throws RepositoryException {
		this.dao.deleteItem(serviceJid, nodeId, id);
	}

	@Override
	public Element getItem(String id) throws RepositoryException {
		return this.dao.getItem(serviceJid, nodeId, id);
	}

	@Override
	public Date getItemCreationDate(String id) throws RepositoryException {
		return this.dao.getItemCreationDate(serviceJid, nodeId, id);
	}

	@Override
	public String[] getItemsIds() throws RepositoryException {
		return this.dao.getItemsIds(serviceJid, nodeId);
	}

	@Override
	public String[] getItemsIdsSince(Date since) throws RepositoryException {
		return this.dao.getItemsIdsSince(serviceJid, nodeId, since);
	}
	
	@Override
	public List<IItems.ItemMeta> getItemsMeta() throws RepositoryException {
		return this.dao.getItemsMeta(serviceJid, nodeId, nodeName);
	}
	
	@Override
	public Date getItemUpdateDate(String id) throws RepositoryException {
		return this.dao.getItemUpdateDate(serviceJid, nodeId, id);
	}

	@Override
	public void writeItem(long timeInMilis, String id, String publisher, Element item) throws RepositoryException {
		this.dao.writeItem(serviceJid, nodeId, timeInMilis, id, publisher, item);
	}

}
