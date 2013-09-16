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

import java.util.HashMap;
import java.util.Map;

import tigase.component2.AbstractComponent;
import tigase.component2.ComponentConfig;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:10:54 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PubSubConfig extends ComponentConfig {

	private String[] admins;

	private CachedPubSubRepository pubSubRepository;

	private BareJID serviceBareJID = BareJID.bareJIDInstanceNS("tigase-pubsub");

	public PubSubConfig(AbstractComponent<?> component) {
		super(component);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public String[] getAdmins() {
		return admins;
	}

	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		final HashMap<String, Object> props = new HashMap<String, Object>();
		return props;
	}

	public CachedPubSubRepository getPubSubRepository() {
		return pubSubRepository;
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
	 * @param jid
	 * 
	 * @return
	 */
	public boolean isAdmin(final BareJID jid) {
		if ((jid == null) || (this.admins == null)) {
			return false;
		}

		for (String adj : this.admins) {
			if (jid.toString().equals(adj)) {
				return true;
			}
		}

		return false;
	}

	public boolean isAdmin(final JID jid) {
		return isAdmin(jid.getBareJID());
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param strings
	 */
	public void setAdmins(String[] strings) {
		this.admins = strings;
	}

	@Override
	public void setProperties(Map<String, Object> props) {
		// TODO Auto-generated method stub

	}

	void setPubSubRepository(CachedPubSubRepository pubSubRepository) {
		this.pubSubRepository = pubSubRepository;
	}

}
