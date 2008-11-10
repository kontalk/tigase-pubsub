package tigase.pubsub.modules.commands;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocResponse;
import tigase.adhoc.AdhHocRequest;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.PubSubDAO;
import tigase.pubsub.repository.RepositoryException;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class DeleteAllNodesCommand implements AdHocCommand {

	private final PubSubConfig config;
	private final PubSubDAO dao;
	private final UserRepository userRepo;

	public DeleteAllNodesCommand(PubSubConfig config, PubSubDAO directPubSubRepository, UserRepository userRepo) {
		this.dao = directPubSubRepository;
		this.config = config;
		this.userRepo = userRepo;
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
				Form form = new Form("result", "Delete all nodes", "To DELETE ALL NODES please check checkbox.");

				form.addField(Field.fieldBoolean("tigase-pubsub#delete-all", Boolean.FALSE,
						"YES! I'm sure! I want to delete all nodes"));

				response.getElements().add(form.getElement());
				response.startSession();

			} else {
				Form form = new Form(data);
				if ("submit".equals(form.getType())) {
					final Boolean rebuild = form.getAsBoolean("tigase-pubsub#delete-all");

					if (rebuild != null && rebuild.booleanValue() == true) {
						startRemoving();
						Form f = new Form(null, "Info", "Nodes has been deleted");
						response.getElements().add(f.getElement());
					} else {
						Form f = new Form(null, "Info", "Deleting cancelled.");
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
		return "Deleting ALL nodes";
	}

	@Override
	public String getNode() {
		return "delete-all-nodes";
	}

	private void startRemoving() throws RepositoryException, UserNotFoundException, TigaseDBException {
		dao.removeAllFromRootCollection();
		userRepo.removeSubnode(config.getServiceName(), "nodes");
	}
}