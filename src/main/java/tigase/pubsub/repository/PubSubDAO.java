/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.pubsub.repository;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Logger;
import tigase.db.DBInitException;
import tigase.db.UserRepository;
import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.BareJID;
import tigase.xmpp.impl.roster.RosterElement;
import tigase.xmpp.impl.roster.RosterFlat;

/**
 *
 * @author andrzej
 */
public abstract class PubSubDAO<T> implements IPubSubDAO<T> {

	protected static final Logger log = Logger.getLogger(PubSubDAO.class.getCanonicalName());
	
	private final SimpleParser parser = SingletonFactory.getParserInstance();	
	private UserRepository repository;
	
	protected PubSubDAO() {
	}

	@Override
	public void destroy() {
		
	}
	
	/**
	 * Method description
	 *
	 *
	 * @param owner
	 * @param buddy
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	@Override
	public String[] getBuddyGroups(BareJID owner, BareJID buddy) throws RepositoryException {
		try {
			return this.repository.getDataList(owner, "roster/" + buddy, "groups");
		} catch (Exception e) {
			throw new RepositoryException("Getting buddy groups error", e);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param owner
	 * @param buddy
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	@Override
	public String getBuddySubscription(BareJID owner, BareJID buddy) throws RepositoryException {
		try {
			return this.repository.getData(owner, "roster/" + buddy, "subscription");
		} catch (Exception e) {
			throw new RepositoryException("Getting buddy subscription status error", e);
		}
	}
	
	/**
	 * Method description
	 *
	 *
	 * @param owner
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	@Override
	public Map<BareJID,RosterElement> getUserRoster(BareJID owner) throws RepositoryException {
		try {
			String tmp = this.repository.getData(owner, "roster");
			Map<BareJID,RosterElement> roster = new HashMap<BareJID,RosterElement>();
			if (tmp != null)
				RosterFlat.parseRosterUtil(tmp, roster, null);
			return roster;
		} catch (Exception e) {
			throw new RepositoryException("Getting user roster error", e);
		}
	}
	
	protected Element itemDataToElement(char[] data) {
		DomBuilderHandler domHandler = new DomBuilderHandler();
		parser.parse(domHandler, data, 0, data.length);
		Queue<Element> q = domHandler.getParsedElements();

		return q.element();
	}
	
	@Override
	public AbstractNodeConfig parseConfig(String nodeName, String data) throws RepositoryException {
		
		try {
			Form cnfForm = parseConfigForm(data);

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
		} catch (RepositoryException e) {
			throw e;
		} catch (Exception e) {
			throw new RepositoryException("Node configuration reading error", e);
		}
	}
	
	protected Form parseConfigForm(String cnfData) {
		if (cnfData == null)
			return null;
		
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

	protected <T extends AbstractNodeConfig> T getNodeConfig(final Class<T> nodeConfigClass, final String nodeName,
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
	
	@Override
	public void init(String resource_uri, Map<String, String> params, UserRepository userRepository) throws RepositoryException {
		try {
			initRepository(resource_uri, params);
		} catch (DBInitException ex) {
			throw new RepositoryException(ex);
		}
		this.repository = userRepository;
	}
}
