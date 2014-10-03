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
import tigase.pubsub.Affiliation;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.AbstractMessageReceiver;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;

public class LoadTestCommand implements AdHocCommand {

	private final AbstractMessageReceiver component;

	private final PubSubConfig config;

	private final IPubSubRepository repository;

	public LoadTestCommand(PubSubConfig config, IPubSubRepository repo, AbstractMessageReceiver component) {
		this.config = config;
		this.component = component;
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
					form.addField(Field.fieldTextSingle("frequency", "1", "Publishing frequency [push/s]"));
					form.addField(Field.fieldTextSingle("length", "20", "Published messages size"));
					form.addField(Field.fieldBoolean("nonBlocking", Boolean.FALSE, "Use non-blocking adding"));

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
						final Boolean nonBlocking = form.getAsBoolean("nonBlocking");

						AbstractNodeConfig cfg = repository.getNodeConfig(service, nodeName);

						if (cfg != null) {
							IAffiliations subscriptions = repository.getNodeAffiliations(service, nodeName);

							UsersAffiliation owner = null;
							UsersAffiliation publisher = null;

							for (UsersAffiliation a : subscriptions.getAffiliations()) {
								if (publisher == null && a.getAffiliation().isPublishItem()) {
									publisher = a;
								}
								if (owner == null && a.getAffiliation() == Affiliation.owner) {
									owner = a;
								}
								if (owner != null && publisher != null)
									break;
							}

							if (owner == null && publisher == null) {
								Form f = new Form(null, "Info", "Can't find publisher!");
								response.getElements().add(f.getElement());
							} else {
								startLoadTest(service, nodeName, owner != null ? owner.getJid() : publisher.getJid(), time,
										frequency, length, nonBlocking == null ? true : !nonBlocking);

								Form f = new Form(null, "Info", "Load Test started");

								response.getElements().add(f.getElement());
							}
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

	private void startLoadTest(BareJID serviceJid, String nodeName, BareJID publisher, Long time, Long frequency,
			Integer length, boolean useBlockingMethod) throws RepositoryException, UserNotFoundException, TigaseDBException {

		(new Thread(new LoadTestGenerator(component, serviceJid, nodeName, publisher, time, frequency, length,
				useBlockingMethod))).start();

	}
}
