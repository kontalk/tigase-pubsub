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
package tigase.pubsub;

import java.util.HashSet;
import java.util.Set;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;

public class NodeConfig implements Cloneable {

	private static boolean asBoolean(String data, boolean defValue) {
		try {
			return Boolean.parseBoolean(data);
		} catch (Exception e) {
			return defValue;
		}
	}

	private static int asInteger(String data, int defValue) {
		try {
			return Integer.parseInt(data);
		} catch (Exception e) {
			return defValue;
		}
	}

	private static String asString(String data, String defValue) {
		try {
			return new String(data);
		} catch (Exception e) {
			return defValue;
		}
	}

	protected static <T extends Enum<?>> String[] convSetToTable(Set<T> set) {
		String[] result = new String[set.size()];
		int i = 0;
		for (T x : set) {
			result[i++] = x.name();
		}
		return result;
	}

	/**
	 * Specify the subscriber model
	 */
	private AccessModel access_model = AccessModel.open;

	/**
	 *Deliver event notifications
	 */
	private boolean deliver_notifications = true;

	/**
	 * Deliver payloads with event notifications
	 */
	private boolean deliver_payloads = true;

	/**
	 * Max # of items to persist
	 */
	private int max_items = 10;

	/**
	 * Max payload size in bytes
	 */
	private int max_payload_size = 9216;

	/**
	 * Notify subscribers when the node configuration changes
	 */
	private boolean notify_config = false;

	/**
	 * Notify subscribers when the node is deleted
	 */
	private boolean notify_delete = false;

	/**
	 * Notify subscribers when items are removed from the node
	 */
	private boolean notify_retract = false;

	/**
	 * Persist items to storage
	 */
	private boolean persist_items = true;

	/**
	 * Deliver notifications only to available users
	 */
	private boolean presence_based_delivery = false;

	/**
	 * Specify the publisher model
	 */
	private PublisherModel publish_model = PublisherModel.publishers;

	/**
	 * Roster groups allowed to subscribe
	 */
	private final Set<RosterGroups> roster_groups_allowed = new HashSet<RosterGroups>();

	/**
	 * When to send the last published item
	 */
	private SendLastPublishedItem send_last_published_item = SendLastPublishedItem.never;

	/**
	 * Whether to allow subscriptions
	 */
	private boolean subscribe = true;

	/**
	 * A friendly name for the node
	 */
	private String title = "";

	@Override
	public NodeConfig clone() throws CloneNotSupportedException {
		return (NodeConfig) super.clone();
	}

	public AccessModel getAccess_model() {
		return access_model;
	}

	public int getMax_items() {
		return max_items;
	}

	public int getMax_payload_size() {
		return max_payload_size;
	}

	public PublisherModel getPublish_model() {
		return publish_model;
	}

	public Set<RosterGroups> getRoster_groups_allowed() {
		return roster_groups_allowed;
	}

	public SendLastPublishedItem getSend_last_published_item() {
		return send_last_published_item;
	}

	public String getTitle() {
		return title;
	}

	public boolean isDeliver_notifications() {
		return deliver_notifications;
	}

	public boolean isDeliver_payloads() {
		return deliver_payloads;
	}

	public boolean isNotify_config() {
		return notify_config;
	}

	public boolean isNotify_delete() {
		return notify_delete;
	}

	public boolean isNotify_retract() {
		return notify_retract;
	}

	public boolean isPersist_items() {
		return persist_items;
	}

	public boolean isPresence_based_delivery() {
		return presence_based_delivery;
	}

	public boolean isSubscribe() {
		return subscribe;
	}

	public void read(UserRepository repo, PubSubConfig pubSubConfig, String subnode) throws UserNotFoundException,
			TigaseDBException {
		this.title = repo.getData(pubSubConfig.getServiceName(), subnode, "title");
		this.deliver_notifications = asBoolean(repo.getData(pubSubConfig.getServiceName(), subnode, "deliver_notifications"), true);
		this.deliver_payloads = asBoolean(repo.getData(pubSubConfig.getServiceName(), subnode, "deliver_payloads"), true);
		this.notify_config = asBoolean(repo.getData(pubSubConfig.getServiceName(), subnode, "notify_config"), false);
		this.notify_delete = asBoolean(repo.getData(pubSubConfig.getServiceName(), subnode, "notify_delete"), false);
		this.notify_retract = asBoolean(repo.getData(pubSubConfig.getServiceName(), subnode, "notify_retract"), false);
		this.persist_items = asBoolean(repo.getData(pubSubConfig.getServiceName(), subnode, "persist_items"), true);
		this.max_items = asInteger(repo.getData(pubSubConfig.getServiceName(), subnode, "max_items"), 10);
		this.subscribe = asBoolean(repo.getData(pubSubConfig.getServiceName(), subnode, "subscribe"), true);
		this.access_model = AccessModel.valueOf(asString(repo.getData(pubSubConfig.getServiceName(), subnode, "access_model"),
				AccessModel.open.name()));
		String[] tmp = repo.getDataList(pubSubConfig.getServiceName(), subnode, "roster_groups_allowed");
		this.roster_groups_allowed.clear();
		if (tmp != null)
			for (String string : tmp) {
				RosterGroups rg = RosterGroups.valueOf(string);
				this.roster_groups_allowed.add(rg);
			}
		this.publish_model = PublisherModel.valueOf(asString(repo.getData(pubSubConfig.getServiceName(), subnode, "publish_model"),
				PublisherModel.publishers.name()));
		this.send_last_published_item = SendLastPublishedItem.valueOf(asString(repo.getData(pubSubConfig.getServiceName(), subnode,
				"send_last_published_item"), SendLastPublishedItem.never.name()));
		this.max_payload_size = asInteger(repo.getData(pubSubConfig.getServiceName(), subnode, "max_payload_size"), 9216);
		this.presence_based_delivery = asBoolean(repo.getData(pubSubConfig.getServiceName(), subnode, "presence_based_delivery"),
				false);
	}

	public void setAccess_model(AccessModel access_model) {
		this.access_model = access_model;
	}

	public void setDeliver_notifications(boolean deliver_notifications) {
		this.deliver_notifications = deliver_notifications;
	}

	public void setDeliver_payloads(boolean deliver_payloads) {
		this.deliver_payloads = deliver_payloads;
	}

	public void setMax_items(int max_items) {
		this.max_items = max_items;
	}

	public void setMax_payload_size(int max_payload_size) {
		this.max_payload_size = max_payload_size;
	}

	public void setNotify_config(boolean notify_config) {
		this.notify_config = notify_config;
	}

	public void setNotify_delete(boolean notify_delete) {
		this.notify_delete = notify_delete;
	}

	public void setNotify_retract(boolean notify_retract) {
		this.notify_retract = notify_retract;
	}

	public void setPersist_items(boolean persist_items) {
		this.persist_items = persist_items;
	}

	public void setPresence_based_delivery(boolean presence_based_delivery) {
		this.presence_based_delivery = presence_based_delivery;
	}

	public void setPublish_model(PublisherModel publish_model) {
		this.publish_model = publish_model;
	}

	public void setSend_last_published_item(SendLastPublishedItem send_last_published_item) {
		this.send_last_published_item = send_last_published_item;
	}

	public void setSubscribe(boolean subscribe) {
		this.subscribe = subscribe;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void write(UserRepository repo, PubSubConfig pubSubConfig, String subnode) throws UserNotFoundException,
			TigaseDBException {
		repo.setData(pubSubConfig.getServiceName(), subnode, "title", title == null ? "" : title);
		repo.setData(pubSubConfig.getServiceName(), subnode, "deliver_notifications", String.valueOf(deliver_notifications));
		repo.setData(pubSubConfig.getServiceName(), subnode, "deliver_payloads", String.valueOf(deliver_payloads));
		repo.setData(pubSubConfig.getServiceName(), subnode, "notify_config", String.valueOf(notify_config));
		repo.setData(pubSubConfig.getServiceName(), subnode, "notify_delete", String.valueOf(notify_delete));
		repo.setData(pubSubConfig.getServiceName(), subnode, "notify_retract", String.valueOf(notify_retract));
		repo.setData(pubSubConfig.getServiceName(), subnode, "persist_items", String.valueOf(persist_items));
		repo.setData(pubSubConfig.getServiceName(), subnode, "max_items", String.valueOf(max_items));
		repo.setData(pubSubConfig.getServiceName(), subnode, "subscribe", String.valueOf(subscribe));
		repo.setData(pubSubConfig.getServiceName(), subnode, "access_model", access_model.name());
		repo.setDataList(pubSubConfig.getServiceName(), subnode, "roster_groups_allowed", convSetToTable(roster_groups_allowed));
		repo.setData(pubSubConfig.getServiceName(), subnode, "publish_model", publish_model.name());
		repo.setData(pubSubConfig.getServiceName(), subnode, "max_payload_size", String.valueOf(max_payload_size));
		repo.setData(pubSubConfig.getServiceName(), subnode, "send_last_published_item", send_last_published_item.name());
		repo.setData(pubSubConfig.getServiceName(), subnode, "presence_based_delivery", String.valueOf(presence_based_delivery));
	}
}
