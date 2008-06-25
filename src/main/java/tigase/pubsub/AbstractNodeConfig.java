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
import java.util.List;
import java.util.Set;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.form.Form;
import tigase.xml.Element;

public class AbstractNodeConfig implements Cloneable {

	private static final String DEFAULT_ACCESS_MODEL = AccessModel.open.name();

	private List<String> children = null;

	public void addTransientNodeChildren(List<String> nodeChildren) {
		if (nodeChildren != null && nodeChildren.size() > 0) {
			this.children = nodeChildren;
		} else
			this.children = null;
	}

	private final static String DEFAULT_COLLECTION = "";

	private final static boolean DEFAULT_DELIVER_NOTIFICATIONS = true;

	private final static boolean DEFAULT_DELIVER_PAYLOADS = true;

	private static final int DEFAULT_MAX_ITEMS = 10;

	private static final int DEFAULT_MAX_PAYLOAD_SIZE = 9216;

	private final static boolean DEFAULT_NOTIFY_CONFIG = false;

	private final static boolean DEFAULT_NOTIFY_DELETE = false;

	private final static boolean DEFAULT_NOTIFY_RETRACT = false;

	private final static boolean DEFAULT_PERSIST_ITEMS = false;

	private static final boolean DEFAULT_PRESENCE_BASED_DELIVERY = false;

	private static final String DEFAULT_PUBLISH_MODEL = PublisherModel.publishers.name();

	private static final String DEFAULT_SEND_LAST_PUBLISHED_ITEM = SendLastPublishedItem.never.name();

	private static final boolean DEFAULT_SUBSCRIBE = true;

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

	protected static String[] convSetToTable(Set<String> set) {
		String[] result = new String[set.size()];
		int i = 0;
		for (String x : set) {
			result[i++] = x;
		}
		return result;
	}

	/**
	 * Specify the subscriber model
	 */
	private AccessModel access_model = AccessModel.open;

	/**
	 * The collection with which a node is affiliated
	 */
	private String collection = DEFAULT_COLLECTION;

	/**
	 *Deliver event notifications
	 */
	private boolean deliver_notifications = true;

	/**
	 * Deliver payloads with event notifications
	 */
	private boolean deliver_payloads = DEFAULT_DELIVER_PAYLOADS;

	/**
	 * Max # of items to persist
	 */
	private int max_items = DEFAULT_MAX_ITEMS;

	/**
	 * Max payload size in bytes
	 */
	private int max_payload_size = DEFAULT_MAX_PAYLOAD_SIZE;

	/**
	 * Notify subscribers when the node configuration changes
	 */
	private boolean notify_config = DEFAULT_NOTIFY_CONFIG;

	/**
	 * Notify subscribers when the node is deleted
	 */
	private boolean notify_delete = DEFAULT_NOTIFY_DELETE;

	/**
	 * Notify subscribers when items are removed from the node
	 */
	private boolean notify_retract = DEFAULT_NOTIFY_RETRACT;

	/**
	 * Persist items to storage
	 */
	private boolean persist_items = DEFAULT_PERSIST_ITEMS;

	/**
	 * Deliver notifications only to available users
	 */
	private boolean presence_based_delivery = DEFAULT_PRESENCE_BASED_DELIVERY;

	/**
	 * Specify the publisher model
	 */
	private PublisherModel publish_model = PublisherModel.valueOf(DEFAULT_PUBLISH_MODEL);

	/**
	 * Roster groups allowed to subscribe
	 */
	private final Set<String> roster_groups_allowed = new HashSet<String>();

	/**
	 * When to send the last published item
	 */
	private SendLastPublishedItem send_last_published_item = SendLastPublishedItem.valueOf(DEFAULT_SEND_LAST_PUBLISHED_ITEM);

	/**
	 * Whether to allow subscriptions
	 */
	private boolean subscribe = DEFAULT_SUBSCRIBE;

	/**
	 * A friendly name for the node
	 */
	private String title = "";

	private String[] asStrinTable(Enum<?>[] values) {
		String[] result = new String[values.length];
		int i = 0;
		for (Enum<?> v : values) {
			result[i++] = v.name();
		}
		return result;
	}

	@Override
	public LeafNodeConfig clone() throws CloneNotSupportedException {
		return (LeafNodeConfig) super.clone();
	}

	public AccessModel getAccess_model() {
		return access_model;
	}

	public String getCollection() {
		return collection;
	}

	public Element getJabberForm() {
		Form form = new Form("form", null, null);
		form.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/pubsub#node_config"));
		form.addField(Field.fieldTextSingle("pubsub#title", this.title, "A friendly name for the node"));
		form.addField(Field.fieldTextSingle("pubsub#collection", this.collection, "The collection with which a node is affiliated"));
		form.addField(Field.fieldBoolean("pubsub#deliver_notifications", this.deliver_notifications, "Deliver event notifications"));
		form.addField(Field.fieldBoolean("pubsub#notify_config", this.notify_config, "Notify subscribers when the node configuration changes"));
		form.addField(Field.fieldBoolean("pubsub#notify_delete", this.notify_delete, "Notify subscribers when the node is deleted"));
		form.addField(Field.fieldBoolean("pubsub#notify_retract", this.notify_retract, "Notify subscribers when items are removed from the node"));
		form.addField(Field.fieldBoolean("pubsub#persist_items", this.persist_items, "Persist items to storage"));
		form.addField(Field.fieldTextSingle("pubsub#max_items", String.valueOf(this.max_items), "Max # of items to persist"));
		form.addField(Field.fieldBoolean("pubsub#subscribe", this.subscribe, "Whether to allow subscriptions"));
		form.addField(Field.fieldListSingle("pubsub#access_model", this.access_model.name(), "Specify the subscriber model", null, asStrinTable(AccessModel.values())));
		form.addField(Field.fieldListMulti("pubsub#roster_groups_allowed", this.roster_groups_allowed.toArray(new String[] {}), "Roster groups allowed to subscribe", null, new String[] {}));
		form.addField(Field.fieldListSingle("pubsub#publish_model", this.publish_model.name(), "Specify the publisher model", null, asStrinTable(PublisherModel.values())));
		form.addField(Field.fieldTextSingle("pubsub#max_payload_size", String.valueOf(this.max_payload_size), "Max payload size in bytes"));
		form.addField(Field.fieldListSingle("pubsub#send_last_published_item", this.send_last_published_item.name(), "When to send the last published item", SendLastPublishedItem.descriptions(), asStrinTable(SendLastPublishedItem.values())));

		if (this.children != null) {
			Element f = new Element("field", new String[]{"var"}, new String[]{"pubsub#children"});
			for (String x : this.children) {
				f.addChild(new Element("value", x));
			}
			Field field = new Field(f);
			//Field field = Field.fieldListSingle("pubsub#children", null, "The child nodes (leaf or collection) associated with a collection", null, this.children.toArray(new String[] {}));
			form.addField(field);
		}

		return form.getElement();
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

	public Set<String> getRoster_groups_allowed() {
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
		setValue("title", repo.getData(pubSubConfig.getServiceName(), subnode, "title"));
		setValue("collection", repo.getData(pubSubConfig.getServiceName(), subnode, "collection"));
		setValue("deliver_notifications", repo.getData(pubSubConfig.getServiceName(), subnode, "deliver_notifications"));
		setValue("deliver_payloads", repo.getData(pubSubConfig.getServiceName(), subnode, "deliver_payloads"));
		setValue("notify_config", repo.getData(pubSubConfig.getServiceName(), subnode, "notify_config"));
		setValue("notify_delete", repo.getData(pubSubConfig.getServiceName(), subnode, "notify_delete"));
		setValue("notify_retract", repo.getData(pubSubConfig.getServiceName(), subnode, "notify_retract"));
		setValue("persist_items", repo.getData(pubSubConfig.getServiceName(), subnode, "persist_items"));
		setValue("max_items", repo.getData(pubSubConfig.getServiceName(), subnode, "max_items"));
		setValue("subscribe", repo.getData(pubSubConfig.getServiceName(), subnode, "subscribe"));
		setValue("access_model", repo.getData(pubSubConfig.getServiceName(), subnode, "access_model"));
		setValue("roster_groups_allowed", repo.getDataList(pubSubConfig.getServiceName(), subnode, "roster_groups_allowed"));
		setValue("publish_model", repo.getData(pubSubConfig.getServiceName(), subnode, "publish_model"));
		setValue("send_last_published_item", repo.getData(pubSubConfig.getServiceName(), subnode, "send_last_published_item"));
		setValue("max_payload_size", repo.getData(pubSubConfig.getServiceName(), subnode, "max_payload_size"));
		setValue("presence_based_delivery", repo.getData(pubSubConfig.getServiceName(), subnode, "presence_based_delivery"));
	}

	public void setAccess_model(AccessModel access_model) {
		this.access_model = access_model;
	}

	public void setCollection(String collection) {
		this.collection = collection;
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

	public void setValue(final String ekey, final Object value) {
		if (value == null)
			return;
		String key = ekey;
		int p;
		if ((p = key.indexOf('#')) != -1) {
			key = ekey.substring(p + 1);
		}
		if ("collection".equals(key)) {
			this.collection = (String) value;
		} else if ("title".equals(key)) {
			this.title = (String) value;
		} else if ("deliver_notifications".equals(key)) {
			this.deliver_notifications = asBoolean((String) value, DEFAULT_DELIVER_NOTIFICATIONS);
		} else if ("deliver_payloads".equals(key)) {
			this.deliver_payloads = asBoolean((String) value, DEFAULT_DELIVER_PAYLOADS);
		} else if ("notify_config".equals(key)) {
			this.notify_config = asBoolean((String) value, DEFAULT_NOTIFY_CONFIG);
		} else if ("notify_delete".equals(key)) {
			this.notify_delete = asBoolean((String) value, DEFAULT_NOTIFY_DELETE);
		} else if ("notify_retract".equals(key)) {
			this.notify_retract = asBoolean((String) value, DEFAULT_NOTIFY_RETRACT);
		} else if ("persist_items".equals(key)) {
			this.persist_items = asBoolean((String) value, DEFAULT_PERSIST_ITEMS);
		} else if ("max_items".equals(key)) {
			this.max_items = asInteger((String) value, DEFAULT_MAX_ITEMS);
		} else if ("subscribe".equals(key)) {
			this.subscribe = asBoolean((String) value, DEFAULT_SUBSCRIBE);
		} else if ("access_model".equals(key)) {
			this.access_model = AccessModel.valueOf(asString((String) value, DEFAULT_ACCESS_MODEL));
		} else if ("roster_groups_allowed".equals(key)) {
			String[] tmp = (String[]) value;
			this.roster_groups_allowed.clear();
			if (tmp != null)
				for (String string : tmp) {
					this.roster_groups_allowed.add(string);
				}
		} else if ("publish_model".equals(key)) {
			this.publish_model = PublisherModel.valueOf(asString((String) value, DEFAULT_PUBLISH_MODEL));
		} else if ("send_last_published_item".equals(key)) {
			SendLastPublishedItem.valueOf(asString((String) value, DEFAULT_SEND_LAST_PUBLISHED_ITEM));
		} else if ("max_payload_size".equals(key)) {
			this.max_payload_size = asInteger((String) value, DEFAULT_MAX_PAYLOAD_SIZE);
		} else if ("presence_based_delivery".equals(key)) {
			this.presence_based_delivery = asBoolean((String) value, DEFAULT_PRESENCE_BASED_DELIVERY);
		}
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
		repo.setData(pubSubConfig.getServiceName(), subnode, "collection", this.collection);
	}
}