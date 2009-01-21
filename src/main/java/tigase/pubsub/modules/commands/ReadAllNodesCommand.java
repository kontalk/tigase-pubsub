package tigase.pubsub.modules.commands;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocResponse;
import tigase.adhoc.AdhHocRequest;
import tigase.form.Form;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.cached.CachedPubSubRepository;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class ReadAllNodesCommand implements AdHocCommand {

	private final PubSubConfig config;
	private final PubSubDAO dao;
	private final CachedPubSubRepository repository;

	public ReadAllNodesCommand(PubSubConfig config, PubSubDAO directPubSubRepository, CachedPubSubRepository pubsubRepository) {
		this.dao = directPubSubRepository;
		this.repository = pubsubRepository;
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
				Form form = new Form("result", "Reading all nodes", "To read all nodes from DB press finish");

				response.getElements().add(form.getElement());
				response.startSession();

			} else {
				Form form = new Form(data);
				if ("submit".equals(form.getType())) {
					startReading();
					Form f = new Form(null, "Info", "Nodes tree has been readed");
					response.getElements().add(f.getElement());
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
		return "Read ALL nodes";
	}

	@Override
	public String getNode() {
		return "read-all-nodes";
	}

	private void startReading() throws RepositoryException {
		final String[] allNodesId = dao.getNodesList();
		for (String n : allNodesId) {
			repository.getNodeConfig(n);
		}
	}

}
