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
package tigase.pubsub.repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AccessModel;
import tigase.pubsub.Affiliation;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.util.JIDUtils;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;

public class PubSubDAO implements IPubSubRepository {

	private static final String ACCESS_MODEL_KEY = "pubsub#access_model";

	static final String AFFILIATIONS_KEY = "affiliations";

	private static final String ASSOCIATE_COLLECTION_KEY = "pubsub#collection";

	public final static String CREATION_DATE_KEY = "creation-date";

	private static final String ITEMS_KEY = "items";

	private static final String NODE_TYPE_KEY = "pubsub#node_type";

	static final String NODES_KEY = "nodes/";

	private static final String SUBSCRIPTIONS_KEY = "subscriptions";

	final PubSubConfig config;

	private Logger log = Logger.getLogger(this.getClass().getName());

	private final SimpleParser parser = SingletonFactory.getParserInstance();

	final UserRepository repository;

	public PubSubDAO(UserRepository repository, PubSubConfig pubSubConfig) throws RepositoryException {
		this.repository = repository;
		this.config = pubSubConfig;

		try {
			this.repository.setData(this.config.getServiceName(), "last-start", String.valueOf(System.currentTimeMillis()));
		} catch (UserNotFoundException e) {
			try {
				this.repository.addUser(this.config.getServiceName());
				this.repository.setData(this.config.getServiceName(), "last-start", String.valueOf(System.currentTimeMillis()));
			} catch (Exception e1) {
				log.log(Level.SEVERE, "PubSub repository initialization problem", e1);
				throw new RepositoryException("Cannot initialize PubSUb repository", e);
			}
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "PubSub repository initialization problem", e);
			throw new RepositoryException("Cannot initialize PubSUb repository", e);
		}

	}

	@Override
	public void addListener(PubSubRepositoryListener listener) {
		throw new RuntimeException("Listeners are unsupported");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#addSubscriberJid(java.lang.
	 * String, java.lang.String, tigase.pubsub.Affiliation,
	 * tigase.pubsub.Subscription)
	 */
	public String addSubscriberJid(final String nodeName, final String jid, final Affiliation affiliation,
			final Subscription subscription) throws RepositoryException {
		try {
			final String subid = Utils.createUID();
			final String bareJid = JIDUtils.getNodeID(jid);

			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + AFFILIATIONS_KEY + "/" + bareJid,
					"affiliation", affiliation.name());

			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIPTIONS_KEY + "/" + jid, "subscription",
					subscription.name());
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIPTIONS_KEY + "/" + jid, "subid", subid);
			return subid;
		} catch (Exception e) {
			throw new RepositoryException("Subscriber adding error", e);
		}

	}

	@Override
	public void changeAffiliation(final String nodeName, final String jid, final Affiliation affiliation)
			throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + AFFILIATIONS_KEY + "/"
					+ JIDUtils.getNodeID(jid), "affiliation", affiliation.name());
		} catch (Exception e) {
			throw new RepositoryException("Affiliation writing error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#changeSubscription(java.lang
	 * .String, java.lang.String, tigase.pubsub.Subscription)
	 */
	public void changeSubscription(final String nodeName, final String jid, final Subscription subscription)
			throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIPTIONS_KEY + "/" + jid, "subscription",
					subscription.name());
		} catch (Exception e) {
			throw new RepositoryException("Subscription writing error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#createNode(java.lang.String,
	 * java.lang.String, tigase.pubsub.LeafNodeConfig, tigase.pubsub.NodeType,
	 * java.lang.String)
	 */
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName, CREATION_DATE_KEY,
					String.valueOf(System.currentTimeMillis()));
			if (nodeConfig != null)
				nodeConfig.write(repository, config, NODES_KEY + nodeName + "/configuration");
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/configuration/", NODE_TYPE_KEY, nodeType.name());

			addSubscriberJid(nodeName, ownerJid, Affiliation.owner, Subscription.subscribed);
			setNewNodeCollection(nodeName, collection);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Node creation error", e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#deleteItem(java.lang.String,
	 * java.lang.String)
	 */
	public void deleteItem(String nodeName, String id) throws RepositoryException {
		try {
			repository.removeSubnode(config.getServiceName(), nodeName + "/" + config.getServiceName() + "/" + NODES_KEY + nodeName
					+ "/" + ITEMS_KEY + "/" + id);
		} catch (Exception e) {
			throw new RepositoryException("Item removing error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#deleteNode(java.lang.String)
	 */
	public void deleteNode(String nodeName) throws RepositoryException {
		try {
			repository.removeSubnode(config.getServiceName(), NODES_KEY + nodeName);
		} catch (Exception e) {
			throw new RepositoryException("Node deleting error", e);
		}
	}

	@Override
	public void forgetConfiguration(String nodeName) {
	}

	public String[] getAffiliations(final String nodeName) throws RepositoryException {
		try {
			return repository.getSubnodes(config.getServiceName(), NODES_KEY + nodeName + "/" + AFFILIATIONS_KEY);
		} catch (Exception e) {
			throw new RepositoryException("Affilitaions getting error", e);
		}
	}

	@Override
	public String[] getBuddyGroups(String owner, String buddy) throws RepositoryException {
		try {
			return this.repository.getDataList(owner, "roster/" + buddy, "groups");
		} catch (Exception e) {
			throw new RepositoryException("Getting buddy groups error", e);
		}
	}

	@Override
	public String getBuddySubscription(String owner, String buddy) throws RepositoryException {
		try {
			return this.repository.getData(owner, "roster/" + buddy, "subscription");
		} catch (Exception e) {
			throw new RepositoryException("Getting buddy subscription status error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#getCollectionOf(java.lang.String
	 * )
	 */
	public String getCollectionOf(String nodeName) throws RepositoryException {
		try {
			return repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/configuration/", ASSOCIATE_COLLECTION_KEY);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Node collection getting error", e);
		}
	}

	@Override
	public IPubSubRepository getDirectRepository() {
		return this;
	}

	@Override
	public Element getItem(String nodeName, String id) throws RepositoryException {
		try {
			String itemData = repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "data");
			char[] data = itemData.toCharArray();
			DomBuilderHandler domHandler = new DomBuilderHandler();
			parser.parse(domHandler, data, 0, data.length);
			Queue<Element> q = domHandler.getParsedElements();
			return q.element();
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
	public String getItemCreationDate(final String nodeName, final String id) throws RepositoryException {
		try {
			return repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "creation-date");
		} catch (Exception e) {
			throw new RepositoryException("Items creation-date reading error", e);
		}
	}

	public String getItemPublisher(String nodeName, String id) throws RepositoryException {
		try {
			return repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "publisher");
		} catch (Exception e) {
			throw new RepositoryException("Items publisher reading error", e);
		}
	}

	@Override
	public String[] getItemsIds(String nodeName) throws RepositoryException {
		try {
			String[] ids = repository.getSubnodes(config.getServiceName(), NODES_KEY + nodeName + "/" + ITEMS_KEY);
			// repository.getKeys(config.getServiceName(), NODES_KEY + nodeName
			// + "/" + ITEMS_KEY + "/");
			return ids;
		} catch (Exception e) {
			throw new RepositoryException("Items list reading error", e);
		}
	}

	public String getItemUpdateDate(String nodeName, String id) throws RepositoryException {
		try {
			return repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "update-date");
		} catch (Exception e) {
			throw new RepositoryException("Items update-date reading error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#getNodeAccessModel(java.lang
	 * .String)
	 */
	public AccessModel getNodeAccessModel(String nodeName) throws RepositoryException {
		try {
			// name = repository.getData(config.getServiceName(),
			// NODES_KEY + nodeName, NODE_TYPE_KEY);
			String name = repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/configuration/", ACCESS_MODEL_KEY);

			if (name != null) {
				return AccessModel.valueOf(name);
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RepositoryException("AccessModel getting error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#getNodeChildren(java.lang.String
	 * )
	 */
	public String[] getNodeChildren(final String node) throws RepositoryException {
		List<String> result = new ArrayList<String>();
		final String tmpNode = node == null ? "" : node;
		for (String name : getNodesList()) {
			final String collection = getCollectionOf(name);
			if (tmpNode.equals(collection)) {
				result.add(name);
			}
		}
		return result.toArray(new String[] {});
	}

	public <T extends AbstractNodeConfig> T getNodeConfig(final Class<T> nodeConfigClass, final String nodeName)
			throws RepositoryException {
		try {
			T nodeConfig = nodeConfigClass.newInstance();
			nodeConfig.read(repository, config, NODES_KEY + nodeName + "/configuration");
			Field f = Field.fieldTextMulti("pubsub#children", getNodeChildren(nodeName), null);
			nodeConfig.add(f);
			return nodeConfig;
		} catch (Exception e) {
			throw new RepositoryException("Node configuration reading error", e);
		}

	}

	public AbstractNodeConfig getNodeConfig(final String nodeName) throws RepositoryException {
		try {
			NodeType type = getNodeType(nodeName);
			if (type == null)
				return null;
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
			return getNodeConfig(cl, nodeName);
		} catch (RepositoryException e) {
			throw e;
		} catch (Exception e) {
			throw new RepositoryException("Node configuration reading error", e);
		}

	}

	public Date getNodeCreationDate(String nodeName) throws RepositoryException {
		try {
			String tmp = this.repository.getData(config.getServiceName(), NODES_KEY + nodeName, CREATION_DATE_KEY);
			long l = Long.parseLong(tmp);
			return new Date(l);
		} catch (Exception e) {
			throw new RepositoryException("Node creation date getting error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.pubsub.repository.PubSubRepository#getNodesList()
	 */
	public String[] getNodesList() throws RepositoryException {
		try {
			log.finer("Getting nodes list directly from DB");
			String[] nodes = repository.getSubnodes(config.getServiceName(), NODES_KEY);
			if (nodes != null) {
				StringBuilder sb = new StringBuilder();
				for (String string : nodes) {
					sb.append(string);
					sb.append(", ");
				}
				log.finest("Readed nodes: " + sb.toString());
			}
			return nodes;
		} catch (Exception e) {
			log.log(Level.SEVERE, "Nodes list getting error", e);
			throw new RepositoryException("Nodes list getting error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#getNodeType(java.lang.String)
	 */
	public NodeType getNodeType(String nodeName) throws RepositoryException {
		try {
			// name = repository.getData(config.getServiceName(),
			// NODES_KEY + nodeName, NODE_TYPE_KEY);
			String name = repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/configuration/", NODE_TYPE_KEY);

			if (name != null) {
				return NodeType.valueOf(name);
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RepositoryException("Owner getting error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#getSubscriberAffiliation(java
	 * .lang.String, java.lang.String)
	 */
	public Affiliation getSubscriberAffiliation(final String nodeName, final String jid) throws RepositoryException {
		try {
			String tmp = repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + AFFILIATIONS_KEY + "/"
					+ JIDUtils.getNodeID(jid), "affiliation");
			// NODES_KEY + nodeName + "/" + SUBSCRIBES_KEY, jid);
			if (tmp != null) {
				return Affiliation.valueOf(tmp);
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RepositoryException("Affiliation getting error", e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#getSubscription(java.lang.String
	 * , java.lang.String)
	 */
	public Subscription getSubscription(String nodeName, String jid) throws RepositoryException {
		try {
			String tmp = repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIPTIONS_KEY + "/" + jid,
					"subscription");
			if (tmp != null) {
				return Subscription.valueOf(tmp);
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RepositoryException("Subscription getting error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#getSubscriptionId(java.lang
	 * .String, java.lang.String)
	 */
	public String getSubscriptionId(String nodeName, String jid) throws RepositoryException {
		try {
			return repository.getData(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIPTIONS_KEY + "/" + jid, "subid");
		} catch (Exception e) {
			throw new RepositoryException("SubID reading error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#getSubscribersJid(java.lang
	 * .String)
	 */
	public String[] getSubscriptions(String nodeName) throws RepositoryException {
		try {
			return repository.getSubnodes(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIPTIONS_KEY);
		} catch (Exception e) {
			throw new RepositoryException("Subscribers getting  error", e);
		}
	}

	@Override
	public String[] getUserRoster(String owner) throws RepositoryException {
		try {
			return this.repository.getSubnodes(owner, "roster");
		} catch (Exception e) {
			throw new RepositoryException("Getting user roster error", e);
		}
	}

	@Override
	public void init() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#readNodeConfig(tigase.pubsub
	 * .LeafNodeConfig, java.lang.String)
	 */
	public void readNodeConfig(LeafNodeConfig nodeConfig, String nodeName) throws RepositoryException {
		try {
			nodeConfig.read(repository, config, NODES_KEY + nodeName + "/configuration");
			// if (nodeConfig.getNodeType() == NodeType.collection) {
			Field f = Field.fieldTextMulti("pubsub#children", getNodeChildren(nodeName), null);
			nodeConfig.add(f);
			// }
		} catch (Exception e) {
			throw new RepositoryException("Node configuration reading error", e);
		}
	}

	@Override
	public void removeListener(PubSubRepositoryListener listener) {
		throw new RuntimeException("Listeners are unsupported");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#removeSubscriber(java.lang.
	 * String, java.lang.String)
	 */
	public void removeSubscriber(final String nodeName, final String jid) throws RepositoryException {
		try {
			repository.removeSubnode(config.getServiceName(), NODES_KEY + nodeName + "/" + SUBSCRIPTIONS_KEY + "/" + jid);
			repository.removeSubnode(config.getServiceName(), NODES_KEY + nodeName + "/" + AFFILIATIONS_KEY + "/"
					+ JIDUtils.getNodeID(jid));
		} catch (Exception e) {
			throw new RepositoryException("Subscriber removing error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#setNewNodeCollection(java.lang
	 * .String, java.lang.String)
	 */
	public void setNewNodeCollection(String nodeName, String collectionNew) throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/configuration/", ASSOCIATE_COLLECTION_KEY,
					collectionNew);
		} catch (Exception e) {
			throw new RepositoryException("Node collection writing error", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.pubsub.repository.PubSubRepository#update(java.lang.String,
	 * tigase.pubsub.LeafNodeConfig)
	 */
	public void update(final String nodeName, final AbstractNodeConfig nodeConfig) throws RepositoryException {
		try {
			nodeConfig.write(repository, config, NODES_KEY + nodeName + "/configuration");
		} catch (Exception e) {
			throw new RepositoryException("Node configuration writing error", e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.pubsub.repository.PubSubRepository#writeItem(java.lang.String,
	 * long, java.lang.String, java.lang.String, tigase.xml.Element)
	 */
	public void writeItem(final String nodeName, long timeInMilis, final String id, final String publisher, final Element item)
			throws RepositoryException {
		try {
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "data", item.toString());
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "creation-date",
					String.valueOf(timeInMilis));
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "update-date",
					String.valueOf(timeInMilis));
			repository.setData(config.getServiceName(), NODES_KEY + nodeName + "/" + ITEMS_KEY + "/" + id, "publisher", publisher);
		} catch (Exception e) {
			throw new RepositoryException("Item writing error", e);
		}
	}
}
