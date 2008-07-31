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
import tigase.form.Field.FieldType;
import tigase.xml.Element;

public class AbstractNodeConfig {

	public static final String PUBSUB = "pubsub#";

	/**
	 * List with do-not-write elements
	 */
	protected final Set<String> blacklist = new HashSet<String>();

	protected final Form form = new Form("form", null, null);

	public AbstractNodeConfig() {
		init();
	}

	public AbstractNodeConfig(final AbstractNodeConfig config) {
		init();
		copyFrom(config);
	}

	public void add(Field f) {
		form.addField(f);
	}

	protected String[] asStrinTable(Enum<?>[] values) {
		String[] result = new String[values.length];
		int i = 0;
		for (Enum<?> v : values) {
			result[i++] = v.name();
		}
		return result;
	}

	public void copyFrom(AbstractNodeConfig c) {
		form.copyValuesFrom(c.form);
	}

	public String getBodyXslt() {
		return form.getAsString("pubsub#body_xslt");
	}

	public String getBodyXsltEmbedded() {
		String[] r = form.getAsStrings("pubsub#embedded_body_xslt");
		if (r == null)
			return null;
		StringBuilder sb = new StringBuilder();
		for (String string : r) {
			sb.append(string);
		}
		return sb.toString();
	}

	public String[] getChildren() {
		return form.getAsStrings("pubsub#children");
	}

	public String getCollection() {
		return form.getAsString("pubsub#collection");
	}

	public String[] getDomains() {
		String[] v = form.getAsStrings(PUBSUB + "domains");
		return v == null ? new String[] {} : v;
	}

	public Element getFormElement() {
		return form.getElement();
	}

	public AccessModel getNodeAccessModel() {
		String tmp = form.getAsString("pubsub#access_model");
		if (tmp == null) {
			return null;
		} else {
			return AccessModel.valueOf(tmp);
		}
	}

	public NodeType getNodeType() {
		String tmp = form.getAsString("pubsub#node_type");
		if (tmp == null) {
			return null;
		} else {
			return NodeType.valueOf(tmp);
		}
	}

	public String[] getRosterGroupsAllowed() {
		return form.getAsStrings("pubsub#roster_groups_allowed");
	}

	public String getTitle() {
		return form.getAsString("pubsub#title");
	}

	protected void init() {
		blacklist.add("pubsub#children");
		blacklist.add("pubsub#node_type");

		form.addField(Field.fieldListSingle(PUBSUB + "node_type", null, null, null, new String[] { NodeType.leaf.name(),
				NodeType.collection.name() }));
		form.addField(Field.fieldTextSingle(PUBSUB + "title", "", "A friendly name for the node"));
		form.addField(Field.fieldBoolean(PUBSUB + "deliver_payloads", true, "Whether to deliver payloads with event notifications"));
		form.addField(Field.fieldBoolean(PUBSUB + "notify_config", false, "Notify subscribers when the node configuration changes"));
		form.addField(Field.fieldBoolean(PUBSUB + "notify_delete", false, "Notify subscribers when the node is deleted"));
		form.addField(Field.fieldBoolean(PUBSUB + "notify_retract", false,
				"Notify subscribers when items are removed from the node"));
		form.addField(Field.fieldBoolean(PUBSUB + "persist_items", true, "Persist items to storage"));
		form.addField(Field.fieldTextSingle(PUBSUB + "max_items", "10", "Max # of items to persist"));
		form.addField(Field.fieldBoolean(PUBSUB + "subscribe", true, "Whether to allow subscriptions"));
		form.addField(Field.fieldTextSingle(PUBSUB + "collection", "", "The collection with which a node is affiliated"));
		form.addField(Field.fieldListSingle(PUBSUB + "access_model", AccessModel.open.name(), "Specify the subscriber model", null,
				asStrinTable(AccessModel.values())));
		form.addField(Field.fieldListSingle(PUBSUB + "publish_model", PublisherModel.publishers.name(),
				"Specify the publisher model", null, asStrinTable(PublisherModel.values())));
		form.addField(Field.fieldListSingle(PUBSUB + "send_last_published_item", SendLastPublishedItem.on_sub.name(),
				"When to send the last published item", null, asStrinTable(PublisherModel.values())));

		form.addField(Field.fieldTextMulti(PUBSUB + "domains", new String[] {},
				"The domains allowed to access this node (blank for any)"));

		form.addField(Field.fieldTextMulti(PUBSUB + "embedded_body_xslt", new String[] {},
				"The XSL transformation which can be applied to payloads in order to generate an appropriate message body element."));

		form.addField(Field.fieldTextSingle(PUBSUB + "body_xslt", "",
				"The URL of an XSL transformation which can be applied to payloads in order to generate an appropriate message body element."));
		form.addField(Field.fieldTextMulti(PUBSUB + "roster_groups_allowed", new String[] {}, "Roster groups allowed to subscribe"));

	}

	public boolean isCollectionSet() {
		return form.get(PUBSUB + "collection") != null;
	}

	public boolean isDeliver_payloads() {
		return form.getAsBoolean("pubsub#deliver_payloads");
	}

	public boolean isNotify_config() {
		return form.getAsBoolean("pubsub#notify_config");
	}

	public void read(final UserRepository repository, final PubSubConfig config, final String subnode)
			throws UserNotFoundException, TigaseDBException {
		String[] keys = repository.getKeys(config.getServiceName(), subnode);
		if (keys != null)
			for (String key : keys) {
				String[] values = repository.getDataList(config.getServiceName(), subnode, key);
				setValues(key, values);
			}
	}

	public void reset() {
		form.clear();
		init();
	}

	public void setBodyXsltEmbedded(String xslt) {
		setValue("pubsub#embedded_body_xslt", xslt);
	}

	public void setCollection(String collectionNew) {
		setValue("pubsub#collection", collectionNew);
	}

	public void setDomains(String... domains) {
		setValues(PUBSUB + "domains", domains);
	}

	public void setValue(String var, boolean data) {
		setValue(var, new Boolean(data));
	}

	public void setValue(String var, Object data) {
		Field f = form.get(var);

		if (f == null) {
			return;
		} else if (data == null) {
			f.setValues(new String[] {});
		} else if (data instanceof String) {
			String str = (String) data;
			if (f.getType() == FieldType.bool && !"0".equals(str) && !"1".equals(str))
				throw new RuntimeException("Boolean fields allows only '1' or '0' values");
			f.setValues(new String[] { str });
		} else if (data instanceof Boolean && f.getType() == FieldType.bool) {
			boolean b = ((Boolean) data).booleanValue();
			f.setValues(new String[] { b ? "1" : "0" });
		} else if (data instanceof String[] && (f.getType() == FieldType.list_multi || f.getType() == FieldType.text_multi)) {
			String[] d = (String[]) data;
			f.setValues(d);
		} else {
			throw new RuntimeException("Cannot match type " + data.getClass().getCanonicalName() + " to field type "
					+ f.getType().name());
		}

	}

	private void setValues(String var, String[] data) {
		if (data == null || data.length > 1) {
			setValue(var, data);
		} else if (data.length == 0) {
			setValue(var, null);
		} else {
			setValue(var, data[0]);
		}
	}

	public void write(final UserRepository repo, final PubSubConfig config, final String subnode) throws UserNotFoundException,
			TigaseDBException {
		List<Field> fields = form.getAllFields();
		for (Field field : fields) {
			if (field.getVar() != null && !this.blacklist.contains(field.getVar())) {
				String[] values = field.getValues();
				String value = field.getValue();
				if (values == null || values.length == 0) {
					repo.removeData(config.getServiceName(), subnode, field.getVar());
				} else if (values.length == 1) {
					repo.setData(config.getServiceName(), subnode, field.getVar(), value);
				} else {
					repo.setDataList(config.getServiceName(), subnode, field.getVar(), values);
				}
			}
		}
	}

}