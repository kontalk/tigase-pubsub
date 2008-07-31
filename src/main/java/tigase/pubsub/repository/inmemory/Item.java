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

import tigase.xml.Element;

public class Item {

	private final Date creationDate;

	private Element data;

	private final String id;

	private final String publisher;

	private Date updateDate;

	Item(String id, Element data, Date creationDate, Date updateDate, String publisher) {
		super();
		this.data = data;
		this.creationDate = creationDate;
		this.updateDate = updateDate;
		this.publisher = publisher;
		this.id = id;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public Element getData() {
		return data;
	}

	public String getId() {
		return id;
	}

	public String getPublisher() {
		return publisher;
	}

	public Date getUpdateDate() {
		return updateDate;
	}

	public void setData(Element data) {
		this.data = data;
	}

	public void setUpdateDate(Date updateDate) {
		this.updateDate = updateDate;
	}

}
