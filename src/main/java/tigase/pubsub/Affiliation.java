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

public enum Affiliation {
	/** */
	member(true, true, false, false, false, false, false),
	/** */
	none(true, false, false, false, false, false, false),
	/** An entity that is disallowed from subscribing or publishing to a node. */
	outcast(false, false, false, false, false, false, false),
	/**
	 * The manager of a node, of which there may be more than one; often but not
	 * necessarily the node creator.
	 */
	owner(true, true, true, true, true, true, true),
	/** An entity that is allowed to publish items to a node. */
	publisher(true, true, true, true, false, false, false);

	private final boolean configureNode;

	private final boolean deleteItem;

	private final boolean deleteNode;

	private final boolean publishItem;

	private final boolean purgeNode;

	private final boolean retrieveItem;

	private final boolean subscribe;

	private Affiliation(boolean subscribe, boolean retrieveItem, boolean publishItem, boolean deleteItem, boolean configureNode,
			boolean deleteNode, boolean purgeNode) {
		this.subscribe = subscribe;
		this.retrieveItem = retrieveItem;
		this.publishItem = publishItem;
		this.deleteItem = deleteItem;
		this.configureNode = configureNode;
		this.deleteNode = deleteNode;
		this.purgeNode = purgeNode;
	}

	public boolean isConfigureNode() {
		return configureNode;
	}

	public boolean isDeleteItem() {
		return deleteItem;
	}

	public boolean isDeleteNode() {
		return deleteNode;
	}

	public boolean isPublishItem() {
		return publishItem;
	}

	public boolean isPurgeNode() {
		return purgeNode;
	}

	public boolean isRetrieveItem() {
		return retrieveItem;
	}

	public boolean isSubscribe() {
		return subscribe;
	}
}
