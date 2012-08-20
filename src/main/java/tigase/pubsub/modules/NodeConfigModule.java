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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class NodeConfigModule extends AbstractConfigCreateNode {

	private static final Criteria CRIT_CONFIG = ElementCriteria.name("iq").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("configure"));

	protected static String[] diff(String[] a, String[] b) {
		HashSet<String> r = new HashSet<String>();
		for (String $a : a) {
			r.add($a);
		}
		for (String $a : b) {
			r.add($a);
		}
		for (String $a : b) {
			r.remove($a);
		}
		return r.toArray(new String[] {});
	}

	public static void parseConf(final AbstractNodeConfig conf, final Element configure) throws PubSubException {
		Element x = configure.getChild("x", "jabber:x:data");
		Form foo = new Form(x);
		if (x != null && "submit".equals(x.getAttribute("type"))) {
			for (Field field : conf.getForm().getAllFields()) {
				final String var = field.getVar();
				Field cf = foo.get(var);
				if (cf != null) {
					field.setValues(cf.getValues());
				}
			}
		}
	}

	private final ArrayList<NodeConfigListener> nodeConfigListeners = new ArrayList<NodeConfigListener>();

	private final PublishItemModule publishModule;

	public NodeConfigModule(PubSubConfig config, IPubSubRepository pubsubRepository, LeafNodeConfig defaultNodeConfig,
			PublishItemModule publishItemModule) {
		super(config, pubsubRepository, defaultNodeConfig);
		this.publishModule = publishItemModule;
	}

	public void addNodeConfigListener(NodeConfigListener listener) {
		this.nodeConfigListeners.add(listener);
	}

	private Element createAssociateNotification(final String collectionNodeName, String associatedNodeName) {
		Element colE = new Element("collection", new String[] { "node" }, new String[] { collectionNodeName });
		colE.addChild(new Element("associate", new String[] { "node" }, new String[] { associatedNodeName }));
		return colE;
	}

	private Element createDisassociateNotification(final String collectionNodeName, String disassociatedNodeName) {
		Element colE = new Element("collection", new String[] { "node" }, new String[] { collectionNodeName });
		colE.addChild(new Element("disassociate", new String[] { "node" }, new String[] { disassociatedNodeName }));
		return colE;
	}

	protected void fireOnNodeConfigChange(final String nodeName) {
		for (NodeConfigListener listener : this.nodeConfigListeners) {
			listener.onNodeConfigChanged(nodeName);
		}
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#config-node" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_CONFIG;
	}

	protected boolean isIn(String node, String[] children) {
		if (node == null | children == null)
			return false;
		for (String x : children) {
			if (x.equals(node))
				return true;
		}
		return false;
	}

	@Override
	public List<Element> process(Element element, ElementWriter elementWriter) throws PubSubException {
		try {
			final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
			final Element configure = pubSub.getChild("configure");
			final String nodeName = configure.getAttribute("node");
			final String type = element.getAttribute("type");

			final String id = element.getAttribute("id");

			if (nodeName == null) {
				throw new PubSubException(element, Authorization.BAD_REQUEST, PubSubErrorCondition.NODEID_REQUIRED);
			}

			final AbstractNodeConfig nodeConfig = repository.getNodeConfig(nodeName);
			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}
			final IAffiliations nodeAffiliations = this.repository.getNodeAffiliations(nodeName);

			String jid = element.getAttribute("from");
			if (!this.config.isAdmin(JIDUtils.getNodeID(jid))) {
				UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(jid);
				if (senderAffiliation.getAffiliation() != Affiliation.owner) {
					throw new PubSubException(element, Authorization.FORBIDDEN);
				}
			}
			// TODO 8.2.3.4 No Configuration Options

			final Element result = createResultIQ(element);
			final List<Element> resultArray = makeArray(result);

			ISubscriptions nodeSubscriptions = this.repository.getNodeSubscriptions(nodeName);

			if ("get".equals(type)) {
				Element rPubSub = new Element("pubsub", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/pubsub#owner" });
				Element rConfigure = new Element("configure", new String[] { "node" }, new String[] { nodeName });
				Element f = nodeConfig.getFormElement();
				rConfigure.addChild(f);
				rPubSub.addChild(rConfigure);

				result.addChild(rPubSub);
			} else if ("set".equals(type)) {
				String[] children = nodeConfig.getChildren() == null ? new String[] {} : Arrays.copyOf(
						nodeConfig.getChildren(), nodeConfig.getChildren().length);
				final String collectionOld = nodeConfig.getCollection() == null ? "" : nodeConfig.getCollection();

				parseConf(nodeConfig, configure);

				if (!collectionOld.equals(nodeConfig.getCollection())) {
					if (collectionOld.equals("")) {
						AbstractNodeConfig colNodeConfig = repository.getNodeConfig(nodeConfig.getCollection());
						if (colNodeConfig == null)
							throw new PubSubException(Authorization.ITEM_NOT_FOUND, "(#1) Node '" + nodeConfig.getCollection()
									+ "' doesn't exists");
						if (!(colNodeConfig instanceof CollectionNodeConfig))
							throw new PubSubException(Authorization.NOT_ALLOWED, "(#1) Node '" + nodeConfig.getCollection()
									+ "' is not collection node");

						((CollectionNodeConfig) colNodeConfig).addChildren(nodeName);

						repository.update(colNodeConfig.getNodeName(), colNodeConfig);
						repository.removeFromRootCollection(nodeName);

						IAffiliations colNodeAffiliations = repository.getNodeAffiliations(colNodeConfig.getNodeName());
						ISubscriptions colNodeSubscriptions = repository.getNodeSubscriptions(colNodeConfig.getNodeName());

						Element associateNotification = createAssociateNotification(colNodeConfig.getNodeName(), nodeName);
						resultArray.addAll(publishModule.prepareNotification(associateNotification, element.getAttribute("to"),
								nodeName, nodeConfig, colNodeAffiliations, colNodeSubscriptions));
					}

					if (nodeConfig.getCollection().equals("")) {
						AbstractNodeConfig colNodeConfig = repository.getNodeConfig(collectionOld);

						if (colNodeConfig != null && colNodeConfig instanceof CollectionNodeConfig) {
							((CollectionNodeConfig) colNodeConfig).removeChildren(nodeName);
							repository.update(colNodeConfig.getNodeName(), colNodeConfig);
						}

						repository.addToRootCollection(nodeName);

						IAffiliations colNodeAffiliations = repository.getNodeAffiliations(colNodeConfig.getNodeName());
						ISubscriptions colNodeSubscriptions = repository.getNodeSubscriptions(colNodeConfig.getNodeName());

						Element disassociateNotification = createDisassociateNotification(collectionOld, nodeName);
						resultArray.addAll(publishModule.prepareNotification(disassociateNotification,
								element.getAttribute("to"), nodeName, nodeConfig, colNodeAffiliations, colNodeSubscriptions));

					}

				}

				if (nodeConfig instanceof CollectionNodeConfig) {
					final String[] removedChildNodes = diff(children == null ? new String[] {} : children,
							nodeConfig.getChildren() == null ? new String[] {} : nodeConfig.getChildren());
					final String[] addedChildNodes = diff(
							nodeConfig.getChildren() == null ? new String[] {} : nodeConfig.getChildren(),
							children == null ? new String[] {} : children);

					for (String ann : addedChildNodes) {
						AbstractNodeConfig nc = repository.getNodeConfig(ann);
						if (nc == null)
							throw new PubSubException(Authorization.ITEM_NOT_FOUND, "(#2) Node '" + ann + "' doesn't exists");

						if (nc.getCollection().equals("")) {
							repository.removeFromRootCollection(nc.getNodeName());
						} else {
							AbstractNodeConfig cnc = repository.getNodeConfig(nc.getCollection());
							if (cnc == null)
								throw new PubSubException(Authorization.ITEM_NOT_FOUND, "(#3) Node '" + nc.getCollection()
										+ "' doesn't exists");
							if (!(cnc instanceof CollectionNodeConfig))
								throw new PubSubException(Authorization.NOT_ALLOWED, "(#2) Node '" + nc.getCollection()
										+ "' is not collection node");

							((CollectionNodeConfig) cnc).removeChildren(nc.getNodeName());
							repository.update(cnc.getNodeName(), cnc);
						}

						nc.setCollection(nodeName);
						repository.update(nc.getNodeName(), nc);

						Element associateNotification = createAssociateNotification(nodeName, ann);
						resultArray.addAll(publishModule.prepareNotification(associateNotification, element.getAttribute("to"),
								nodeName, nodeConfig, nodeAffiliations, nodeSubscriptions));

					}

					for (String rnn : removedChildNodes) {
						AbstractNodeConfig nc = repository.getNodeConfig(rnn);
						if (nc != null) {
							nc.setCollection("");
							repository.update(nc.getNodeName(), nc);
						}
						if (rnn != null && rnn.length() != 0) {
							Element disassociateNotification = createDisassociateNotification(nodeName, rnn);
							resultArray.addAll(publishModule.prepareNotification(disassociateNotification,
									element.getAttribute("to"), nodeName, nodeConfig, nodeAffiliations, nodeSubscriptions));
						}
					}

				}

				repository.update(nodeName, nodeConfig);

				if (nodeConfig.isNotify_config()) {
					String pssJid = element.getAttribute("to");
					Element configuration = new Element("configuration", new String[] { "node" }, new String[] { nodeName });
					resultArray.addAll(this.publishModule.prepareNotification(configuration, pssJid, nodeName, nodeConfig,
							nodeAffiliations, nodeSubscriptions));
				}

			} else {
				throw new PubSubException(element, Authorization.BAD_REQUEST);
			}

			return resultArray;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	public void removeNodeConfigListener(NodeConfigListener listener) {
		this.nodeConfigListeners.remove(listener);
	}

}
