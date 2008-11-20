package tigase.pubsub.repository.cached;

import java.util.Date;

import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;

class Items implements IItems {

	private final IPubSubDAO dao;

	private final String nodeName;

	public Items(String nodeName, IPubSubDAO dao) {
		this.dao = dao;
		this.nodeName = nodeName;
	}

	@Override
	public void deleteItem(String id) throws RepositoryException {
		this.dao.deleteItem(nodeName, id);
	}

	@Override
	public Element getItem(String id) throws RepositoryException {
		return this.dao.getItem(nodeName, id);
	}

	@Override
	public Date getItemCreationDate(String id) throws RepositoryException {
		return this.dao.getItemCreationDate(nodeName, id);
	}

	@Override
	public String[] getItemsIds() throws RepositoryException {
		return this.dao.getItemsIds(nodeName);
	}

	@Override
	public Date getItemUpdateDate(String id) throws RepositoryException {
		return this.dao.getItemUpdateDate(nodeName, id);
	}

	@Override
	public void writeItem(long timeInMilis, String id, String publisher, Element item) throws RepositoryException {
		this.dao.writeItem(nodeName, timeInMilis, id, publisher, item);
	}

}
