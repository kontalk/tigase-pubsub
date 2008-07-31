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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.Subscription;
import tigase.util.JIDUtils;

class Entry {

	private final static Comparator<Item> comparator = new Comparator<Item>() {

		@Override
		public int compare(Item o1, Item o2) {
			return -o1.getCreationDate().compareTo(o2.getCreationDate());
		}
	};

	/**
	 * <bareJID, Affilitation>
	 */
	private final HashMap<String, NodeAffiliation> affiliations = new HashMap<String, NodeAffiliation>();

	private final AbstractNodeConfig config;

	private final Date creationDate;

	/**
	 * ID
	 */
	private final HashMap<String, Item> items = new HashMap<String, Item>();

	private final String name;

	private final ArrayList<Item> sortedItems = new ArrayList<Item>();

	/**
	 * <JID, Subscriber>
	 */
	private final HashMap<String, Subscriber> subscriptions = new HashMap<String, Subscriber>();

	Entry(String name, Date creationDate, AbstractNodeConfig config, List<Subscriber> subscribers,
			List<NodeAffiliation> affiliations, List<Item> items) {
		super();
		this.name = name;
		this.creationDate = creationDate;
		this.config = config;

		if (affiliations != null) {
			for (NodeAffiliation affiliation : affiliations) {
				add(affiliation);
			}
		}

		if (items != null) {
			for (Item item : items) {
				add(item);
			}
		}
		if (subscribers != null)
			for (Subscriber subscriber : subscribers) {
				add(subscriber);
			}

	}

	public void add(Item it) {
		this.items.put(it.getId(), it);
		this.sortedItems.add(it);
		sortItems();
	}

	public void add(NodeAffiliation nodeAffiliation) {
		this.affiliations.put(nodeAffiliation.getJid(), nodeAffiliation);
	}

	public void add(Subscriber subscriber) {
		this.subscriptions.put(subscriber.getJid(), subscriber);
	}

	public void changeAffiliation(String jid, Affiliation affiliation) {
		NodeAffiliation na = this.affiliations.get(JIDUtils.getNodeID(jid));
		na.setAffiliation(affiliation);
	}

	public void changeSubscription(String jid, Subscription subscription) {
		Subscriber subscriber = this.subscriptions.get(jid);
		subscriber.setSubscription(subscription);
	}

	public void deleteItem(String id) {
		Item x = this.items.remove(id);
		if (x != null)
			this.sortedItems.remove(x);
	}

	public String[] getAffiliations() {
		// TODO Auto-generated method stub
		return null;
	}

	public AbstractNodeConfig getConfig() {
		return config;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public Date getItemCreationDate(String id) {
		Item item = items.get(id);
		if (item != null) {
			return item.getCreationDate();
		} else
			return null;
	}

	public Item getItemData(String id) {
		return this.items.get(id);
	}

	public String getName() {
		return name;
	}

	public String[] getSortedItemsId() {
		String[] result = new String[this.sortedItems.size()];
		for (int i = 0; i < this.sortedItems.size(); i++) {
			result[i] = this.sortedItems.get(i).getId();
		}
		return result;
	}

	public Affiliation getSubscriberAffiliation(String jid) {
		NodeAffiliation na = this.affiliations.get(JIDUtils.getNodeID(jid));
		return na == null ? Affiliation.none : na.getAffiliation();
	}

	public String[] getSubscribersJid() {
		return this.subscriptions.keySet().toArray(new String[] {});
	}

	public Subscription getSubscriberSubscription(String jid) {
		Subscriber subscriber = this.subscriptions.get(jid);
		if (subscriber == null) {
			subscriber = this.subscriptions.get(JIDUtils.getNodeID(jid));
		}
		if (subscriber == null) {
			return Subscription.none;
		}
		return subscriber.getSubscription();
	}

	public String getSubscriptionId(String jid) {
		Subscriber subscriber = this.subscriptions.get(jid);
		return subscriber == null ? null : subscriber.getSubid();
	}

	public void removeSubscriber(String jid) {
		this.subscriptions.remove(jid);
	}

	private void sortItems() {
		Collections.sort(this.sortedItems, comparator);
	}
}
