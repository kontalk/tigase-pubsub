package tigase.pubsub.repository;

import java.util.Date;

import tigase.xml.Element;

public interface IItems {

	public abstract void deleteItem(String id) throws RepositoryException;

	public abstract Element getItem(String id) throws RepositoryException;

	public abstract Date getItemCreationDate(String id) throws RepositoryException;

	public abstract String[] getItemsIds() throws RepositoryException;

	public abstract Date getItemUpdateDate(String id) throws RepositoryException;

	public abstract void writeItem(long timeInMilis, String id, String publisher, Element item) throws RepositoryException;

}
