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

public enum SendLastPublishedItem {
	/** Never */
	never("Never"),
	/** When a new subscription is processed */
	on_sub("When a new subscription is processed"),
	/**
	 * When a new subscription is processed and whenever a subscriber comes
	 * online
	 */
	on_sub_and_presence("When a new subscription is processed and whenever a subscriber comes online")
	;

	public static String[] descriptions() {
		String[] result = new String[values().length];
		int i = 0;
		for (SendLastPublishedItem item : values()) {
			result[i++] = item.description;
		}
		return result;
	}

	private final String description;

	private SendLastPublishedItem(String description) {
		this.description = description;
	}
}
