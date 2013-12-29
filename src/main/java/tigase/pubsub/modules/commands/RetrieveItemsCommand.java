package tigase.pubsub.modules.commands;

import java.util.Calendar;
import java.util.Date;

import tigase.adhoc.AdHocCommand;
import tigase.adhoc.AdHocCommandException;
import tigase.adhoc.AdHocResponse;
import tigase.adhoc.AdhHocRequest;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.form.Form;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IItems;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.util.DateTimeFormatter;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class RetrieveItemsCommand implements AdHocCommand {

	private final PubSubConfig config;

	private final DateTimeFormatter dtf = new DateTimeFormatter();

	private final IPubSubRepository repository;

	private final UserRepository userRepo;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param directPubSubRepository
	 * @param userRepo
	 */
	public RetrieveItemsCommand(PubSubConfig config, IPubSubRepository repository, UserRepository userRepo) {
		this.repository = repository;
		this.config = config;
		this.userRepo = userRepo;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param request
	 * @param response
	 * 
	 * @throws AdHocCommandException
	 */
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
					Form form = new Form("result", "Retrieve items", null);

					form.addField(Field.fieldTextSingle("tigase-pubsub#node-name", "", "Node name"));
					form.addField(Field.fieldTextSingle("tigase-pubsub#timestamp", dtf.formatDateTime(new Date()),
							"Items since"));

					response.getElements().add(form.getElement());
					response.startSession();
				} else {
					Form form = new Form(data);
					if ("submit".equals(form.getType())) {
						final Boolean rebuild = form.getAsBoolean("tigase-pubsub#delete-all");

						String nodeName = form.getAsString("tigase-pubsub#node-name");
						String timeStr = form.getAsString("tigase-pubsub#timestamp");
						final Calendar timestamp = timeStr == null || timeStr.trim().length() == 0 ? null
								: dtf.parseDateTime(timeStr);

						if (nodeName == null) {
							throw new AdHocCommandException(Authorization.BAD_REQUEST, "Empty node name.");
						} else if (timestamp == null) {
							throw new AdHocCommandException(Authorization.BAD_REQUEST, "Invalid timestamp.");
						}

						// ==================

						Element f = new Element("x", new String[] { "xmlns" }, new String[] { "jabber:x:data" });
						f.addChild(new Element("title", "Items"));
						Element reported = new Element("reported");
						reported.addChild(new Element("field", new String[] { "var" }, new String[] { "id" }));
						f.addChild(reported);

						IItems nodeItems = repository.getNodeItems(request.getIq().getTo().getBareJID(), nodeName);
						String[] allItems = nodeItems.getItemsIdsSince(timestamp.getTime());
						for (String id : allItems) {
							Element i = new Element("item");
							Element fi = new Element("field", new String[]{"var"}, new String[]{"id"});
							fi.addChild(new Element("value", id));
							i.addChild(fi);
							f.addChild(i);
						}
						// ==================
						response.getElements().add(f);
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

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String getName() {
		return "Retrieve items";
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String getNode() {
		return "retrieve-items";
	}

}
