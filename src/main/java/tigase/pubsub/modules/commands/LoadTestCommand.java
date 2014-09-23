package tigase.pubsub.modules.commands;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocResponse;
import tigase.adhoc.AdhHocRequest;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.modules.PublishItemModule;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;

public class LoadTestCommand implements AdHocCommand {

	private final PubSubConfig config;

	private final PublishItemModule publishModule;

	private final IPubSubRepository repository;

	public LoadTestCommand(PubSubConfig config, IPubSubRepository repo, PublishItemModule module) {
		this.config = config;
		this.publishModule = module;
		this.repository = repo;
	}

	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			if (!config.isAdmin(request.getSender())) {
				throw new AdHocCommandException(Authorization.FORBIDDEN);
			}

			final Element data = request.getCommand().getChild("x", "jabber:x:data");

			if ((request.getAction() != null) && "cancel".equals(request.getAction())) {
				response.cancelSession();
			} else {
				if (data == null) {
					Form form = new Form("result", "Load Test", "To start load test fill the form");

					form.addField(Field.fieldTextSingle("nodeId", "", "Node"));
					form.addField(Field.fieldTextSingle("time", "60", "Time of the test [s]"));
					form.addField(Field.fieldTextSingle("frequency", "60", "Publishing frequency [msg/min]"));
					form.addField(Field.fieldTextSingle("length", "512", "Published messages size"));

					response.getElements().add(form.getElement());
					response.startSession();
				} else {
					Form form = new Form(data);

					if ("submit".equals(form.getType())) {
						final BareJID service = request.getIq().getStanzaTo().getBareJID();
						final long time = form.getAsLong("time");
						final long frequency = form.getAsLong("frequency");
						final int length = form.getAsInteger("length");
						final String nodeName = form.getAsString("nodeId");

						AbstractNodeConfig cfg = repository.getNodeConfig(service, nodeName);

						if (cfg != null) {
							startLoadTest(service, nodeName, time, frequency, length);

							Form f = new Form(null, "Info", "Load Test started");

							response.getElements().add(f.getElement());
						} else {
							Form f = new Form(null, "Info", "Load Test cancelled. Node " + nodeName + " doesn't exists.");
							response.getElements().add(f.getElement());
						}
					}

					response.completeSession();
				}
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
		return "Load Test";
	}

	@Override
	public String getNode() {
		return "load-test";
	}

	private void startLoadTest(BareJID serviceJid, String nodeName, Long time, Long frequency, Integer length)
			throws RepositoryException, UserNotFoundException, TigaseDBException {

		(new Thread(new LoadTestGenerator(publishModule, serviceJid, nodeName, time, frequency, length))).start();

	}
}
