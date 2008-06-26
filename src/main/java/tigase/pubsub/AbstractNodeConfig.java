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

	public static void main(String[] args) {
		AbstractNodeConfig c = new AbstractNodeConfig();

		c.setValue("pubsub#title", "xxxx");
		System.out.println(c.getFormElement());

		LeafNodeConfig l = new LeafNodeConfig();
		l.copyFrom(c);
		System.out.println(l.getFormElement());
	}

	/**
	 * List with do-not-write elements
	 */
	protected final Set<String> blacklist = new HashSet<String>();

	protected final Form form = new Form("form", null, null);

	public AbstractNodeConfig() {
		init();
		blacklist.add("pubsub#children");
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

	public String[] getChildren() {
		return form.getAsStrings("pubsub#children");
	}

	public String getCollection() {
		return form.getAsString("pubsub#collection");
	}

	public Element getFormElement() {
		return form.getElement();
	}

	protected void init() {
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
				String value = repository.getData(config.getServiceName(), subnode, key);
				setValue(key, value);
			}
	}

	public void reset() {
		form.clear();
		init();
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
		} else if (data instanceof String[] && f.getType() == FieldType.list_multi) {
			String[] d = (String[]) data;
			f.setValues(d);
		} else {
			throw new RuntimeException("Cannot match type " + data.getClass().getCanonicalName() + " to field type "
					+ f.getType().name());
		}

	}

	public void write(final UserRepository repo, final PubSubConfig config, final String subnode) throws UserNotFoundException,
			TigaseDBException {
		List<Field> fields = form.getAllFields();
		for (Field field : fields) {
			if (field.getVar() != null && !this.blacklist.contains(field.getVar())) {
				String value = field.getValue();
				if (value == null) {
					repo.removeData(config.getServiceName(), subnode, field.getVar());
				} else {
					repo.setData(config.getServiceName(), subnode, field.getVar(), value);
				}
			}
		}
	}

}