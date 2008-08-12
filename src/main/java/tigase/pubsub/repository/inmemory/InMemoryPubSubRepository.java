/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub.repository.inmemory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.PubSubRepositoryListener;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;

public class InMemoryPubSubRepository implements IPubSubRepository {

	public final static String CREATION_DATE_KEY = "creation-date";

	protected static final String NODE_TYPE_KEY = "pubsub#node_type";

	protected static final String NODES_KEY = "nodes/";

	private final List<PubSubRepositoryListener> listeners = new ArrayList<PubSubRepositoryListener>();

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private final HashMap<String, Entry> nodes = new HashMap<String, Entry>();

	private final PubSubDAO pubSubDB;

	public InMemoryPubSubRepository(PubSubDAO pubSubDB, PubSubConfig pubSubConfig) {
		this.pubSubDB = pubSubDB;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#addListener(tigase
	 * .pubsub.repository.PubSubRepositoryListener)
	 */
	public void addListener(PubSubRepositoryListener listener) {
		this.listeners.add(listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#addSubscriberJid(
	 * java.lang.String, java.lang.String, tigase.pubsub.Affiliation,
	 * tigase.pubsub.Subscription)
	 */
	public String addSubscriberJid(String nodeName, String jid, Affiliation affiliation, Subscription subscription)
			throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		String subid = this.pubSubDB.addSubscriberJid(nodeName, jid, affiliation, subscription);
		Subscriber subscriber = new Subscriber(jid, subid, subscription);
		NodeAffiliation nodeAffiliation = new NodeAffiliation(jid, affiliation);
		entry.add(subscriber);
		entry.add(nodeAffiliation);
		return subid;
	}

	private Date asDate(String d) {
		Long x = Long.valueOf(d);
		return new Date(x.longValue());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#changeAffiliation
	 * (java.lang.String, java.lang.String, tigase.pubsub.Affiliation)
	 */
	public void changeAffiliation(String nodeName, String jid, Affiliation affiliation) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		this.pubSubDB.changeAffiliation(nodeName, jid, affiliation);
		entry.changeAffiliation(jid, affiliation);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#changeSubscription
	 * (java.lang.String, java.lang.String, tigase.pubsub.Subscription)
	 */
	public void changeSubscription(String nodeName, String jid, Subscription subscription) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		this.pubSubDB.changeSubscription(nodeName, jid, subscription);
		entry.changeSubscription(jid, subscription);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#createNode(java.lang
	 * .String, java.lang.String, tigase.pubsub.AbstractNodeConfig,
	 * tigase.pubsub.NodeType, java.lang.String)
	 */
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		this.pubSubDB.createNode(nodeName, ownerJid, nodeConfig, nodeType, collection);
		Entry cnf = readNodeEntry(nodeName);
		if (!"".equals(cnf.getConfig().getCollection())) {
			fireNewNodeCollection(nodeName, null, collection);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#deleteItem(java.lang
	 * .String, java.lang.String)
	 */
	public void deleteItem(String nodeName, String id) throws RepositoryException {
		this.pubSubDB.deleteItem(nodeName, id);
		Entry entry = readNodeEntry(nodeName);
		entry.deleteItem(id);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#deleteNode(java.lang
	 * .String)
	 */
	public void deleteNode(String nodeName) throws RepositoryException {
		this.pubSubDB.deleteNode(nodeName);
		this.nodes.remove(nodeName);
	}

	private void fireNewNodeCollection(String nodeName, String oldCollectionName, String newCollectionName) {
		for (PubSubRepositoryListener listener : this.listeners) {
			listener.onChangeCollection(nodeName, oldCollectionName, newCollectionName);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#forgetConfiguration
	 * (java.lang.String)
	 */
	public void forgetConfiguration(String nodeName) throws RepositoryException {
		this.nodes.remove(nodeName);
		readNodeEntry(nodeName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getAffiliations(java
	 * .lang.String)
	 */
	public NodeAffiliation[] getAffiliations(final String nodeName) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry.getAffiliations();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getBuddyGroups(java
	 * .lang.String, java.lang.String)
	 */
	public String[] getBuddyGroups(String owner, String bareJid) throws RepositoryException {
		return this.pubSubDB.getBuddyGroups(owner, bareJid);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getBuddySubscription
	 * (java.lang.String, java.lang.String)
	 */
	public String getBuddySubscription(String owner, String buddy) throws RepositoryException {
		return this.pubSubDB.getBuddySubscription(owner, buddy);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getItem(java.lang
	 * .String, java.lang.String)
	 */
	public Element getItem(String nodeName, String id) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		Item item = entry.getItemData(id);
		return item != null ? item.getData() : null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getItemCreationDate
	 * (java.lang.String, java.lang.String)
	 */
	public String getItemCreationDate(String nodeName, String id) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		if (entry == null)
			return null;
		return String.valueOf(entry.getItemCreationDate(id).getTime());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getItemsIds(java.
	 * lang.String)
	 */
	public String[] getItemsIds(String nodeName) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry == null ? null : entry.getSortedItemsId();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getNodeConfig(java
	 * .lang.String)
	 */
	public AbstractNodeConfig getNodeConfig(String nodeName) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry == null ? null : entry.getConfig();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.pubsub.repository.inmemory.IPubSubRepository#getNodesList()
	 */
	public String[] getNodesList() throws RepositoryException {
		return this.pubSubDB.getNodesList();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.pubsub.repository.inmemory.IPubSubRepository#getPubSubDAO()
	 */
	public IPubSubDAO getPubSubDAO() {
		return this.pubSubDB;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getSubscriberAffiliation
	 * (java.lang.String, java.lang.String)
	 */
	public NodeAffiliation getSubscriberAffiliation(String nodeName, String jid) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry.getSubscriberAffiliation(jid);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getSubscription(java
	 * .lang.String, java.lang.String)
	 */
	public Subscription getSubscription(String nodeName, String jid) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		if (entry == null)
			return Subscription.none;
		return entry.getSubscriberSubscription(jid);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getSubscriptionId
	 * (java.lang.String, java.lang.String)
	 */
	public String getSubscriptionId(String nodeName, String jid) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry.getSubscriptionId(jid);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getSubscriptions(
	 * java.lang.String)
	 */
	public Subscriber[] getSubscriptions(String nodeName) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry == null ? null : entry.getSubscribersJid();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#getUserRoster(java
	 * .lang.String)
	 */
	public String[] getUserRoster(String owner) throws RepositoryException {
		return this.pubSubDB.getUserRoster(owner);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.pubsub.repository.inmemory.IPubSubRepository#init()
	 */
	public void init() {
		try {
			log.config("InMemory PS Repository initializing...");
			this.nodes.clear();
			String[] nodes = this.pubSubDB.getNodesList();
			if (nodes != null)
				for (String nodeName : nodes) {
					readNodeEntry(nodeName);
				}
		} catch (Exception e) {
			log.log(Level.SEVERE, "Can't initialize InMemory PS!", e);
			throw new RuntimeException("Can't initialize InMemory PS!", e);
		}
	}

	protected List<Item> readItems(String nodeName) throws RepositoryException {
		String[] ids = this.pubSubDB.getItemsIds(nodeName);
		if (ids != null) {
			List<Item> result = new ArrayList<Item>();
			for (String id : ids) {
				final Element data = this.pubSubDB.getItem(nodeName, id);
				final Date creationDate = asDate(this.pubSubDB.getItemCreationDate(nodeName, id));
				final Date updateDate = asDate(this.pubSubDB.getItemUpdateDate(nodeName, id));
				final String pubslisher = this.pubSubDB.getItemPublisher(nodeName, id);
				Item item = new Item(id, data, creationDate, updateDate, pubslisher);
				result.add(item);
			}
			return result;
		}
		return null;
	}

	protected List<NodeAffiliation> readNodeAffiliations(final String nodeName) throws RepositoryException {
		String[] jids = this.pubSubDB.getAffiliations(nodeName);
		List<NodeAffiliation> result = new ArrayList<NodeAffiliation>();
		if (jids != null) {
			for (String jid : jids) {
				Affiliation affiliation = this.pubSubDB.getSubscriberAffiliation(nodeName, jid);
				NodeAffiliation na = new NodeAffiliation(jid, affiliation);
				result.add(na);
			}
		}
		return result;
	}

	protected AbstractNodeConfig readNodeConfig(final String nodeName) throws RepositoryException {
		return this.pubSubDB.getNodeConfig(nodeName);
	}

	protected Entry readNodeEntry(final String nodeName) throws RepositoryException {
		Entry entry = this.nodes.get(nodeName);
		if (entry == null) {
			log.fine("Reading '" + nodeName + "' node entry...");
			AbstractNodeConfig cnf = readNodeConfig(nodeName);
			if (cnf == null)
				return null;
			Date creationDate = this.pubSubDB.getNodeCreationDate(nodeName);

			List<Subscriber> subscribers = readSubscribers(nodeName);
			List<NodeAffiliation> affiliations = readNodeAffiliations(nodeName);
			List<Item> items = readItems(nodeName);
			entry = new Entry(nodeName, creationDate, cnf, subscribers, affiliations, items);
			this.nodes.put(nodeName, entry);
		}
		return entry;
	}

	protected List<Subscriber> readSubscribers(final String nodeName) throws RepositoryException {
		String[] jids = this.pubSubDB.getSubscriptions(nodeName);
		List<Subscriber> result = new ArrayList<Subscriber>();
		if (jids != null)
			for (String jid : jids) {
				String subId = this.pubSubDB.getSubscriptionId(nodeName, jid);
				Subscription subscription = this.pubSubDB.getSubscription(nodeName, jid);
				Subscriber s = new Subscriber(jid, subId, subscription);
				result.add(s);
			}

		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#removeListener(tigase
	 * .pubsub.repository.PubSubRepositoryListener)
	 */
	public void removeListener(PubSubRepositoryListener listener) {
		this.listeners.remove(listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#removeSubscriber(
	 * java.lang.String, java.lang.String)
	 */
	public void removeSubscriber(String nodeName, String jid) throws RepositoryException {
		this.pubSubDB.removeSubscriber(nodeName, jid);
		Entry entry = readNodeEntry(nodeName);
		entry.removeSubscriber(jid);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#setNewNodeCollection
	 * (java.lang.String, java.lang.String)
	 */
	public void setNewNodeCollection(String nodeName, String collectionNew) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		this.pubSubDB.setNewNodeCollection(nodeName, collectionNew);
		final String oldCollectionName = entry.getConfig().getCollection();
		entry.getConfig().setCollection(collectionNew);
		fireNewNodeCollection(nodeName, oldCollectionName, collectionNew);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#update(java.lang.
	 * String, tigase.pubsub.AbstractNodeConfig)
	 */
	public void update(String nodeName, AbstractNodeConfig nodeConfig) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		final String oldCollectionName = entry.getConfig().getCollection();
		this.pubSubDB.update(nodeName, nodeConfig);
		entry.getConfig().copyFrom(nodeConfig);
		final String newCollectionName = entry.getConfig().getCollection();
		if (!oldCollectionName.equals(newCollectionName)) {
			fireNewNodeCollection(nodeName, oldCollectionName, newCollectionName);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.inmemory.IPubSubRepository#writeItem(java.lang
	 * .String, long, java.lang.String, java.lang.String, tigase.xml.Element)
	 */
	public void writeItem(String nodeName, long timeInMilis, String id, String publisher, Element item) throws RepositoryException {
		this.pubSubDB.writeItem(nodeName, timeInMilis, id, publisher, item);
		Item it = new Item(id, item, new Date(timeInMilis), new Date(timeInMilis), publisher);
		Entry entry = readNodeEntry(nodeName);
		entry.add(it);
	}

}
