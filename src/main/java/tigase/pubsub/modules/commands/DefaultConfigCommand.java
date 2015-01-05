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
package tigase.pubsub.modules.commands;

import java.util.logging.Logger;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocResponse;
import tigase.adhoc.AdhHocRequest;
import tigase.component2.eventbus.Event;
import tigase.component2.eventbus.EventHandler;
import tigase.component2.eventbus.EventType;
import tigase.db.UserRepository;
import tigase.form.Form;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PubSubComponent;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.modules.NodeConfigModule;
import tigase.pubsub.modules.commands.DefaultConfigCommand.DefaultNodeConfigurationChangedHandler.DefaultNodeConfigurationChangedEvent;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class DefaultConfigCommand implements AdHocCommand {

	public interface DefaultNodeConfigurationChangedHandler extends EventHandler {

		public static class DefaultNodeConfigurationChangedEvent extends Event<DefaultNodeConfigurationChangedHandler> {

			public static final EventType<DefaultNodeConfigurationChangedHandler> TYPE = new EventType<DefaultNodeConfigurationChangedHandler>();

			private PubSubConfig config;

			private final Packet packet;

			public DefaultNodeConfigurationChangedEvent(Packet packet, PubSubConfig config) {
				super(TYPE);
				this.packet = packet;
				this.config = config;
			}

			@Override
			protected void dispatch(DefaultNodeConfigurationChangedHandler handler) {
				handler.onDefaultConfigurationChanged(packet, config);
			}

		}

		void onDefaultConfigurationChanged(Packet packet, final PubSubConfig config);
	}

	private final PubSubConfig config;

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private final UserRepository userRepository;

	public DefaultConfigCommand(PubSubConfig config, UserRepository userRepository) {
		this.config = config;
		this.userRepository = userRepository;
	}

	public void addDefaultNodeConfigurationChangedHandler(DefaultNodeConfigurationChangedHandler handler) {
		config.getEventBus().addHandler(DefaultNodeConfigurationChangedEvent.TYPE, handler);
	}

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			final Element data = request.getCommand().getChild("x", "jabber:x:data");
			if (request.getAction() != null && "cancel".equals(request.getAction())) {

				response.cancelSession();
			} else if (data == null) {
				LeafNodeConfig defaultNodeConfig = new LeafNodeConfig("default");
				defaultNodeConfig.read(userRepository, config, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
				response.getElements().add(defaultNodeConfig.getFormElement());
				response.startSession();
			} else {
				Form form = new Form(data);
				if ("submit".equals(form.getType())) {
					LeafNodeConfig nodeConfig = new LeafNodeConfig("default");
					nodeConfig.read(userRepository, config, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);

					NodeConfigModule.parseConf(nodeConfig, request.getCommand(), config);

					nodeConfig.write(userRepository, config, PubSubComponent.DEFAULT_LEAF_NODE_CONFIG_KEY);
					DefaultNodeConfigurationChangedEvent event = new DefaultNodeConfigurationChangedEvent(request.getIq(),
							config);
					config.getEventBus().fire(event);

					Form f = new Form("result", "Info", "Default config saved.");

					response.getElements().add(f.getElement());
					response.completeSession();
				}
				response.completeSession();
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "Default config";
	}

	@Override
	public String getNode() {
		return "default-config";
	}

	public void removeDefaultNodeConfigurationChangedHandler(DefaultNodeConfigurationChangedHandler handler) {
		config.getEventBus().remove(DefaultNodeConfigurationChangedEvent.TYPE, handler);
	}
}
