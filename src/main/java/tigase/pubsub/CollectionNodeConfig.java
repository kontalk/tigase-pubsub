/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import tigase.form.Field;

public class CollectionNodeConfig extends AbstractNodeConfig {

	public CollectionNodeConfig(String nodeName) {
		super(nodeName);
	}

	public void addChildren(String... children) {
		Set<String> list = new HashSet<String>();
		String[] cur = getChildren();
		if (cur != null)
			list.addAll(Arrays.asList(cur));
		for (String kid : children) {
			list.add(kid);
		}
		setChildren(list.toArray(new String[] {}));
	}

	@Override
	protected AbstractNodeConfig getInstance(String nodeName) {
		return new CollectionNodeConfig(nodeName);
	}

	@Override
	protected void init() {
		super.init();
		Field f = Field.fieldTextMulti("pubsub#children", "", null);
		add(f);
	}

	public void removeChildren(String nodeName) {
		Set<String> list = new HashSet<String>();
		String[] cur = getChildren();
		if (cur != null)
			list.addAll(Arrays.asList(cur));
		list.remove(nodeName);
		setChildren(list.toArray(new String[] {}));
	}

	public void setChildren(String[] children) {
		setValue("pubsub#children", children);
	}

}
