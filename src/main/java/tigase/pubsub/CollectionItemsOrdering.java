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

import tigase.pubsub.repository.IItems;

import java.util.Comparator;

public enum CollectionItemsOrdering {

	byCreationDate( "Sort items by creation time", new Comparator<IItems.ItemMeta>() {

		@Override
		public int compare( IItems.ItemMeta o1, IItems.ItemMeta o2 ) {
			return o1.getCreationDate().compareTo( o2.getCreationDate() ) * ( -1 );
		}
	} ),
	
	byUpdateDate( "Sort items by last update time", new Comparator<IItems.ItemMeta>() {

		@Override
		public int compare( IItems.ItemMeta o1, IItems.ItemMeta o2 ) {
			return o1.getItemUpdateDate().compareTo( o2.getItemUpdateDate() ) * ( -1 );
		}
	} ),;

	public static String[] descriptions() {
		String[] result = new String[ values().length ];
		int i = 0;
		for ( CollectionItemsOrdering item : values() ) {
			result[i++] = item.description;
		}
		return result;
	}

	public Comparator<IItems.ItemMeta> getComparator() {
		return comparator;
	}

	private final String description;
	private final Comparator<IItems.ItemMeta> comparator;

	private CollectionItemsOrdering( String description, Comparator<IItems.ItemMeta> cmp ) {
		this.description = description;
		this.comparator = cmp;
	}

}
