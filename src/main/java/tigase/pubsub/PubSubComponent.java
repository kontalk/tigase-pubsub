/*
 * PubSubComponent.java
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

package tigase.pubsub;

import java.util.Map;
import java.util.logging.Level;

import tigase.component.AbstractComponent;
import tigase.component.PacketWriter;
import tigase.conf.Configurable;
import tigase.db.UserRepository;
import tigase.pubsub.modules.AdHocConfigCommandModule;
import tigase.pubsub.modules.DefaultConfigModule;
import tigase.pubsub.modules.DiscoverInfoModule;
import tigase.pubsub.modules.DiscoverItemsModule;
import tigase.pubsub.modules.JabberVersionModule;
import tigase.pubsub.modules.ManageAffiliationsModule;
import tigase.pubsub.modules.ManageSubscriptionModule;
import tigase.pubsub.modules.NodeConfigModule;
import tigase.pubsub.modules.NodeCreateModule;
import tigase.pubsub.modules.NodeDeleteModule;
import tigase.pubsub.modules.PendingSubscriptionModule;
import tigase.pubsub.modules.PresenceCollectorModule;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.modules.PurgeItemsModule;
import tigase.pubsub.modules.RetractItemModule;
import tigase.pubsub.modules.RetrieveAffiliationsModule;
import tigase.pubsub.modules.RetrieveItemsModule;
import tigase.pubsub.modules.RetrieveSubscriptionsModule;
import tigase.pubsub.modules.SubscribeNodeModule;
import tigase.pubsub.modules.UnsubscribeNodeModule;
import tigase.pubsub.modules.XmppPingModule;
import tigase.pubsub.modules.XsltTool;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.server.DisableDisco;

/**
 * Class description
 * 
 * 
 * @version 5.1.0, 2010.11.02 at 01:05:02 MDT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PubSubComponent extends AbstractComponent<PubSubConfig> implements Configurable, DisableDisco,
		DefaultNodeConfigListener {

	public static final String DEFAULT_LEAF_NODE_CONFIG_KEY = "default-node-config";
	private AdHocConfigCommandModule adHocCommandsModule;
	private DefaultConfigModule defaultConfigModule;
	protected LeafNodeConfig defaultNodeConfig;
	private ManageAffiliationsModule manageAffiliationsModule;
	private ManageSubscriptionModule manageSubscriptionModule;
	private NodeConfigModule nodeConfigModule;
	private NodeCreateModule nodeCreateModule;
	private NodeDeleteModule nodeDeleteModule;
	private PendingSubscriptionModule pendingSubscriptionModule;
	private PresenceCollectorModule presenceCollectorModule;
	private PublishItemModule publishNodeModule;
	protected CachedPubSubRepository pubsubRepository;
	private PurgeItemsModule purgeItemsModule;
	private RetractItemModule retractItemModule;
	private RetrieveItemsModule retrirveItemsModule;
	private SubscribeNodeModule subscribeNodeModule;

	private UnsubscribeNodeModule unsubscribeNodeModule;

	protected UserRepository userRepository;

	private XsltTool xslTransformer;

	@Override
	protected PubSubConfig createComponentConfigInstance(AbstractComponent<?> abstractComponent) {
		return new PubSubConfig(abstractComponent);
	}

	/**
	 * Method description
	 * 
	 */
	protected void init() {
		final PacketWriter writer = getWriter();
		this.xslTransformer = new XsltTool();
		this.presenceCollectorModule = registerModule(new PresenceCollectorModule(componentConfig, pubsubRepository, writer));
		this.publishNodeModule = registerModule(new PublishItemModule(componentConfig, this.pubsubRepository, writer,
				this.xslTransformer, this.presenceCollectorModule));
		this.retractItemModule = registerModule(new RetractItemModule(componentConfig, this.pubsubRepository, writer,
				this.publishNodeModule));
		this.pendingSubscriptionModule = registerModule(new PendingSubscriptionModule(componentConfig, this.pubsubRepository,
				writer));
		this.manageSubscriptionModule = registerModule(new ManageSubscriptionModule(componentConfig, this.pubsubRepository,
				writer));
		this.subscribeNodeModule = registerModule(new SubscribeNodeModule(componentConfig, this.pubsubRepository, writer,
				this.pendingSubscriptionModule));
		this.nodeCreateModule = registerModule(new NodeCreateModule(componentConfig, this.pubsubRepository, writer,
				this.defaultNodeConfig, this.publishNodeModule));
		this.nodeDeleteModule = registerModule(new NodeDeleteModule(componentConfig, this.pubsubRepository, writer,
				this.publishNodeModule));
		this.defaultConfigModule = registerModule(new DefaultConfigModule(componentConfig, this.pubsubRepository,
				this.defaultNodeConfig, writer));
		this.nodeConfigModule = registerModule(new NodeConfigModule(componentConfig, this.pubsubRepository, writer,
				this.defaultNodeConfig, this.publishNodeModule));
		this.unsubscribeNodeModule = registerModule(new UnsubscribeNodeModule(componentConfig, this.pubsubRepository, writer));
		this.manageAffiliationsModule = registerModule(new ManageAffiliationsModule(componentConfig, this.pubsubRepository,
				writer));
		this.retrirveItemsModule = registerModule(new RetrieveItemsModule(componentConfig, this.pubsubRepository, writer));
		this.purgeItemsModule = registerModule(new PurgeItemsModule(componentConfig, this.pubsubRepository, writer,
				this.publishNodeModule));
		registerModule(new JabberVersionModule(componentConfig, pubsubRepository, writer));
		this.adHocCommandsModule = registerModule(new AdHocConfigCommandModule(componentConfig, this.pubsubRepository, writer));
		registerModule(new DiscoverInfoModule(componentConfig, this.pubsubRepository, writer, modulesManager));
		registerModule(new DiscoverItemsModule(componentConfig, this.pubsubRepository, writer, this.adHocCommandsModule));
		registerModule(new RetrieveAffiliationsModule(componentConfig, this.pubsubRepository, writer));
		registerModule(new RetrieveSubscriptionsModule(componentConfig, this.pubsubRepository, writer));
		registerModule(new XmppPingModule(componentConfig, pubsubRepository, writer));

		this.pubsubRepository.init();
	}

	@Override
	public void onChangeDefaultNodeConfig() {
		try {
			this.defaultNodeConfig.read(userRepository, componentConfig, ____PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
			log.info("Node " + getComponentId() + " read default node configuration.");
		} catch (Exception e) {
			log.log(Level.SEVERE, "Reading default config error", e);
		}
	}

	@Override
	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);

		init();
	}
}
