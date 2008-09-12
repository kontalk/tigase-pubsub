package tigase.pubsub.modules.commands;

import java.util.ArrayList;
import java.util.List;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocResponse;
import tigase.adhoc.AdhHocRequest;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.PubSubDAO;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class DatabaseCleanCommand implements AdHocCommand {

	private final PubSubConfig config;

	private final PubSubDAO dao;

	public DatabaseCleanCommand(PubSubConfig config, PubSubDAO dao) {
		this.config = config;
		this.dao = dao;
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

				Form form = new Form("result", "Cleaning bad/corrupted nodes",
						"To remove corrupted nodes from DB please check checkbox.");

				String[] nodes = dao.getNodesList();

				List<String> toRemove = new ArrayList<String>();
				for (String string : nodes) {
					NodeType type = dao.getNodeType(string);
					if (type == null) {
						toRemove.add(string);
					}
				}

				form.addField(Field.fieldTextMulti("tigase-pubsub#nodes-to-remove", toRemove.toArray(new String[] {}),
						"Corrupted nodes to remove"));

				form.addField(Field.fieldBoolean("tigase-pubsub#removing", Boolean.FALSE, "Remove nodes?"));

				response.getElements().add(form.getElement());
				response.startSession();
			} else {
				Form form = new Form(data);
				if ("submit".equals(form.getType())) {
					final Boolean removing = form.getAsBoolean("tigase-pubsub#removing");
					final String[] toRemove = form.getAsStrings("tigase-pubsub#nodes-to-remove");

					if (removing != null && removing.booleanValue() == true) {
						for (String n : toRemove) {
							dao.deleteNode(n);
						}
						Form f = new Form(null, "Info", "Given nodes has been removed");
						response.getElements().add(f.getElement());
					} else {
						Form f = new Form(null, "Info", "Removing cancelled.");
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
		return "Clean bad nodes";
	}

	@Override
	public String getNode() {
		return "clean-bad-nodes";
	}

}
