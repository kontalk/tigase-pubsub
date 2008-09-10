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

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import tigase.cluster.ClusterElement;
import tigase.cluster.ClusteredComponent;
import tigase.pubsub.repository.RepositoryException;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;

public class PubSubClusterComponent extends PubSubComponent implements ClusteredComponent {

	private static Random random = new SecureRandom();

	private final Set<String> cluster_nodes = new LinkedHashSet<String>();

	public PubSubClusterComponent() {
		super();
		this.log = Logger.getLogger(this.getClass().getName());
		log.config("PubSubCluster Component starting");
	}

	@Override
	public String getComponentId() {
		String name;
		if (System.getProperty("test", "no").equals("yes")) {
			name = super.getComponentId().replace("@", ".");
		} else
			name = super.getComponentId();

		return name;
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

	private String getRandomNode() {
		String[] nodes = this.cluster_nodes.toArray(new String[] {});
		if (nodes == null || nodes.length == 0)
			return null;
		int a = random.nextInt(nodes.length);
		return nodes[a];
	}

	@Override
	protected void init() {
		if (System.getProperty("test", "no").equals("yes")) {
			final Set<String> n = new HashSet<String>();
			n.add("pubsub.sphere");
			n.add("pubsub1.sphere");
			// n.add("pubsub2.sphere");
			// n.add("pubsub3.sphere");
			final String msh = "********** !!!  TEST ENVIROMENT !!! **********";
			System.out.println(msh);
			log.config(msh);
			for (String string : n) {
				log.config("Test Node connected: " + string);
				cluster_nodes.add(string);
			}
		}

		super.init();

		log.config("PubSubCluster component configured.");
	}

	public void nodesConnected(Set<String> node_hostnames) {
		for (String node : node_hostnames) {
			log.finest("Node connected: " + node + " (" + getName() + "@" + node + ")");
			cluster_nodes.add(getName() + "@" + node);
		}
	}

	public void nodesDisconnected(Set<String> node_hostnames) {
		for (String node : node_hostnames) {
			log.finest("Node disconnected: " + node + " (" + getName() + "@" + node + ")");
			cluster_nodes.remove(getName() + "@" + node);
		}
	}

	protected void processMethodCall(String methodName, Map<String, String> allMethodParams) throws RepositoryException {
		log.severe("Unexpected remote method call");
		System.out.println("!!!! Unexpected remote method call !!!!");
	}

	@Override
	public void processPacket(final Packet packet) {
		if (packet.getElemName() == ClusterElement.CLUSTER_EL_NAME || packet.getElemName() == ClusterElement.CLUSTER_EL_NAME
				&& packet.getElement().getXMLNS() == ClusterElement.XMLNS) {
			log.finest("Handling as internal cluster message");
			final ClusterElement clel = new ClusterElement(packet.getElement());
			List<Element> elements = clel.getDataPackets();
			if (clel.getMethodName() != null) {
				try {
					processMethodCall(clel.getMethodName(), clel.getAllMethodParams());
				} catch (Exception e) {
					log.throwing("PubSub Service", "processPacket (remote method call)", e);
					e.printStackTrace();
					try {
						addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, e.getMessage(), true));
					} catch (PacketErrorTypeException e1) {
						e1.printStackTrace();
						log.throwing("PubSub Service", "processPacket (sending internal-server-error)", e);
					}
				}
			} else
				for (Element element : elements) {
					super.processPacket(new Packet(element));
				}
		} else {
			Element element = packet.getElement();
			String node = extractNodeName(element);
			if (node != null) {
				String clusterNode = getRandomNode();
				if (clusterNode == null || this.publishNodeModule.isPEPNodeName(node)) {
					super.processPacket(packet);
				} else {
					log.finest("Cluster node " + getComponentId() + " received PubSub node '" + node
							+ "' and sent it to cluster node [" + clusterNode + "]");
					sentToNode(packet, clusterNode);
				}
			} else {
				log.finest("Cluster node " + getComponentId() + " received stanza without node name");
				super.processPacket(packet);
			}
		}
	}

	/*
	 * protected void sentBroadcast(final String methodName, final Map<String,
	 * String> params) { StringBuilder sb = new StringBuilder(); for (String cNN
	 * : this.cluster_nodes) { sb.append(cNN + ", "); ClusterElement call =
	 * ClusterElement.createClusterMethodCall(getComponentId(), cNN, "set",
	 * methodName, params); Packet toSend = new
	 * Packet(call.getClusterElement()); addOutPacket(toSend); }
	 * log.finer("Sent broadcast '" + methodName + "' method to: " +
	 * sb.toString()); }
	 */
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
