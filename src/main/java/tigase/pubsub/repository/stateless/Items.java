package tigase.pubsub.repository.stateless;

import java.util.Date;

import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

class Items implements IItems {

	private final IPubSubDAO dao;

	private final String nodeName;

	private final BareJID serviceJid;

	public Items(BareJID serviceJid, String nodeName, IPubSubDAO dao) {
		this.dao = dao;
		this.nodeName = nodeName;
		this.serviceJid = serviceJid;
	}

	@Override
	public void deleteItem(String id) throws RepositoryException {
		this.dao.deleteItem(serviceJid, nodeName, id);
	}

	@Override
	public Element getItem(String id) throws RepositoryException {
		return this.dao.getItem(serviceJid, nodeName, id);
	}

	@Override
	public Date getItemCreationDate(String id) throws RepositoryException {
		return this.dao.getItemCreationDate(serviceJid, nodeName, id);
	}

	@Override
	public String[] getItemsIds() throws RepositoryException {
		return this.dao.getItemsIds(serviceJid, nodeName);
	}

	@Override
	public Date getItemUpdateDate(String id) throws RepositoryException {
		return this.dao.getItemUpdateDate(serviceJid, nodeName, id);
	}

	@Override
	public void writeItem(long timeInMilis, String id, String publisher, Element item) throws RepositoryException {
		this.dao.writeItem(serviceJid, nodeName, timeInMilis, id, publisher, item);
	}

}
