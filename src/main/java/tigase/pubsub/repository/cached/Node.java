/*
 * Node.java
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
package tigase.pubsub.repository.cached;

import tigase.pubsub.AbstractNodeConfig;

import tigase.pubsub.repository.INodeMeta;
import tigase.xmpp.BareJID;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Node<T> implements INodeMeta<T> {

	private static final Logger log = Logger.getLogger(Node.class.getName());

	//private boolean affNeedsWriting = false;
	private boolean conNeedsWriting = false;
	private final Date creationTime;
	private final BareJID creator;

	private boolean deleted = false;
	private String name;
	private T nodeId;

	// private Long nodeAffiliationsChangeTimestamp;

	private NodeAffiliations nodeAffiliations;
	private AbstractNodeConfig nodeConfig;
	private NodeSubscriptions nodeSubscriptions;

	// private Long nodeConfigChangeTimestamp;

	private BareJID serviceJid;
	//private boolean subNeedsWriting = false;

	// private Long nodeSubscriptionsChangeTimestamp;

	public Node(T nodeId, BareJID serviceJid, AbstractNodeConfig nodeConfig, NodeAffiliations nodeAffiliations,
			NodeSubscriptions nodeSubscriptions, BareJID creator, Date creationTime) {
		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST,
							 "Constructing Node, serviceJid: {0}, nodeConfig: {1}, nodeId: {2}, nodeAffiliations: {3}, nodeSubscriptions: {4}",
							 new Object[] { serviceJid, nodeConfig, nodeId, nodeAffiliations, nodeSubscriptions } );
		}

		this.nodeId = nodeId;
		this.serviceJid = serviceJid;
		this.nodeConfig = nodeConfig;
		this.nodeAffiliations = nodeAffiliations;
		this.nodeSubscriptions = nodeSubscriptions;
		this.name = nodeConfig.getNodeName();
		this.creator = creator;
		this.creationTime = creationTime;
	}
	
	public T getNodeId() {
		return nodeId;
	}

	public void affiliationsMerge() {
		nodeAffiliations.merge();
	}

	public boolean affiliationsNeedsWriting() {
		return nodeAffiliations.isChanged();
	}

	public void affiliationsSaved() {
	//	affNeedsWriting = false;
		affiliationsMerge();
	}

	public void configCopyFrom(AbstractNodeConfig nodeConfig) {
		synchronized (this) {
			this.nodeConfig.copyFrom(nodeConfig);
			conNeedsWriting = true;
		}
	}

	public boolean configNeedsWriting() {
		return conNeedsWriting;
	}

	public void configSaved() {
		conNeedsWriting = false;
	}

	// public Long getNodeAffiliationsChangeTimestamp() {
	// return nodeAffiliationsChangeTimestamp;
	// }

	public Date getCreationTime() {
		return creationTime;
	}

	public BareJID getCreator() {
		return creator;
	}

	public String getName() {
		return name;
	}

	public NodeAffiliations getNodeAffiliations() {
		return nodeAffiliations;
	}

	public AbstractNodeConfig getNodeConfig() {
		return nodeConfig;
	}

	// public Long getNodeConfigChangeTimestamp() {
	// return nodeConfigChangeTimestamp;
	// }

	public NodeSubscriptions getNodeSubscriptions() {
		return nodeSubscriptions;
	}

	public BareJID getServiceJid() {
		return serviceJid;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public boolean needsWriting() {
		return affiliationsNeedsWriting() || subscriptionsNeedsWriting() || conNeedsWriting;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	// public Long getNodeSubscriptionsChangeTimestamp() {
	// return nodeSubscriptionsChangeTimestamp;
	// }

	public void subscriptionsMerge() {
		nodeSubscriptions.merge();
	}

	// public void resetNodeAffiliationsChangeTimestamp() {
	// this.nodeAffiliationsChangeTimestamp = null;
	// }
	//
	// public void resetNodeConfigChangeTimestamp() {
	// this.nodeConfigChangeTimestamp = null;
	// }
	//
	// public void resetNodeSubscriptionsChangeTimestamp() {
	// this.nodeSubscriptionsChangeTimestamp = null;
	// }

	public boolean subscriptionsNeedsWriting() {
		return nodeSubscriptions.isChanged();
	}

	public void subscriptionsSaved() {
		//subNeedsWriting = false;
		this.subscriptionsMerge();
	}

	// public void setNodeAffiliationsChangeTimestamp() {
	// if (nodeAffiliationsChangeTimestamp == null)
	// nodeAffiliationsChangeTimestamp = System.currentTimeMillis();
	// }
	//
	// public void setNodeConfigChangeTimestamp() {
	// if (nodeConfigChangeTimestamp == null)
	// nodeConfigChangeTimestamp = System.currentTimeMillis();
	// }
	//
	// public void setNodeSubscriptionsChangeTimestamp() {
	// if (nodeSubscriptionsChangeTimestamp == null)
	// nodeSubscriptionsChangeTimestamp = System.currentTimeMillis();
	// }

	public void resetChanges() {
		nodeAffiliations.resetChangedFlag();
		nodeSubscriptions.resetChangedFlag();
	}

	@Override
	public String toString() {
		return "Node{" + "creationTime=" + creationTime + ", deleted=" + deleted + ", name=" + name + ", nodeId=" + nodeId
					 + ", nodeAffiliations=" + nodeAffiliations + ", nodeSubscriptions=" + nodeSubscriptions
					 + ", serviceJid=" + serviceJid
					 + ", creator=" + creator +
					 '}';
	}
}
