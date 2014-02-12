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

package tigase.pubsub.repository.migration;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.cached.NodeAffiliations;
import tigase.pubsub.repository.cached.NodeSubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.BareJID;

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:19:00 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class PubSubOldDAO implements IPubSubOldDAO {

	/** Field description */
	public final static String CREATION_DATE_KEY = "creation-date";
	private static final String ITEMS_KEY = "items";

	/** Field description */
	public static final String NODES_KEY = "nodes/";
	private static final String ROOT_COLLECTION_KEY = "root-collection";

	protected Logger log = Logger.getLogger(this.getClass().getName());
	private final SimpleParser parser = SingletonFactory.getParserInstance();
	final UserRepository repository;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param repository
	 * @param pubSubConfig
	 */
	public PubSubOldDAO(UserRepository repository) {
		this.repository = repository;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param id
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	@Override
	public Item getItem(BareJID serviceJid, String nodeName, String id) throws RepositoryException {
		try {
			Item item = new Item();
			String itemData = repository.getData(serviceJid, NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "data");
			char[] data = itemData.toCharArray();

			item.id = id;
			item.publisher = getItemPublisher(serviceJid, nodeName, id);
			item.creationDate = getItemCreationDate(serviceJid, nodeName, id);
			item.updateDate = getItemUpdateDate(serviceJid, nodeName, id);
			item.item = itemDataToElement(data);
			return item;
		} catch (UserNotFoundException e1) {
			log.log(Level.WARNING, "missing user for service jid = {0}", serviceJid);
			return null;
		} catch (Exception e) {
			throw new RepositoryException("Item reading error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#getItemCreationDate(java.lang
	 * .String, java.lang.String)
	 */

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param id
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public Date getItemCreationDate(BareJID serviceJid, final String nodeName, final String id) throws RepositoryException {
		try {
			String tmp = repository.getData(serviceJid, NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "creation-date");

			if (tmp == null) {
				return null;
			}

			Date d = new Date(Long.parseLong(tmp));

			return d;
		} catch (UserNotFoundException e1) {
			log.log(Level.WARNING, "missing user for service jid = {0}", serviceJid);
			return null;
		} catch (Exception e) {
			throw new RepositoryException("Items creation-date reading error", e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param id
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public String getItemPublisher(BareJID serviceJid, String nodeName, String id) throws RepositoryException {
		try {
			return repository.getData(serviceJid, NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "publisher");
		} catch (Exception e) {
			throw new RepositoryException("Items publisher reading error", e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	@Override
	public String[] getItemsIds(BareJID serviceJid, String nodeName) throws RepositoryException {
		try {
			String[] ids = repository.getSubnodes(serviceJid, NODES_KEY + nodeName + "/" + ITEMS_KEY);

			// repository.getKeys(config.getServiceName(), NODES_KEY + nodeName
			// + "/" + ITEMS_KEY + "/");
			return ids;
		} catch (UserNotFoundException e1) {
			log.log(Level.WARNING, "missing user for service jid = {0}", serviceJid);
			return null;
		} catch (Exception e) {
			throw new RepositoryException("Items list reading error", e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param id
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public Date getItemUpdateDate(BareJID serviceJid, String nodeName, String id) throws RepositoryException {
		try {
			String tmp = repository.getData(serviceJid, NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "update-date");

			if (tmp == null) {
				return null;
			}

			Date d = new Date(Long.parseLong(tmp));

			return d;
		} catch (UserNotFoundException e1) {
			log.log(Level.WARNING, "missing user for service jid = {0}", serviceJid);
			return null;
		} catch (Exception e) {
			throw new RepositoryException("Items update-date reading error", e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	@Override
	public UsersAffiliation[] getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException {
		try {
			String cnfData = repository.getData(serviceJid, NODES_KEY + nodeName, "affiliations");
			return NodeAffiliations.create(cnfData).getAffiliations();
		} catch (UserNotFoundException e1) {
			log.log(Level.WARNING, "missing user for service jid = {0}", serviceJid);
			return null;
		} catch (Exception e) {
			throw new RepositoryException("Node subscribers reading error", e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	@Override
	public AbstractNodeConfig getNodeConfig(BareJID serviceJid, final String nodeName) throws RepositoryException {
		try {
			Form cnfForm = readNodeConfigForm(serviceJid, nodeName);

			if (cnfForm == null) {
				return null;
			}

			NodeType type = NodeType.valueOf(cnfForm.getAsString("pubsub#node_type"));
			Class<? extends AbstractNodeConfig> cl = null;

			switch (type) {
			case collection:
				cl = CollectionNodeConfig.class;

				break;

			case leaf:
				cl = LeafNodeConfig.class;

				break;

			default:
				throw new RepositoryException("Unknown node type " + type);
			}

			AbstractNodeConfig nc = getNodeConfig(cl, nodeName, cnfForm);

			return nc;
		} catch (UserNotFoundException e1) {
			log.log(Level.WARNING, "missing user for service jid = {0}", serviceJid);
			return null;
		} catch (RepositoryException e) {
			throw e;
		} catch (Exception e) {
			throw new RepositoryException("Node configuration reading error", e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeConfigClass
	 * @param nodeName
	 * @param configForm
	 * @param <T>
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	public <T extends AbstractNodeConfig> T getNodeConfig(final Class<T> nodeConfigClass, final String nodeName,
			final Form configForm) throws RepositoryException {
		try {
			Constructor<T> constructor = nodeConfigClass.getConstructor(String.class);
			T nodeConfig = constructor.newInstance(nodeName);
			
			nodeConfig.copyFromForm(configForm);

			return nodeConfig;
		} catch (Exception e) {
			throw new RepositoryException("Node configuration reading error", e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	@Override
	public Date getNodeCreationDate(BareJID serviceJid, String nodeName) throws RepositoryException {
		try {
			String tmp = this.repository.getData(serviceJid, NODES_KEY + nodeName, CREATION_DATE_KEY);
			long l = Long.parseLong(tmp);

			return new Date(l);
		} catch (UserNotFoundException e1) {
			log.log(Level.WARNING, "missing user for service jid = {0}", serviceJid);
			return null;
		} catch (Exception e) {
			throw new RepositoryException("Node creation date getting error", e);
		}
	}

	@Override
	public BareJID getNodeCreator(BareJID serviceJid, String nodeName) throws RepositoryException {
		// let's use service jid as node creator as this store 
		// does not contain any information about creator
		return serviceJid;
	}
	
	/**
	 * Method description
	 * 
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	@Override
	public String[] getNodesList(BareJID serviceJid) throws RepositoryException {
		try {
			String[] nodes;

			//log.finer("Getting nodes list directly from DB");
			nodes = repository.getSubnodes(serviceJid, NODES_KEY);

			return nodes;
		} catch (UserNotFoundException e1) {
			log.log(Level.WARNING, "missing user for service jid = {0}", serviceJid);
			return null;
		} catch (Exception e) {
			log.log(Level.WARNING, "Nodes list getting error", e);

			throw new RepositoryException("Nodes list getting error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.pubsub.repository.PubSubRepository#getNodesList()
	 */

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
	@Override
	public UsersSubscription[] getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException {
		try {
			final NodeSubscriptions ns = NodeSubscriptions.create();
			int index = 0;

			while (true) {
				final String key = "subscriptions" + ((index == 0) ? "" : ("." + index));
				String cnfData = repository.getData(serviceJid, NODES_KEY + nodeName, key);

				if ((cnfData == null) || (cnfData.length() == 0)) {
					break;
				}

				ns.parse(cnfData);
				++index;
			}

			return ns.getSubscriptions();
		} catch (Exception e) {
			throw new RepositoryException("Node subscribers reading error", e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 * 
	 * @throws RepositoryException
	 */
//	public String[] getRootNodes(BareJID serviceJid) throws RepositoryException {
//		try {
//			String[] ids = repository.getKeys(serviceJid, ROOT_COLLECTION_KEY);
//
//			return ids;
//		} catch (UserNotFoundException e1) {
//			log.log(Level.WARNING, "missing user for service jid = {0}", serviceJid);
//			return null;
//		} catch (Exception e) {
//			throw new RepositoryException("Getting root collection error", e);
//		}
//	}

	/**
	 * Method description
	 * 
	 * 
	 * @throws RepositoryException
	 */
	@Override
	public void init() throws RepositoryException {

	}

	protected Element itemDataToElement(char[] data) {
		DomBuilderHandler domHandler = new DomBuilderHandler();

		parser.parse(domHandler, data, 0, data.length);

		Queue<Element> q = domHandler.getParsedElements();

		return q.element();
	}

	private Form readNodeConfigForm(final BareJID serviceJid, final String nodeName) throws UserNotFoundException,
			TigaseDBException {
		String cnfData = readNodeConfigFormData(serviceJid, nodeName);

		if (cnfData == null) {
			return null;
		}

		char[] data = cnfData.toCharArray();
		DomBuilderHandler domHandler = new DomBuilderHandler();

		parser.parse(domHandler, data, 0, data.length);

		Queue<Element> q = domHandler.getParsedElements();

		if ((q != null) && (q.size() > 0)) {
			Form form = new Form(q.element());

			return form;
		}

		return null;
	}

	protected String readNodeConfigFormData(final BareJID serviceJid, final String nodeName) throws TigaseDBException {
		return repository.getData(serviceJid, NODES_KEY + nodeName, "configuration");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.pubsub.repository.PubSubRepository#update(java.lang.String,
	 * tigase.pubsub.LeafNodeConfig)
	 */

	@Override
	public BareJID[] getServiceJids() throws RepositoryException {
		try {
			List<BareJID> users = repository.getUsers();
			return users.toArray(new BareJID[users.size()]);
		} catch (TigaseDBException ex) {
			throw new RepositoryException("Exception reading service jids", ex);
		}
	}

}
