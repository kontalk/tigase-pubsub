package tigase.pubsub.modules.commands;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocResponse;
import tigase.adhoc.AdhHocRequest;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.RepositoryException;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class RebuildDatabaseCommand implements AdHocCommand {

	private final PubSubConfig config;
	private final PubSubDAO dao;

	public RebuildDatabaseCommand(PubSubConfig config, PubSubDAO directPubSubRepository) {
		this.dao = directPubSubRepository;
		this.config = config;
	}

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			if (!config.isAdmin(JIDUtils.getNodeID(request.getSender()))) {
				throw new AdHocCommandException(Authorization.FORBIDDEN);
			}

			final Element data = request.getCommand().getChild("x", "jabber:x:data");
			if (request.getAction() != null && "cancel".equals(request.getAction())) {
				response.cancelSession();
			} else if (data == null) {
				Form form = new Form("result", "Rebuild nodes tree", "To rebuild tree of nodes please check checkbox.");

				form.addField(Field.fieldBoolean("tigase-pubsub#rebuild", Boolean.FALSE, "Rebuild nodes tree?"));

				response.getElements().add(form.getElement());
				response.startSession();

			} else {
				Form form = new Form(data);
				if ("submit".equals(form.getType())) {
					final Boolean rebuild = form.getAsBoolean("tigase-pubsub#rebuild");

					if (rebuild != null && rebuild.booleanValue() == true) {
						startRebuild();
						Form f = new Form(null, "Info", "Nodes tree has been rebuild");
						response.getElements().add(f.getElement());
					} else {
						Form f = new Form(null, "Info", "Rebuild cancelled.");
						response.getElements().add(f.getElement());
					}
				}
				response.completeSession();
			}

		} catch (AdHocCommandException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "Rebuild database";
	}

	@Override
	public String getNode() {
		return "rebuild-db";
	}

	private void startRebuild() throws RepositoryException {
		final String[] allNodesId = dao.getNodesList();
		final Set<String> rootCollection = new HashSet<String>();
		final Map<String, AbstractNodeConfig> nodeConfigs = new HashMap<String, AbstractNodeConfig>();
		for (String nodeName : allNodesId) {
			AbstractNodeConfig nodeConfig = dao.getNodeConfig(nodeName);
			nodeConfigs.put(nodeName, nodeConfig);
			if (nodeConfig instanceof CollectionNodeConfig) {
				CollectionNodeConfig collectionNodeConfig = (CollectionNodeConfig) nodeConfig;
				collectionNodeConfig.setChildren(null);
			}
		}

		for (Entry<String, AbstractNodeConfig> entry : nodeConfigs.entrySet()) {
			final AbstractNodeConfig nodeConfig = entry.getValue();
			final String nodeName = entry.getKey();
			final String collectionNodeName = nodeConfig.getCollection();
			if (collectionNodeName == null || collectionNodeName.equals("")) {
				nodeConfig.setCollection("");
				rootCollection.add(nodeName);
			} else {
				AbstractNodeConfig potentialParent = nodeConfigs.get(collectionNodeName);
				if (potentialParent != null && potentialParent instanceof CollectionNodeConfig) {
					CollectionNodeConfig collectionConfig = (CollectionNodeConfig) potentialParent;
					collectionConfig.addChildren(nodeName);
				} else {
					nodeConfig.setCollection("");
					rootCollection.add(nodeName);
				}

			}
		}

		for (Entry<String, AbstractNodeConfig> entry : nodeConfigs.entrySet()) {
			final AbstractNodeConfig nodeConfig = entry.getValue();
			final String nodeName = entry.getKey();
			dao.update(nodeName, nodeConfig);
		}

		dao.removeAllFromRootCollection();
		for (String nodeName : rootCollection) {
			dao.addToRootCollection(nodeName);
		}

	}

}
