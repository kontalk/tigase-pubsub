package tigase.pubsub.cluster;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

public class ClusterNodeMap {

	private class NodeInfo {
		private String clusterNodeId;
	}

	public static void main(String[] args) {
		Set<String> cluster = new HashSet<String>();
		cluster.add("cluster_node_1");
		cluster.add("cluster_node_2");
		ClusterNodeMap c = new ClusterNodeMap(cluster);

		c.assign("cluster_node_1", "001");
		c.assign("cluster_node_2", "002");
		c.assign("cluster_node_1", "003");

		System.out.println(c.getClusterNodeId("004"));

	}

	private final Set<String> clusterNodes;

	private final Map<String, NodeInfo> nodesMap = new HashMap<String, NodeInfo>();

	private final Random random = new SecureRandom();

	public ClusterNodeMap(Set<String> cluster_nodes) {
		this.clusterNodes = cluster_nodes;
	}

	public void addPubSubNode(final String nodeName) {
		nodesMap.put(nodeName, new NodeInfo());
	}

	public void addPubSubNode(final String[] nodeNames) {
		if (nodeNames != null)
			for (String string : nodeNames) {
				addPubSubNode(string);
			}
	}

	public void assign(final String clusterNodeId, final String pubSubNodeName) {
		NodeInfo i = this.nodesMap.get(pubSubNodeName);
		if (i == null) {
			i = new NodeInfo();
			this.nodesMap.put(pubSubNodeName, i);
		}
		i.clusterNodeId = clusterNodeId;
	}

	public String getClusterNodeId(final String pubsubNodeName) {
		NodeInfo i = this.nodesMap.get(pubsubNodeName);
		if (i != null)
			return i.clusterNodeId;
		else
			return null;
	}

	public Map<String, Integer> getClusterNodesLoad() {
		final Map<String, Integer> nodeLoad = new HashMap<String, Integer>();
		// init
		for (String n : this.clusterNodes)
			nodeLoad.put(n, new Integer(0));

		// counting
		for (Entry<String, NodeInfo> entry : this.nodesMap.entrySet()) {
			if (entry.getValue().clusterNodeId == null)
				continue;
			Integer a = nodeLoad.get(entry.getValue().clusterNodeId);
			if (a != null) {
				a++;
				nodeLoad.put(entry.getValue().clusterNodeId, a);
			}
		}
		return nodeLoad;
	}

	/**
	 * Stupid name, but important method. This meathod realize Load-Balancing
	 * 
	 * @return Name of (usual) less loaded cluster node
	 */
	public String getNewOwnerOfNode(final String nodeName) {
		// calculating current node load
		final Map<String, Integer> nodeLoad = getClusterNodesLoad();

		Integer minCount = null;
		for (Integer i : nodeLoad.values()) {
			minCount = minCount == null || minCount > i ? i : minCount;
		}

		List<String> shortList = new ArrayList<String>();

		for (Entry<String, Integer> entry : nodeLoad.entrySet()) {
			if (entry.getValue().equals(minCount)) {
				shortList.add(entry.getKey());
			}
		}

		int r = this.random.nextInt(shortList.size());

		return shortList.get(r);
	}

}
