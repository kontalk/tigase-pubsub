package tigase.pubsub.repository.cached;

import tigase.pubsub.AbstractNodeConfig;

public class Node {

	private boolean deleted = false;
	private long creationTime = System.currentTimeMillis();
	private String name;

	private NodeAffiliations nodeAffiliations;
	private boolean affNeedsWriting = false;

	//private Long nodeAffiliationsChangeTimestamp;

	private AbstractNodeConfig nodeConfig;
	private boolean conNeedsWriting = false;

	//private Long nodeConfigChangeTimestamp;

	private NodeSubscriptions nodeSubscriptions;
	private boolean subNeedsWriting = false;

	//private Long nodeSubscriptionsChangeTimestamp;

	public Node(AbstractNodeConfig nodeConfig, NodeAffiliations nodeAffiliations, NodeSubscriptions nodeSubscriptions) {
		this.nodeConfig = nodeConfig;
		this.nodeAffiliations = nodeAffiliations;
		this.nodeSubscriptions = nodeSubscriptions;
		this.name = nodeConfig.getNodeName();
	}

	public String getName() {
		return name;
	}

	public long getCreationTime() {
		return creationTime;
	}

	public NodeAffiliations getNodeAffiliations() {
		return nodeAffiliations;
	}

	public void affiliationsMerge() {
		synchronized (this) {
			nodeAffiliations.merge();
			affNeedsWriting = true;
		}
	}

	public boolean affiliationsNeedsWriting() {
		return affNeedsWriting;
	}

	public void affiliationsSaved() {
		affNeedsWriting = false;
	}

//	public Long getNodeAffiliationsChangeTimestamp() {
//		return nodeAffiliationsChangeTimestamp;
//	}

	public AbstractNodeConfig getNodeConfig() {
		return nodeConfig;
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

//	public Long getNodeConfigChangeTimestamp() {
//		return nodeConfigChangeTimestamp;
//	}

	public NodeSubscriptions getNodeSubscriptions() {
		return nodeSubscriptions;
	}

	public void subscriptionsMerge() {
		synchronized (this) {
			nodeSubscriptions.merge();
			subNeedsWriting = true;
		}
	}

	public boolean subscriptionsNeedsWriting() {
		return subNeedsWriting;
	}

	public void subscriptionsSaved() {
		subNeedsWriting = false;
	}

//	public Long getNodeSubscriptionsChangeTimestamp() {
//		return nodeSubscriptionsChangeTimestamp;
//	}

	public boolean isDeleted() {
		return deleted;
	}

//	public void resetNodeAffiliationsChangeTimestamp() {
//		this.nodeAffiliationsChangeTimestamp = null;
//	}
//
//	public void resetNodeConfigChangeTimestamp() {
//		this.nodeConfigChangeTimestamp = null;
//	}
//
//	public void resetNodeSubscriptionsChangeTimestamp() {
//		this.nodeSubscriptionsChangeTimestamp = null;
//	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public boolean needsWriting() {
		return subNeedsWriting || affNeedsWriting || conNeedsWriting;
	}

//	public void setNodeAffiliationsChangeTimestamp() {
//		if (nodeAffiliationsChangeTimestamp == null)
//			nodeAffiliationsChangeTimestamp = System.currentTimeMillis();
//	}
//
//	public void setNodeConfigChangeTimestamp() {
//		if (nodeConfigChangeTimestamp == null)
//			nodeConfigChangeTimestamp = System.currentTimeMillis();
//	}
//
//	public void setNodeSubscriptionsChangeTimestamp() {
//		if (nodeSubscriptionsChangeTimestamp == null)
//			nodeSubscriptionsChangeTimestamp = System.currentTimeMillis();
//	}

}
