/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tigase.cluster.ClusterElement;
import tigase.cluster.ClusteredComponent;
import tigase.pubsub.cluster.ClusterManager;
import tigase.pubsub.repository.PubSubRepositoryListener;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;

public class PubSubClusterComponent extends PubSubComponent implements ClusteredComponent, PubSubRepositoryListener {

	private final Set<String> cluster_nodes = new LinkedHashSet<String>();

	private final ClusterManager clusterManager;

	public static final String METHOD_GOT_NODE = "cluster_node_got_pubsub_node";

	private static final String METHOD_DELETE_NODE = "pubsub_node_delete";

	public PubSubClusterComponent() {
		this.clusterManager = new ClusterManager(this.cluster_nodes);
		if (System.getProperty("test", "no").equals("yes")) {
			final Set<String> n = new HashSet<String>();
			n.add("pubsub.sphere");
			n.add("pubsub1.sphere");
			n.add("pubsub2.sphere");
			n.add("pubsub3.sphere");
			final String msh = "********** !!!  TEST ENVIROMENT !!! **********";
			System.out.println(msh);
			log.config(msh);
			nodesConnected(n);
		}
	}

	protected String getFirstClusterNode() {
		String cluster_node = null;
		for (String node : cluster_nodes) {
			if (!node.equals(getComponentId())) {
				cluster_node = node;
				break;
			}
		}
		return cluster_node;
	}

	@Override
	protected void init() {
		super.init();
		pubsubRepository.addListener(this);
	}

	public void nodesConnected(Set<String> node_hostnames) {
		for (String node : node_hostnames) {
			cluster_nodes.add(node);
		}
		this.clusterManager.nodesConnected(node_hostnames);
	}

	public void nodesDisconnected(Set<String> node_hostnames) {
		for (String node : node_hostnames) {
			cluster_nodes.remove(node);
		}
		this.clusterManager.nodesDisconnected(node_hostnames);
	}

	@Override
	public void onChangeCollection(String nodeName, String oldCollectionName, String newCollectionName) {
		if (!"".equals(newCollectionName)) {
			final String newOwner = this.clusterManager.getClusterNode(newCollectionName);
			final Map<String, String> params = new HashMap<String, String>();
			params.put("pubSubNodeName", nodeName);
			params.put("clusterNodeName", newOwner);
			log.finest("Send broadcast about new nodes owner (after collection change).");
			sentBroadcast(METHOD_GOT_NODE, params);
		}
	}

	@Override
	public void onNodeDelete(String nodeName) {
		final Map<String, String> params = new HashMap<String, String>();
		params.put("pubSubNodeName", nodeName);
		log.finest("Send broadcast about node '" + nodeName + "' delete.");
		sentBroadcast(METHOD_DELETE_NODE, params);
	}

	protected void processMethodCall(String methodName, Map<String, String> allMethodParams) {
		if (METHOD_DELETE_NODE.equals(methodName)) {
			List<String> nodesNames = new ArrayList<String>();
			for (Map.Entry<String, String> pps : allMethodParams.entrySet()) {
				if (pps.getKey().startsWith("pubSubNodeName")) {
					nodesNames.add(pps.getValue());
				}
				this.clusterManager.removePubSubNode(nodesNames.toArray(new String[] {}));
			}
		} else if (METHOD_GOT_NODE.equals(methodName)) {
			List<String> nodesNames = new ArrayList<String>();
			for (Map.Entry<String, String> pps : allMethodParams.entrySet()) {
				if (pps.getKey().startsWith("pubSubNodeName")) {
					nodesNames.add(pps.getValue());
				}
			}
			String clName = allMethodParams.get("clusterNodeName");
			this.clusterManager.registerOwner(clName, nodesNames.toArray(new String[] {}));
		}
	}

	@Override
	public void processPacket(final Packet packet) {
		if (packet.getElemName() == ClusterElement.CLUSTER_EL_NAME && packet.getElement().getXMLNS() == ClusterElement.XMLNS) {
			final ClusterElement clel = new ClusterElement(packet.getElement());
			List<Element> elements = clel.getDataPackets();
			if (clel.getMethodName() != null) {
				processMethodCall(clel.getMethodName(), clel.getAllMethodParams());
			} else
				for (Element element : elements) {
					String nodeName = extractNodeName(element);
					if (nodeName != null && this.clusterManager.getClusterNode(nodeName) == null) {
						publishNodeGotNotification(nodeName);
					}
					super.processPacket(new Packet(element));
				}
		} else {
			Element element = packet.getElement();
			String node = extractNodeName(element);
			if (node != null) {
				String clusterNode = this.clusterManager.getClusterNode(node);
				if (clusterNode == null) {
					clusterNode = this.clusterManager.getLessLadenNode();
				}
				if (clusterNode == null) {
					super.processPacket(packet);
				}
				log.finest("Cluster node " + getComponentId() + " received PubSub node '" + node + "' and sent it to cluster node "
						+ clusterNode);
				sentToNode(packet, clusterNode);
			} else {
				super.processPacket(packet);
			}
		}
	}

	protected void publishNodeGotNotification(final String... nodeName) {
		final Map<String, String> params = new HashMap<String, String>();

		for (int i = 0; i < nodeName.length; i++) {
			params.put("pubSubNodeName." + i, nodeName[i]);
		}
		params.put("clusterNodeName", getComponentId());
		log.finest("Send broadcast about new nodes owner.");
		sentBroadcast(METHOD_GOT_NODE, params);
	}

	protected void sentBroadcast(final String methodName, final Map<String, String> params) {
		for (String cNN : this.cluster_nodes) {
			ClusterElement call = ClusterElement.createClusterMethodCall(getComponentId(), cNN, "set", methodName, params);
			addOutPacket(new Packet(call.getClusterElement()));
		}
	}

	protected boolean sentToNextNode(ClusterElement clel) {
		ClusterElement next_clel = ClusterElement.createForNextNode(clel, cluster_nodes, getComponentId());
		if (next_clel != null) {
			addOutPacket(new Packet(next_clel.getClusterElement()));
			return true;
		} else {
			return false;
		}
	}

	protected boolean sentToNextNode(Packet packet) {
		if (cluster_nodes.size() > 0) {
			String sess_man_id = getComponentId();
			String cluster_node = getFirstClusterNode();
			if (cluster_node != null) {
				ClusterElement clel = new ClusterElement(sess_man_id, cluster_node, StanzaType.set, packet);
				clel.addVisitedNode(sess_man_id);
				log.finest("Sending packet to next node [" + cluster_node + "]");
				addOutPacket(new Packet(clel.getClusterElement()));
				return true;
			}
		}
		return false;
	}

	protected boolean sentToNode(final Packet packet, final String cluster_node) {
		if (cluster_nodes.size() > 0) {
			String sess_man_id = getComponentId();
			if (cluster_node != null) {
				ClusterElement clel = new ClusterElement(sess_man_id, cluster_node, StanzaType.set, packet);
				clel.addVisitedNode(sess_man_id);
				log.finest("Sending packet to next node [" + cluster_node + "]");
				addOutPacket(new Packet(clel.getClusterElement()));
				return true;
			}
		}
		return false;
	}
}
