/*
 * NodeMeta.java
 *
 * Tigase PubSub Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
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
 */
package tigase.pubsub.repository.stateless;

import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.repository.INodeMeta;
import tigase.xmpp.BareJID;

import java.util.Date;

/**
 * Class implements INodeMeta interfaces and holds PubSub node metadata
 */
public class NodeMeta<T> implements INodeMeta<T> {

	private final T id;
	private final Date creationTime;
	private final BareJID creator;
	private final AbstractNodeConfig config;

	public NodeMeta(T id, AbstractNodeConfig config, BareJID creator, Date creationTime) {
		this.id = id;
		this.creationTime = creationTime;
		this.creator = creator;
		this.config = config;
	}

	public AbstractNodeConfig getNodeConfig() {
		return config;
	}

	@Override
	public T getNodeId() {
		return id;
	}

	@Override
	public Date getCreationTime() {
		return creationTime;
	}

	@Override
	public BareJID getCreator() {
		return creator;
	}
}
