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

public enum AccessModel {
	/**
	 * The node owner must approve all subscription requests, and only
	 * subscribers may retrieve items from the node.
	 */
	authorize,
	/**
	 * Any entity may subscribe to the node (i.e., without the necessity for
	 * subscription approval) and any entity may retrieve items from the node
	 * (i.e., without being subscribed); this SHOULD be the default access model
	 * for generic pubsub services.
	 */
	open,
	/**
	 * Any entity with a subscription of type "from" or "both" may subscribe to
	 * the node and retrieve items from the node; this access model applies
	 * mainly to instant messaging systems (see RFC 3921).
	 */
	presence,
	/**
	 * Any entity in the specified roster group(s) may subscribe to the node and
	 * retrieve items from the node; this access model applies mainly to instant
	 * messaging systems (see RFC 3921).
	 */
	roster,
	/**
	 * An entity may subscribe or retrieve items only if on a whitelist managed
	 * by the node owner. The node owner MUST automatically be on the whitelist.
	 * In order to add entities to the whitelist, the node owner SHOULD use the
	 * protocol specified in the Manage Affiliated Entities section of this
	 * document, specifically by setting the affiliation to "member".
	 */
	whitelist,
}
