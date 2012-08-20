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
package tigase.pubsub.modules;

import tigase.pubsub.AbstractModule;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IPubSubRepository;

public abstract class AbstractConfigCreateNode extends AbstractModule {

	protected final LeafNodeConfig defaultNodeConfig;

	public AbstractConfigCreateNode(final PubSubConfig config, final IPubSubRepository pubsubRepository,
			final LeafNodeConfig defaultNodeConfig) {
		super(config, pubsubRepository);
		this.defaultNodeConfig = defaultNodeConfig;
	}

}
