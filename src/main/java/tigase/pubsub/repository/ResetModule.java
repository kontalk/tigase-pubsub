package tigase.pubsub.repository;

import java.util.List;
import java.util.logging.Level;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class ResetModule extends AbstractModule {

	private static final Criteria CRIT_RESET = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#admin")).add(ElementCriteria.name("reset"));

	private LeafNodeConfig defaultNodeConfig;

	public ResetModule(PubSubConfig config, PubSubRepository pubsubRepository, LeafNodeConfig defaultNodeConfig) {
		super(config, pubsubRepository);
		this.defaultNodeConfig = defaultNodeConfig;
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_RESET;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		try {

			if (!this.config.isAdmin(JIDUtils.getNodeID(element.getAttribute("from")))) {
				throw new PubSubException(element, Authorization.FORBIDDEN);
			}

			try {
				this.repository.getUserRepository().removeUser(this.config.getServiceName());

				this.repository.getUserRepository().setData(this.config.getServiceName(), "last-start",
						String.valueOf(System.currentTimeMillis()));
			} catch (UserNotFoundException e) {
				try {
					this.repository.getUserRepository().addUser(this.config.getServiceName());
					this.repository.getUserRepository().setData(this.config.getServiceName(), "last-start",
							String.valueOf(System.currentTimeMillis()));
					this.defaultNodeConfig.write(this.repository.getUserRepository(), config, "default-node-config");

				} catch (Exception e1) {
					log.log(Level.SEVERE, "PubSub repository initialization problem", e1);
					throw new RepositoryException("Cannot initialize PubSUb repository", e);
				}
			} catch (TigaseDBException e) {
				log.log(Level.SEVERE, "PubSub repository initialization problem", e);
				throw new RepositoryException("Cannot initialize PubSUb repository", e);
			}

			Element result = createResultIQ(element);
			return makeArray(result);
		} catch (PubSubException e1) {
			e1.printStackTrace();
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

}
