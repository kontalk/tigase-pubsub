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

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AccessModel;
import tigase.pubsub.Affiliation;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.PubSubRepositoryListener;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.StatelessPubSubRepository;
import tigase.xml.Element;

public class InMemoryPubSubRepository implements IPubSubRepository {

	private final StatelessPubSubRepository pubSubDB;

	public final static String CREATION_DATE_KEY = "creation-date";

	protected static final String NODE_TYPE_KEY = "pubsub#node_type";

	protected static final String NODES_KEY = "nodes/";

	private final PubSubConfig config;

	private final HashMap<String, Entry> nodes = new HashMap<String, Entry>();

	private final List<PubSubRepositoryListener> listeners = new ArrayList<PubSubRepositoryListener>();

	public InMemoryPubSubRepository(StatelessPubSubRepository pubSubDB, PubSubConfig pubSubConfig) {
		this.pubSubDB = pubSubDB;
		this.config = pubSubConfig;
	}

	@Override
	public void addListener(PubSubRepositoryListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public String addSubscriberJid(String nodeName, String jid, Affiliation affiliation, Subscription subscription)
			throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		String subid = this.pubSubDB.addSubscriberJid(nodeName, jid, affiliation, subscription);
		Subscriber subscriber = new Subscriber(jid, subid, affiliation, subscription);
		entry.add(subscriber);
		return subid;
	}

	@Override
	public void changeAffiliation(String nodeName, String jid, Affiliation affiliation) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		this.pubSubDB.changeAffiliation(nodeName, jid, affiliation);
		entry.changeAffiliation(jid, affiliation);
	}

	@Override
	public void changeSubscription(String nodeName, String jid, Subscription subscription) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		this.pubSubDB.changeSubscription(nodeName, jid, subscription);
		entry.changeSubscription(jid, subscription);
	}

	@Override
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		this.pubSubDB.createNode(nodeName, ownerJid, nodeConfig, nodeType, collection);
		Entry cnf = readNodeEntry(nodeName);
		if (!"".equals(cnf.getConfig().getCollection())) {
			fireNewNodeCollection(nodeName, null, collection);
		}
	}

	@Override
	public void deleteItem(String nodeName, String id) throws RepositoryException {
		this.pubSubDB.deleteItem(nodeName, id);
		Entry entry = readNodeEntry(nodeName);
		entry.deleteItem(id);
	}

	@Override
	public void deleteNode(String nodeName) throws RepositoryException {
		this.pubSubDB.deleteNode(nodeName);
		this.nodes.remove(nodeName);
		fireNodeDelete(nodeName);
	}

	private void fireNewNodeCollection(String nodeName, String oldCollectionName, String newCollectionName) {
		for (PubSubRepositoryListener listener : this.listeners) {
			listener.onChangeCollection(nodeName, oldCollectionName, newCollectionName);
		}
	}

	private void fireNodeDelete(String nodeName) {
		for (PubSubRepositoryListener listener : this.listeners) {
			listener.onNodeDelete(nodeName);
		}
	}

	@Override
	public String getCollectionOf(String nodeName) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		if (entry == null)
			return null;
		return entry.getConfig().getCollection();
	}

	@Override
	public Element getItem(String nodeName, String id) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		Item item = entry.getItemData(id);
		return item != null ? item.getData() : null;
	}

	@Override
	public String getItemCreationDate(String nodeName, String id) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		if (entry == null)
			return null;
		return String.valueOf(entry.getItemCreationDate(id).getTime());
	}

	@Override
	public String[] getItemsIds(String nodeName) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry.getSortedItemsId();
	}

	@Override
	public AccessModel getNodeAccessModel(String nodeName) throws RepositoryException {
		try {
			Entry entry = readNodeEntry(nodeName);
			if (entry == null)
				return null;
			return entry.getConfig().getNodeAccessModel();
		} catch (Exception e) {
			throw new RepositoryException("AccessModel getting error", e);
		}
	}

	@Override
	public String[] getNodeChildren(String node) throws RepositoryException {
		Entry entry = readNodeEntry(node);
		return entry.getConfig().getChildren();
	}

	@Override
	public AbstractNodeConfig getNodeConfig(String nodeName) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry.getConfig();
	}

	@Override
	public String[] getNodesList() throws RepositoryException {
		return this.nodes.keySet().toArray(new String[] {});
	}

	@Override
	public NodeType getNodeType(String nodeName) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		if (entry == null)
			return null;
		return entry.getConfig().getNodeType();
	}

	@Override
	public Affiliation getSubscriberAffiliation(String nodeName, String jid) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry.getSubscriberAffiliation(jid);
	}

	@Override
	public String[] getSubscribersJid(String nodeName) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry.getSubscribersJid();
	}

	@Override
	public Subscription getSubscription(String nodeName, String jid) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		if (entry == null)
			return Subscription.none;
		return entry.getSubscriberSubscription(jid);
	}

	@Override
	public String getSubscriptionId(String nodeName, String jid) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		return entry.getSubscriptionId(jid);
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

	private Date asDate(String d) {
		Long x = Long.valueOf(d);
		return new Date(x.longValue());
	}

	protected AbstractNodeConfig readNodeConfig(final String nodeName) throws RepositoryException {
		return this.pubSubDB.getNodeConfig(nodeName);
	}

	protected Entry readNodeEntry(final String nodeName) throws RepositoryException {
		Entry entry = this.nodes.get(nodeName);
		if (entry == null) {
			AbstractNodeConfig cnf = readNodeConfig(nodeName);
			if (cnf == null)
				return null;
			Date creationDate = this.pubSubDB.getNodeCreationDate(nodeName);

			List<Subscriber> subscribers = readSubscribers(nodeName);
			List<Item> items = readItems(nodeName);
			entry = new Entry(nodeName, creationDate, cnf, subscribers, items);
			this.nodes.put(nodeName, entry);
			// XXX add reading nodes and subscribers

		}
		return entry;
	}

	protected List<Subscriber> readSubscribers(final String nodeName) throws RepositoryException {
		String[] jids = this.pubSubDB.getSubscribersJid(nodeName);
		List<Subscriber> result = new ArrayList<Subscriber>();

		for (String jid : jids) {
			String subId = this.pubSubDB.getSubscriptionId(nodeName, jid);
			Affiliation affiliation = this.pubSubDB.getSubscriberAffiliation(nodeName, jid);
			Subscription subscription = this.pubSubDB.getSubscription(nodeName, jid);
			Subscriber s = new Subscriber(jid, subId, affiliation, subscription);
			result.add(s);
		}

		return result;
	}

	@Override
	public void removeListener(PubSubRepositoryListener listener) {
		this.listeners.remove(listener);
	}

	@Override
	public void removeSubscriber(String nodeName, String jid) throws RepositoryException {
		this.pubSubDB.removeSubscriber(nodeName, jid);
		Entry entry = readNodeEntry(nodeName);
		entry.removeSubscriber(jid);
	}

	@Override
	public void setNewNodeCollection(String nodeName, String collectionNew) throws RepositoryException {
		Entry entry = readNodeEntry(nodeName);
		this.pubSubDB.setNewNodeCollection(nodeName, collectionNew);
		final String oldCollectionName = entry.getConfig().getCollection();
		entry.getConfig().setCollection(collectionNew);
		fireNewNodeCollection(nodeName, oldCollectionName, collectionNew);
	}

	@Override
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

	@Override
	public void writeItem(String nodeName, long timeInMilis, String id, String publisher, Element item) throws RepositoryException {
		this.pubSubDB.writeItem(nodeName, timeInMilis, id, publisher, item);
		Item it = new Item(id, item, new Date(timeInMilis), new Date(timeInMilis), publisher);
		Entry entry = readNodeEntry(nodeName);
		entry.add(it);
	}

}
