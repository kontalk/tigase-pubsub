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

//~--- non-JDK imports --------------------------------------------------------

import tigase.xmpp.BareJID;

//~--- classes ----------------------------------------------------------------

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:10:54 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PubSubConfig {
	private String[] admins;
	private BareJID serviceBareJID;
	private String serviceName;

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public String[] getAdmins() {
		return admins;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public BareJID getServiceBareJID() {
		return serviceBareJID;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public String getServiceName() {
		return serviceName;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * 
	 * @return
	 */
	public boolean isAdmin(final String jid) {
		if ((jid == null) || (this.admins == null)) {
			return false;
		}

		for (String adj : this.admins) {
			if (jid.equals(adj)) {
				return true;
			}
		}

		return false;
	}

	// ~--- set methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param strings
	 */
	public void setAdmins(String[] strings) {
		this.admins = strings;
	}

	void setServiceName(String serviceName) {
		this.serviceName = serviceName;
		serviceBareJID = BareJID.bareJIDInstanceNS(serviceName);
	}
}

// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com
