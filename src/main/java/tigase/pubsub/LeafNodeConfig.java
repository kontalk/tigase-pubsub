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

public class LeafNodeConfig extends AbstractNodeConfig {

	public LeafNodeConfig(final String nodeName) {
		super(nodeName);
	}

	public LeafNodeConfig(final String nodeName, AbstractNodeConfig config) {
		super(nodeName, config);
	}

	@Override
	protected AbstractNodeConfig getInstance(String nodeName) {
		return new LeafNodeConfig(nodeName);
	}

	public Integer getMaxItems() {
		Integer x = form.getAsInteger("pubsub#max_items");
		return x;
	}

	@Override
	protected void init() {
		super.init();
	}

	public boolean isPersistItem() {
		Boolean x = form.getAsBoolean("pubsub#persist_items");
		return x == null ? false : x;
	}

}
