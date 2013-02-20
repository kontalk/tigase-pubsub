/*
 * RetrieveAffiliationsModule.java
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

//~--- non-JDK imports --------------------------------------------------------

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;

import tigase.pubsub.AbstractModule;
import tigase.pubsub.Affiliation;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.stateless.UsersAffiliation;

import tigase.util.JIDUtils;

import tigase.xml.Element;

//~--- JDK imports ------------------------------------------------------------

import java.util.List;

/**
 * Class description
 *
 *
 * @version        Enter version here..., 13/02/20
 * @author         Enter your name here...
 */
public class RetrieveAffiliationsModule
				extends AbstractModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
																				 ElementCriteria.name(
																					 "pubsub",
																					 "http://jabber.org/protocol/pubsub")).add(
																						 ElementCriteria.name("affiliations"));

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param config
	 * @param pubsubRepository
	 */
	public RetrieveAffiliationsModule(PubSubConfig config,
																		IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#retrieve-affiliations",
													"http://jabber.org/protocol/pubsub#publisher-affiliation",
													"http://jabber.org/protocol/pubsub#outcast-affiliation",
													"http://jabber.org/protocol/pubsub#member-affiliation" };
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param element
	 * @param elementWriter
	 *
	 * @return
	 *
	 * @throws PubSubException
	 */
	@Override
	public List<Element> process(Element element, ElementWriter elementWriter)
					throws PubSubException {
		try {
			final Element pubsub = element.getChild("pubsub",
															 "http://jabber.org/protocol/pubsub");
			final Element affiliations = pubsub.getChild("affiliations");
			final String senderJid     = element.getAttributeStaticStr("from");
			final String senderBareJid = JIDUtils.getNodeID(senderJid);
			final Element result       = createResultIQ(element);
			final Element pubsubResult = new Element("pubsub", new String[] { "xmlns" },
																		 new String[] {
																			 "http://jabber.org/protocol/pubsub" });

			result.addChild(pubsubResult);

			final Element affiliationsResult = new Element("affiliations");

			pubsubResult.addChild(affiliationsResult);

			IPubSubDAO directRepo = this.repository.getPubSubDAO();
			String[] nodes        = directRepo.getNodesList();

			if (nodes != null) {
				for (String node : nodes) {
					UsersAffiliation[] affilitaions =
						directRepo.getNodeAffiliations(node).getAffiliations();

					if (affiliations != null) {
						for (UsersAffiliation usersAffiliation : affilitaions) {
							if (senderBareJid.equals(usersAffiliation.getJid())) {
								Affiliation affiliation = usersAffiliation.getAffiliation();
								Element a               = new Element("affiliation",
																						new String[] { "node",
												"affiliation" }, new String[] { node, affiliation.name() });

								affiliationsResult.addChild(a);
							}
						}
					}
				}
			}

			return makeArray(result);
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
