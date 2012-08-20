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

import java.util.ArrayList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class NodeDeleteModule extends AbstractModule {

	private static final Criteria CRIT_DELETE = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("delete"));

	private final ArrayList<NodeConfigListener> nodeConfigListeners = new ArrayList<NodeConfigListener>();

	private final PublishItemModule publishModule;

	public NodeDeleteModule(PubSubConfig config, IPubSubRepository pubsubRepository, PublishItemModule publishItemModule) {
		super(config, pubsubRepository);
		this.publishModule = publishItemModule;
	}

	public void addNodeConfigListener(NodeConfigListener listener) {
		this.nodeConfigListeners.add(listener);
	}

	protected void fireOnNodeDeleted(final String nodeName) {
		for (NodeConfigListener listener : this.nodeConfigListeners) {
			listener.onNodeDeleted(nodeName);
		}
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#delete-nodes" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_DELETE;
	}

	@Override
	public List<Element> process(Element element, ElementWriter elementWriter) throws PubSubException {
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
		final Element delete = pubSub.getChild("delete");
		final String nodeName = delete.getAttribute("node");
		try {
			if (nodeName == null) {
				throw new PubSubException(element, Authorization.NOT_ALLOWED);
			}

			AbstractNodeConfig nodeConfig = repository.getNodeConfig(nodeName);
			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}
			final IAffiliations nodeAffiliations = this.repository.getNodeAffiliations(nodeName);

			String jid = element.getAttribute("from");
			if (!this.config.isAdmin(JIDUtils.getNodeID(jid))) {
				UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(jid);
				if (!senderAffiliation.getAffiliation().isDeleteNode()) {
					throw new PubSubException(element, Authorization.FORBIDDEN);
				}
			}
			List<Element> resultArray = makeArray(createResultIQ(element));

			if (nodeConfig.isNotify_config()) {
				ISubscriptions nodeSubscriptions = this.repository.getNodeSubscriptions(nodeName);
				String pssJid = element.getAttribute("to");
				Element del = new Element("delete", new String[] { "node" }, new String[] { nodeName });
				resultArray.addAll(this.publishModule.prepareNotification(del, pssJid, nodeName, nodeConfig, nodeAffiliations,
						nodeSubscriptions));
			}

			final String parentNodeName = nodeConfig.getCollection();
			CollectionNodeConfig parentCollectionConfig = null;
			if (parentNodeName != null && !parentNodeName.equals("")) {
				parentCollectionConfig = (CollectionNodeConfig) repository.getNodeConfig(parentNodeName);
				if (parentCollectionConfig != null)
					parentCollectionConfig.removeChildren(nodeName);
			} else {
				repository.removeFromRootCollection(nodeName);
			}

			if (nodeConfig instanceof CollectionNodeConfig) {
				CollectionNodeConfig cnc = (CollectionNodeConfig) nodeConfig;
				final String[] childrenNodes = cnc.getChildren();
				if (childrenNodes != null && childrenNodes.length > 0) {
					for (String childNodeName : childrenNodes) {
						AbstractNodeConfig childNodeConfig = repository.getNodeConfig(childNodeName);
						if (childNodeConfig != null) {
							childNodeConfig.setCollection(parentNodeName);
							repository.update(childNodeName, childNodeConfig);
						}
						if (parentCollectionConfig != null) {
							parentCollectionConfig.addChildren(childNodeName);
						} else {
							repository.addToRootCollection(childNodeName);
						}
					}
				}
			}
			if (parentCollectionConfig != null) {
				repository.update(parentNodeName, parentCollectionConfig);
			}

			log.fine("Delete node [" + nodeName + "]");
			repository.deleteNode(nodeName);
			fireOnNodeDeleted(nodeName);
			return resultArray;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	};

	public void removeNodeConfigListener(NodeConfigListener listener) {
		this.nodeConfigListeners.remove(listener);
	}

}
