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
import tigase.pubsub.repository.IPubSubRepository;
import tigase.sys.TigaseRuntime;
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

	private static final String PUBSUB_HIGH_MEMORY_USAGE_LEVEL_KEY = "pubsub-high-memory-usage-level";
	private static final String PUBSUB_LOW_MEMORY_DELAY_KEY = "pubsub-low-memory-delay";
	private static final String PUBSUB_PERSISTENT_PEP_KEY = "persistent-pep";
	private static final String PUBSUB_SEND_LAST_PUBLISHED_ITEM_ON_PRESECE_KEY = "send-last-published-item-on-presence";
	
	private static final int DEF_PUBSUB_HIGH_MEMORY_USAGE_LEVEL_VAL = 90;
	private static final long DEF_PUBSUB_LOW_MEMORY_DELAY_VAL = 1000;
	
	protected String[] admins;

	protected IPubSubRepository pubSubRepository;

	protected BareJID serviceBareJID = BareJID.bareJIDInstanceNS("tigase-pubsub");

	private long lowMemoryDelay = DEF_PUBSUB_LOW_MEMORY_DELAY_VAL;
	private float highMemoryUsageLevel = DEF_PUBSUB_HIGH_MEMORY_USAGE_LEVEL_VAL;
	private boolean persistentPep = false;
	private boolean sendLastPublishedItemOnPresence = false;
	
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
		props.put(PUBSUB_HIGH_MEMORY_USAGE_LEVEL_KEY, DEF_PUBSUB_HIGH_MEMORY_USAGE_LEVEL_VAL);
		props.put(PUBSUB_LOW_MEMORY_DELAY_KEY, DEF_PUBSUB_LOW_MEMORY_DELAY_VAL);
		return props;
	}

	public long getDelayOnLowMemory() {
		if (isHighMemoryUsage()) {
			return lowMemoryDelay;
		}
		
		return 0;
	}
	
	public IPubSubRepository getPubSubRepository() {
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

	public JID getComponentJID() {
		return this.component.getComponentId();
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
		if (props.containsKey(PUBSUB_LOW_MEMORY_DELAY_KEY)) {
			this.lowMemoryDelay = (Long) props.get(PUBSUB_LOW_MEMORY_DELAY_KEY);
		}
		if (props.containsKey(PUBSUB_HIGH_MEMORY_USAGE_LEVEL_KEY)) {
			this.highMemoryUsageLevel = ((Integer) props.get(PUBSUB_HIGH_MEMORY_USAGE_LEVEL_KEY)).floatValue();
		}
		if (props.containsKey(PUBSUB_PERSISTENT_PEP_KEY)) {
			this.persistentPep = (Boolean) props.get(PUBSUB_PERSISTENT_PEP_KEY);
		}
		if (props.containsKey(PUBSUB_SEND_LAST_PUBLISHED_ITEM_ON_PRESECE_KEY)) {
			this.sendLastPublishedItemOnPresence = (Boolean) props.get(PUBSUB_SEND_LAST_PUBLISHED_ITEM_ON_PRESECE_KEY);
		}
	}

	void setPubSubRepository(IPubSubRepository pubSubRepository) {
		this.pubSubRepository = pubSubRepository;
	}
	
	private boolean isHighMemoryUsage() {
		return TigaseRuntime.getTigaseRuntime().getHeapMemUsage() > highMemoryUsageLevel;
	}	
	
	public boolean isPepPeristent() {
		return persistentPep;
	}
	
	public boolean isSendLastPublishedItemOnPresence() {
		return sendLastPublishedItemOnPresence;
	}
}
