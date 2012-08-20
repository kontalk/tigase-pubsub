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
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.Module;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class DiscoverInfoModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("query", "http://jabber.org/protocol/disco#info"));

	private ArrayList<Module> modules;

	public DiscoverInfoModule(PubSubConfig config, IPubSubRepository pubsubRepository, ArrayList<Module> modules) {
		super(config, pubsubRepository);
		this.modules = modules;
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(Element element, ElementWriter elementWriter) throws PubSubException {
		try {
			final String senderJid = element.getAttribute("from");
			final Element query = element.getChild("query", "http://jabber.org/protocol/disco#info");
			final String nodeName = query.getAttribute("node");

			Element resultIq = createResultIQ(element);
			Element resultQuery = new Element("query", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/disco#info" });
			resultIq.addChild(resultQuery);

			if (nodeName == null) {
				resultQuery.addChild(new Element("identity", new String[] { "category", "type", "name" }, new String[] {
						"pubsub", "service", "Publish-Subscribe" }));
				for (Module module : this.modules) {
					String[] features = module.getFeatures();
					if (features != null) {
						for (String f : features) {
							resultQuery.addChild(new Element("feature", new String[] { "var" }, new String[] { f }));
						}
					}
				}
			} else {
				AbstractNodeConfig nodeConfig = this.repository.getNodeConfig(nodeName);
				if (nodeConfig == null)
					throw new PubSubException(Authorization.ITEM_NOT_FOUND);

				boolean allowed = (senderJid == null || nodeConfig == null) ? true : Utils.isAllowedDomain(senderJid,
						nodeConfig.getDomains());

				if (!allowed)
					throw new PubSubException(Authorization.FORBIDDEN);

				resultQuery.addChild(new Element("identity", new String[] { "category", "type" }, new String[] { "pubsub",
						nodeConfig.getNodeType().name() }));
				resultQuery.addChild(new Element("feature", new String[] { "var" },
						new String[] { "http://jabber.org/protocol/pubsub" }));

				Form form = new Form("result", null, null);
				form.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/pubsub#meta-data"));
				form.addField(Field.fieldTextSingle("pubsub#title", nodeConfig.getTitle(), "A short name for the node"));
				resultQuery.addChild(form.getElement());
			}

			return makeArray(resultIq);
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
