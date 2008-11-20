package tigase.pubsub.repository.cached;

import tigase.pubsub.AbstractNodeConfig;

class Node {

	private boolean deleted = false;

	private String name;

	private NodeAffiliations nodeAffiliations;

	private Long nodeAffiliationsChangeTimestamp;

	private AbstractNodeConfig nodeConfig;

	private Long nodeConfigChangeTimestamp;

	private NodeSubscriptions nodeSubscriptions;

	private Long nodeSubscriptionsChangeTimestamp;

	Node(AbstractNodeConfig nodeConfig, NodeAffiliations nodeAffiliations, NodeSubscriptions nodeSubscriptions) {
		this.nodeConfig = nodeConfig;
		this.nodeAffiliations = nodeAffiliations;
		this.nodeSubscriptions = nodeSubscriptions;
		this.name = nodeConfig.getNodeName();
	}

	public String getName() {
		return name;
	}

	public NodeAffiliations getNodeAffiliations() {
		return nodeAffiliations;
	}

	public Long getNodeAffiliationsChangeTimestamp() {
		return nodeAffiliationsChangeTimestamp;
	}

	public AbstractNodeConfig getNodeConfig() {
		return nodeConfig;
	}

	public Long getNodeConfigChangeTimestamp() {
		return nodeConfigChangeTimestamp;
	}

	public NodeSubscriptions getNodeSubscriptions() {
		return nodeSubscriptions;
	}

	public Long getNodeSubscriptionsChangeTimestamp() {
		return nodeSubscriptionsChangeTimestamp;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void resetNodeAffiliationsChangeTimestamp() {
		this.nodeAffiliationsChangeTimestamp = null;
	}

	public void resetNodeConfigChangeTimestamp() {
		this.nodeConfigChangeTimestamp = null;
	}

	public void resetNodeSubscriptionsChangeTimestamp() {
		this.nodeSubscriptionsChangeTimestamp = null;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public void setNodeAffiliationsChangeTimestamp() {
		if (nodeAffiliationsChangeTimestamp == null)
			nodeAffiliationsChangeTimestamp = System.currentTimeMillis();
	}

	public void setNodeConfigChangeTimestamp() {
		if (nodeConfigChangeTimestamp == null)
			nodeConfigChangeTimestamp = System.currentTimeMillis();
	}

	public void setNodeSubscriptionsChangeTimestamp() {
		if (nodeSubscriptionsChangeTimestamp == null)
			nodeSubscriptionsChangeTimestamp = System.currentTimeMillis();
	}

}
