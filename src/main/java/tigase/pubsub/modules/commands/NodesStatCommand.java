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
package tigase.pubsub.modules.commands;

import java.util.List;
import java.util.Map;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocResponse;
import tigase.adhoc.AdhHocRequest;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.cluster.ClusterManager;
import tigase.util.JIDUtils;
import tigase.xmpp.Authorization;

public class NodesStatCommand implements AdHocCommand {

	private ClusterManager clusterManager;

	private PubSubConfig config;

	public NodesStatCommand(final ClusterManager clusterManager, PubSubConfig config) {
		this.config = config;
		this.clusterManager = clusterManager;
	}

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			if (!config.isAdmin(JIDUtils.getNodeID(request.getSender())))
				throw new AdHocCommandException(Authorization.FORBIDDEN);

			Form form = new Form(null, "Nodes load stat", null);

			Map<String, List<String>> load = this.clusterManager.getNodeLoad();

			for (String node : this.clusterManager.getKnownNodes()) {
				List<String> list = load.get(node);
				String[] x = list == null ? new String[] {} : list.toArray(new String[] {});
				form.addField(Field.fieldTextMulti("load-node-" + node, x, "Cluster Node [" + node + "] entries:"));
			}

			response.getElements().add(form.getElement());
			response.completeSession();
		} catch (AdHocCommandException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "Nodes load";
	}

	@Override
	public String getNode() {
		return "nodes-stat";
	}

}
