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

package tigase.pubsub.repository.stateless;

//~--- non-JDK imports --------------------------------------------------------

import tigase.pubsub.Subscription;
import tigase.xmpp.BareJID;

//~--- classes ----------------------------------------------------------------

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:23:47 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class UsersSubscription implements Cloneable {
	private final BareJID jid;
	private final String subid;
	private Subscription subscription;

	// ~--- constructors
	// ---------------------------------------------------------

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param jid
	 * @param subid
	 * @param subscriptionType
	 */
	public UsersSubscription(BareJID jid, String subid, Subscription subscriptionType) {
		super();
		this.jid = jid;
		this.subid = subid;
		this.subscription = subscriptionType;
	}

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param jid
	 * @param subid
	 * @param subscriptionType
	 */
	public UsersSubscription(String jid, String subid, Subscription subscriptionType) {
		super();
		this.jid = BareJID.bareJIDInstanceNS(jid);
		this.subid = subid;
		this.subscription = subscriptionType;
	}

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 * 
	 * @throws CloneNotSupportedException
	 */
	@Override
	public UsersSubscription clone() throws CloneNotSupportedException {
		UsersSubscription a = new UsersSubscription(jid, subid, subscription);

		return a;
	}

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public BareJID getJid() {
		return jid;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public String getSubid() {
		return subid;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public Subscription getSubscription() {
		return subscription;
	}

	// ~--- set methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param subscriptionType
	 */
	public void setSubscription(Subscription subscriptionType) {
		this.subscription = subscriptionType;
	}
}

// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com
