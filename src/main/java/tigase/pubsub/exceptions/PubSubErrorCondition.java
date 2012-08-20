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
package tigase.pubsub.exceptions;

import tigase.xml.Element;

public class PubSubErrorCondition {

	public static final PubSubErrorCondition CLOSED_NODE = new PubSubErrorCondition("closed-node");

	public static final PubSubErrorCondition INVALID_JID = new PubSubErrorCondition("invalid-jid");

	public static final PubSubErrorCondition INVALID_SUBID = new PubSubErrorCondition("invalid-subid");

	public static final PubSubErrorCondition ITEM_REQUIRED = new PubSubErrorCondition("item-required");

	public static final PubSubErrorCondition NODE_REQUIRED = new PubSubErrorCondition("node-required");

	public static final PubSubErrorCondition NODEID_REQUIRED = new PubSubErrorCondition("nodeid-required");

	public static final PubSubErrorCondition NOT_IN_ROSTER_GROUP = new PubSubErrorCondition("not-in-roster-group");

	public static final PubSubErrorCondition NOT_SUBSCRIBED = new PubSubErrorCondition("not-subscribed");

	public static final PubSubErrorCondition PENDING_SUBSCRIPTION = new PubSubErrorCondition("pending-subscription");

	public static final PubSubErrorCondition PRESENCE_SUBSCRIPTION_REQUIRED = new PubSubErrorCondition(
			"presence-subscription-required");

	protected static final String XMLNS = "http://jabber.org/protocol/pubsub#errors";

	private final String condition;

	private final String feature;

	protected PubSubErrorCondition(String condition) {
		this(condition, null);
	}

	public PubSubErrorCondition(String condition, String feature) {
		this.condition = condition;
		this.feature = feature;
	}

	public Element getElement() {
		Element result = new Element(condition);
		result.addAttribute("xmlns", XMLNS);
		if (this.feature != null) {
			result.addAttribute("feature", feature);
		}
		return result;
	}

}
