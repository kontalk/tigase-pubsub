/*
 * NodeConfigModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
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

package tigase.pubsub.modules;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import tigase.component2.PacketWriter;
import tigase.component2.eventbus.Event;
import tigase.component2.eventbus.EventHandler;
import tigase.component2.eventbus.EventType;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.Affiliation;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.SendLastPublishedItem;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.modules.NodeConfigModule.NodeConfigurationChangedHandler.NodeConfigurationChangedEvent;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

/**
 * Class description
 * 
 * 
 */
public class NodeConfigModule extends AbstractConfigCreateNode {
	public interface NodeConfigurationChangedHandler extends EventHandler {

		public static class NodeConfigurationChangedEvent extends Event<NodeConfigurationChangedHandler> {

			public static final EventType<NodeConfigurationChangedHandler> TYPE = new EventType<NodeConfigurationChangedHandler>();

			private final String nodeName;

			private final Packet packet;

			public NodeConfigurationChangedEvent(Packet packet, String nodeName) {
				super(TYPE);
				this.packet = packet;
				this.nodeName = nodeName;
			}

			@Override
			protected void dispatch(NodeConfigurationChangedHandler handler) {
				handler.onConfigurationChanged(packet, nodeName);
			}

		}

		void onConfigurationChanged(Packet packet, final String nodeName);
	}

	private static final Criteria CRIT_CONFIG = ElementCriteria.name("iq").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(ElementCriteria.name("configure"));

	/**
	 * Method description
	 * 
	 * 
	 * @param a
	 * @param b
	 * 
	 * @return
	 */
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

	/**
	 * Method description
	 * 
	 * 
	 * @param conf
	 * @param configure
	 * 
	 * @throws PubSubException
	 */
	public static void parseConf(final AbstractNodeConfig conf, final Element configure, final PubSubConfig config) throws PubSubException {
		Element x = configure.getChild("x", "jabber:x:data");
		Form foo = new Form(x);

		if ((x != null) && "submit".equals(x.getAttributeStaticStr("type"))) {
			for (Field field : conf.getForm().getAllFields()) {
				final String var = field.getVar();
				Field cf = foo.get(var);
				
				if (!config.isSendLastPublishedItemOnPresence() && "pubsub#send_last_published_item".equals(var)){
					if (SendLastPublishedItem.on_sub_and_presence.name().equals(cf.getValue())) {
						throw new PubSubException(Authorization.NOT_ACCEPTABLE, "Requested on_sub_and_presence mode for sending last published item is disabled.");
					}
				}

				if (cf != null) {
					field.setValues(cf.getValues());
				}
			}
		}
	}

	private final PublishItemModule publishModule;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 * @param defaultNodeConfig
	 * @param publishItemModule
	 */
	public NodeConfigModule(PubSubConfig config, PacketWriter packetWriter, LeafNodeConfig defaultNodeConfig,
			PublishItemModule publishItemModule) {
		super(config, defaultNodeConfig, packetWriter);
		this.publishModule = publishItemModule;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param listener
	 */
	public void addNodeConfigurationChangedHandler(NodeConfigurationChangedHandler handler) {
		getEventBus().addHandler(NodeConfigurationChangedEvent.TYPE, handler);
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

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#config-node" };
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT_CONFIG;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param node
	 * @param children
	 * 
	 * @return
	 */
	protected boolean isIn(String node, String[] children) {
		if (node == null | children == null) {
			return false;
		}
		for (String x : children) {
			if (x.equals(node)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 * @return
	 * 
	 * @throws PubSubException
	 */
	@Override
	public void process(Packet packet) throws PubSubException {
		try {
			final BareJID toJid = packet.getStanzaTo().getBareJID();
			final Element element = packet.getElement();
			final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
			final Element configure = pubSub.getChild("configure");
			final String nodeName = configure.getAttributeStaticStr("node");
			final StanzaType type = packet.getType();
			final String id = element.getAttributeStaticStr("id");

			if (nodeName == null) {
				throw new PubSubException(element, Authorization.BAD_REQUEST, PubSubErrorCondition.NODEID_REQUIRED);
			}

			final AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}

			final IAffiliations nodeAffiliations = this.getRepository().getNodeAffiliations(toJid, nodeName);
			JID jid = packet.getStanzaFrom();

			if (!this.config.isAdmin(jid)) {
				UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(jid.getBareJID());

				if (senderAffiliation.getAffiliation() != Affiliation.owner) {
					throw new PubSubException(element, Authorization.FORBIDDEN);
				}
			}

			// TODO 8.2.3.4 No Configuration Options

			// final Element result = createResultIQ(element);
			final Packet result = packet.okResult((Element) null, 0);
			ISubscriptions nodeSubscriptions = this.getRepository().getNodeSubscriptions(toJid, nodeName);

			if (type == StanzaType.get) {
				Element rPubSub = new Element("pubsub", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/pubsub#owner" });
				Element rConfigure = new Element("configure", new String[] { "node" }, new String[] { nodeName });
				Element f = nodeConfig.getFormElement();

				rConfigure.addChild(f);
				rPubSub.addChild(rConfigure);
				result.getElement().addChild(rPubSub);				
			} else if (type == StanzaType.set) {
				String[] children = (nodeConfig.getChildren() == null) ? new String[] {} : Arrays.copyOf(
						nodeConfig.getChildren(), nodeConfig.getChildren().length);
				final String collectionOld = (nodeConfig.getCollection() == null) ? "" : nodeConfig.getCollection();

				parseConf(nodeConfig, configure, config);
				if (!collectionOld.equals(nodeConfig.getCollection())) {
					if (collectionOld.equals("")) {
						AbstractNodeConfig colNodeConfig = getRepository().getNodeConfig(toJid, nodeConfig.getCollection());

						if (colNodeConfig == null) {
							throw new PubSubException(Authorization.ITEM_NOT_FOUND, "(#1) Node '" + nodeConfig.getCollection()
									+ "' doesn't exists");
						}
						if (!(colNodeConfig instanceof CollectionNodeConfig)) {
							throw new PubSubException(Authorization.NOT_ALLOWED, "(#1) Node '" + nodeConfig.getCollection()
									+ "' is not collection node");
						}
						((CollectionNodeConfig) colNodeConfig).addChildren(nodeName);
						getRepository().update(toJid, colNodeConfig.getNodeName(), colNodeConfig);
						getRepository().removeFromRootCollection(toJid, nodeName);
						
						IAffiliations colNodeAffiliations = getRepository().getNodeAffiliations(toJid,
								colNodeConfig.getNodeName());
						ISubscriptions colNodeSubscriptions = getRepository().getNodeSubscriptions(toJid,
								colNodeConfig.getNodeName());
						Element associateNotification = createAssociateNotification(colNodeConfig.getNodeName(), nodeName);

						publishModule.sendNotifications(associateNotification, packet.getStanzaTo(),
								nodeName, nodeConfig, colNodeAffiliations, colNodeSubscriptions);
					}
					if (nodeConfig.getCollection().equals("")) {
						AbstractNodeConfig colNodeConfig = getRepository().getNodeConfig(toJid, collectionOld);

						if ((colNodeConfig != null) && (colNodeConfig instanceof CollectionNodeConfig)) {
							((CollectionNodeConfig) colNodeConfig).removeChildren(nodeName);
							getRepository().update(toJid, colNodeConfig.getNodeName(), colNodeConfig);
						}
						getRepository().addToRootCollection(toJid, nodeName);

						IAffiliations colNodeAffiliations = getRepository().getNodeAffiliations(toJid,
								colNodeConfig.getNodeName());
						ISubscriptions colNodeSubscriptions = getRepository().getNodeSubscriptions(toJid,
								colNodeConfig.getNodeName());
						Element disassociateNotification = createDisassociateNotification(collectionOld, nodeName);

						publishModule.sendNotifications(disassociateNotification, packet.getStanzaTo(),
								nodeName, nodeConfig, colNodeAffiliations, colNodeSubscriptions);
					}
				}
				if (nodeConfig instanceof CollectionNodeConfig) {
					final String[] removedChildNodes = diff((children == null) ? new String[] {} : children,
							(nodeConfig.getChildren() == null) ? new String[] {} : nodeConfig.getChildren());
					final String[] addedChildNodes = diff(
							(nodeConfig.getChildren() == null) ? new String[] {} : nodeConfig.getChildren(),
							(children == null) ? new String[] {} : children);

					for (String ann : addedChildNodes) {
						AbstractNodeConfig nc = getRepository().getNodeConfig(toJid, ann);

						if (nc == null) {
							throw new PubSubException(Authorization.ITEM_NOT_FOUND, "(#2) Node '" + ann + "' doesn't exists");
						}
						if (nc.getCollection().equals("")) {
							getRepository().removeFromRootCollection(toJid, nc.getNodeName());						
						} else {
							AbstractNodeConfig cnc = getRepository().getNodeConfig(toJid, nc.getCollection());

							if (cnc == null) {
								throw new PubSubException(Authorization.ITEM_NOT_FOUND, "(#3) Node '" + nc.getCollection()
										+ "' doesn't exists");
							}
							if (!(cnc instanceof CollectionNodeConfig)) {
								throw new PubSubException(Authorization.NOT_ALLOWED, "(#2) Node '" + nc.getCollection()
										+ "' is not collection node");
							}
							((CollectionNodeConfig) cnc).removeChildren(nc.getNodeName());
							getRepository().update(toJid, cnc.getNodeName(), cnc);
						}
						nc.setCollection(nodeName);
						getRepository().update(toJid, nc.getNodeName(), nc);

						Element associateNotification = createAssociateNotification(nodeName, ann);

						publishModule.sendNotifications(associateNotification, packet.getStanzaTo(),
								nodeName, nodeConfig, nodeAffiliations, nodeSubscriptions);
					}
					for (String rnn : removedChildNodes) {
						AbstractNodeConfig nc = getRepository().getNodeConfig(toJid, rnn);

						if (nc != null) {
							nc.setCollection("");
							getRepository().update(toJid, nc.getNodeName(), nc);
						}
						if ((rnn != null) && (rnn.length() != 0)) {
							Element disassociateNotification = createDisassociateNotification(nodeName, rnn);

							publishModule.sendNotifications(disassociateNotification,
									packet.getStanzaTo(), nodeName, nodeConfig, nodeAffiliations, nodeSubscriptions);
						}
					}
				}
				getRepository().update(toJid, nodeName, nodeConfig);

				NodeConfigurationChangedEvent event = new NodeConfigurationChangedEvent(packet, nodeName);
				getEventBus().fire(event);

				if (nodeConfig.isNotify_config()) {
					Element configuration = new Element("configuration", new String[] { "node" }, new String[] { nodeName });

					this.publishModule.sendNotifications(configuration, packet.getStanzaTo(), nodeName,
							nodeConfig, nodeAffiliations, nodeSubscriptions);
				}
			} else {
				throw new PubSubException(element, Authorization.BAD_REQUEST);
			}
			
			// we are sending ok result after applying all changes and after 
			// sending notifications, is it ok? XEP-0060 is not specific about this
			packetWriter.write(result);			
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param listener
	 */
	public void removeNodeConfigurationChangedHandler(NodeConfigurationChangedHandler handler) {
		getEventBus().remove(handler);
	}
}
