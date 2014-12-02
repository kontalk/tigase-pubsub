package tigase.pubsub.repository;

import java.util.Date;
import java.util.List;

import tigase.xml.Element;

public interface IItems {

	public static class ItemMeta {
		private final String node;
		private final String id;
		private final Date creationDate;
		private final Date updateDate;
		
		public ItemMeta(String node, String id, Date creationDate) {
			this.node = node;
			this.id = id;
			this.creationDate = creationDate;
			this.updateDate = creationDate;
		}

		public ItemMeta(String node, String id, Date creationDate, Date updateDate) {
			this.node = node;
			this.id = id;
			this.creationDate = creationDate;
			this.updateDate = updateDate;
		}
		
		public String getNode() {
			return node;
		}
		
		public String getId() {
			return id;
		}
		
		public Date getCreationDate() {
			return creationDate;
		}

		public Date getItemUpdateDate() {
			return updateDate;
		}
	}
	
	public abstract void deleteItem(String id) throws RepositoryException;

	public abstract Element getItem(String id) throws RepositoryException;

	public abstract Date getItemCreationDate(String id) throws RepositoryException;

	public abstract String[] getItemsIds() throws RepositoryException;
	
	public abstract String[] getItemsIdsSince(Date since) throws RepositoryException;

	public abstract List<ItemMeta> getItemsMeta() throws RepositoryException;
	
	public abstract Date getItemUpdateDate(String id) throws RepositoryException;

	public abstract void writeItem(long timeInMilis, String id, String publisher, Element item) throws RepositoryException;

}
