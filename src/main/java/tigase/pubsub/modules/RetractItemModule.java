package tigase.pubsub.modules;

import java.util.ArrayList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.Affiliation;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.PubSubRepository;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class RetractItemModule extends AbstractModule {
	private static final Criteria CRIT_RETRACT = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("retract"));

	private final PubSubRepository repository;

	public RetractItemModule(final PubSubConfig config, final PubSubRepository pubsubRepository) {
		this.repository = pubsubRepository;
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_RETRACT;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element retract = pubSub.getChild("retract");
		final String nodeName = retract.getAttribute("node");

		try {
			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}
			NodeType nodeType = repository.getNodeType(nodeName);
			if (nodeType == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			} else if (nodeType == NodeType.collection) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported", "publish"));
			}

			final String[] allSubscribers = repository.getSubscribersJid(nodeName);
			final String publisherJid = findBestJid(allSubscribers, element.getAttribute("from"));

			Affiliation affiliation = repository.getSubscriberAffiliation(nodeName, publisherJid);

			if (affiliation != Affiliation.owner && affiliation != Affiliation.publisher) {
				throw new PubSubException(Authorization.FORBIDDEN);
			}

			LeafNodeConfig nodeConfig = new LeafNodeConfig();
			repository.readNodeConfig(nodeConfig, nodeName, false);

			if (!nodeConfig.isPersistItem()) {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, new PubSubErrorCondition("unsupported",
						"persistent-items"));
			}

			List<String> itemsToDelete = new ArrayList<String>();
			if (retract.getChildren() != null) {
				for (Element item : retract.getChildren()) {
					final String n = item.getAttribute("id");
					if (n != null) {
						itemsToDelete.add(n);
					} else {
						throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.ITEM_REQUIRED);
					}
				}
			} else {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.ITEM_REQUIRED);
			}

			List<Element> result = new ArrayList<Element>();
			result.add(createResultIQ(element));

			for (String id : itemsToDelete) {
				String date = repository.getItemCreationDate(nodeName, id);
				if (date != null) {
					repository.deleteItem(nodeName, id);

					for (String jid : getActiveSubscribers(repository, allSubscribers, nodeName)) {
						final String jidTO = jid;
						final String jidFrom = element.getAttribute("to");
						Element notification = createNotification(nodeConfig, itemsToDelete, nodeName, jidFrom, jidTO);
						result.add(notification);
					}

				}
			}

			return result;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	private Element createNotification(final LeafNodeConfig config, final List<String> itemsToSend, final String nodeName,
			final String fromJID, final String toJID) {
		Element message = new Element("message");
		message.setAttribute("from", fromJID);
		message.setAttribute("to", toJID);

		Element event = new Element("event", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub#event" });
		message.addChild(event);

		Element items = new Element("items", new String[] { "node" }, new String[] { nodeName });
		event.addChild(items);

		for (String id : itemsToSend) {
			items.addChild(new Element("retract", new String[] { "id" }, new String[] { id }));
		}

		return message;
	}

}
