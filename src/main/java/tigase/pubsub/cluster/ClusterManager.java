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
package tigase.pubsub.cluster;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;

public class ClusterManager {

	/**
	 * <channel, clusternode>
	 */
	private final HashMap<String, String> clusterNodes = new HashMap<String, String>();

	private final Set<String> cluster_nodes;

	protected Logger log = Logger.getLogger(this.getClass().getName());

	public ClusterManager(Set<String> cluster_nodes) {
		this.cluster_nodes = cluster_nodes;
	}

	public String getClusterNode(final String pubSubNode) {
		return this.clusterNodes.get(pubSubNode);
	}

	public String getLessLadenNode() {
		Map<String, Integer> x = new HashMap<String, Integer>();
		for (String nodeName : cluster_nodes) {
			x.put(nodeName, 0);
		}
		for (String node : this.clusterNodes.values()) {
			Integer c = x.get(node);
			if (c == null) {
				continue;
			} else {
				c++;
			}
			x.put(node, c);
		}

		Integer c = null;
		String name = null;

		for (Map.Entry<String, Integer> e : x.entrySet()) {
			if (c == null || c > e.getValue()) {
				c = e.getValue();
				name = e.getKey();
			}
		}
		return name;
	}

	public void nodesConnected(Set<String> node_hostnames) {
		// TODO Auto-generated method stub

	}

	public void nodesDisconnected(Set<String> node_hostnames) {
		if (node_hostnames != null)
			for (String hostname : node_hostnames) {
				String key = null;
				for (Entry<String, String> e : this.clusterNodes.entrySet()) {
					if (e.getValue().equals(hostname)) {
						key = e.getKey();
						break;
					}
				}
				if (key != null) {
					this.clusterNodes.remove(key);
				}
			}
	}

	public void registerOwner(String clusterNodeName, String... pubSubNodeName) {
		String debug = "";
		for (String p : pubSubNodeName) {
			this.clusterNodes.put(p, clusterNodeName);
			debug += p + " ";
		}
		log.fine("Cluster node '" + clusterNodeName + "' is owner of: " + debug);

	}

	public void removePubSubNode(String... nodeNames) {
		for (String nn : nodeNames) {
			log.fine("Node '" + nn + "' is unregistered from cluster.");
			this.clusterNodes.remove(nn);
		}
	}

}
