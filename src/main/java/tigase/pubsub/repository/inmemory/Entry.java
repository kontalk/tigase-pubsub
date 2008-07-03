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

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.Subscription;

class Entry {

	private final String name;

	private final Date creationDate;

	private final AbstractNodeConfig config;

	/**
	 * <JID, Subscriber>
	 */
	private final HashMap<String, Subscriber> subscribers = new HashMap<String, Subscriber>();

	/**
	 * ID
	 */
	private final HashMap<String, Item> items = new HashMap<String, Item>();

	Entry(String name, Date creationDate, AbstractNodeConfig config, List<Subscriber> subscribers, List<Item> items) {
		super();
		this.name = name;
		this.creationDate = creationDate;
		this.config = config;

		if (items != null)
			for (Item item : items) {
				add(item);
			}

		if (subscribers != null)
			for (Subscriber subscriber : subscribers) {
				add(subscriber);
			}

	}

	public void add(Item it) {
		this.items.put(it.getId(), it);
	}

	public void add(Subscriber subscriber) {
		this.subscribers.put(subscriber.getJid(), subscriber);
	}

	public void changeSubscription(String jid, Subscription subscription) {
		Subscriber subscriber = this.subscribers.get(jid);
		subscriber.setSubscription(subscription);
	}

	public void deleteItem(String id) {
		this.items.remove(id);
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

	public String getName() {
		return name;
	}

	public Affiliation getSubscriberAffiliation(String jid) {
		Subscriber subscriber = this.subscribers.get(jid);
		if (subscriber == null)
			return null;
		return subscriber.getAffiliation();
	}

	public String[] getSubscribersJid() {
		return this.subscribers.keySet().toArray(new String[] {});
	}

	public Subscription getSubscriberSubscription(String jid) {
		Subscriber subscriber = this.subscribers.get(jid);
		return subscriber.getSubscription();
	}

	public String getSubscriptionId(String jid) {
		Subscriber subscriber = this.subscribers.get(jid);
		return subscriber.getSubid();
	}

	public void removeSubscriber(String jid) {
		this.subscribers.remove(jid);
	}
}
