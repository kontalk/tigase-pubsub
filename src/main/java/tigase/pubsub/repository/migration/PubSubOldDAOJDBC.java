/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tigase.pubsub.repository.migration;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.CollectionNodeConfig;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import static tigase.pubsub.NodeType.collection;
import static tigase.pubsub.NodeType.leaf;
import tigase.pubsub.repository.NodeAffiliations;
import tigase.pubsub.repository.NodeSubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.BareJID;

/**
 *
 * @author andrzej
 */
public class PubSubOldDAOJDBC implements IPubSubOldDAO {

	private Connection conn;
	
	private PreparedStatement get_service_jids_st = null;
	private PreparedStatement get_nodes_list_st = null;
	private PreparedStatement get_node_creation_date = null;
	private PreparedStatement get_node_creator = null;
	private PreparedStatement get_node_config = null;
	private PreparedStatement get_node_affiliations = null;
	private PreparedStatement get_node_subscriptions = null;
	private PreparedStatement get_item_ids = null;
	private PreparedStatement get_item = null;
	
	private final SimpleParser parser = SingletonFactory.getParserInstance();
	
	public PubSubOldDAOJDBC(String type, String uri) throws RepositoryException {
		String driverClass = null;
		if (type.equals("mysql")) driverClass = "com.mysql.jdbc.Driver";
		else if (type.equals("pgsql")) driverClass = "org.postgresql.Driver";
		else if (type.equals("sqlserver")) driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
				
		try {
			Class.forName( driverClass, true, this.getClass().getClassLoader() );
			conn = DriverManager.getConnection(uri);
			conn.setAutoCommit(true);
		}
		catch (Exception ex) {
			throw new RepositoryException("could not initialize repository", ex);
		}
	}
	
	@Override
	public BareJID[] getServiceJids() throws RepositoryException {
		ResultSet rs = null;
		try {
			synchronized (get_service_jids_st) {
				rs = get_service_jids_st.executeQuery();
				List<BareJID> result = new ArrayList<BareJID>();
				while (rs.next())
					result.add(BareJID.bareJIDInstanceNS(rs.getString(1)));
				return result.toArray(new BareJID[result.size()]);
			}
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not retreive service jids", ex);
		}
		finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ex) {
					Logger.getLogger(PubSubOldDAOJDBC.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}

	@Override
	public String[] getNodesList(BareJID serviceJid) throws RepositoryException {
		ResultSet rs = null;
		try {
			synchronized (get_nodes_list_st) {
				get_nodes_list_st.setString(1, serviceJid.toString());
				rs = get_nodes_list_st.executeQuery();
				List<String> result = new ArrayList<String>();
				while (rs.next())
					result.add(rs.getString(1));
				return result.toArray(new String[result.size()]);			
			}
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not retreive nodes list", ex);
		}
		finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ex) {
					Logger.getLogger(PubSubOldDAOJDBC.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}

	@Override
	public Date getNodeCreationDate(BareJID serviceJid, String nodeName) throws RepositoryException {
		ResultSet rs = null;
		try {
			synchronized (get_node_creation_date) {
				get_node_creation_date.setString(1, serviceJid.toString());
				get_node_creation_date.setString(2, nodeName);
				rs = get_node_creation_date.executeQuery();
				List<String> result = new ArrayList<String>();
				if (rs.next())
					return rs.getTimestamp(1);
				return null;		
			}
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not retreive node creation date", ex);
		}
		finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ex) {
					Logger.getLogger(PubSubOldDAOJDBC.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}

	@Override
	public BareJID getNodeCreator(BareJID serviceJid, String nodeName) throws RepositoryException {
		ResultSet rs = null;
		try {
			synchronized (get_node_creator) {
				get_node_creator.setString(1, serviceJid.toString());
				get_node_creator.setString(2, nodeName);
				rs = get_node_creator.executeQuery();
				List<String> result = new ArrayList<String>();
				if (rs.next())
					return BareJID.bareJIDInstanceNS(rs.getString(1));
				return null;		
			}
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not retreive node creation date", ex);
		}
		finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ex) {
					Logger.getLogger(PubSubOldDAOJDBC.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}
	
	@Override
	public AbstractNodeConfig getNodeConfig(BareJID serviceJid, String nodeName) throws RepositoryException {
		ResultSet rs = null;
		try {
			synchronized (get_node_config) {
				get_node_config.setString(1, serviceJid.toString());
				get_node_config.setString(2, nodeName);
				rs = get_node_config.executeQuery();
				List<String> result = new ArrayList<String>();
				if (!rs.next()) 
					return null;
				
				Form cnfForm = readNodeConfigForm(rs.getString(1));

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
			}
		}
		catch (Exception ex) {
			throw new RepositoryException("could not retreive node config", ex);
		}
		finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ex) {
					Logger.getLogger(PubSubOldDAOJDBC.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}

	@Override
	public UsersAffiliation[] getNodeAffiliations(BareJID serviceJid, String nodeName) throws RepositoryException {
		ResultSet rs = null;
		try {
			synchronized (get_node_affiliations) {
				get_node_affiliations.setString(1, serviceJid.toString());
				get_node_affiliations.setString(2, nodeName);
				rs = get_node_affiliations.executeQuery();
				List<String> result = new ArrayList<String>();
				if (rs.next()) {
					String data = rs.getString(1);
					return NodeAffiliations.create(data).getAffiliations();
				}
				return null;		
			}
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not retreive node affiliations", ex);
		}
		finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ex) {
					Logger.getLogger(PubSubOldDAOJDBC.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}	
	}

	@Override
	public UsersSubscription[] getNodeSubscriptions(BareJID serviceJid, String nodeName) throws RepositoryException {
		ResultSet rs = null;
		try {
			synchronized (get_node_subscriptions) {
				get_node_subscriptions.setString(1, serviceJid.toString());
				get_node_subscriptions.setString(2, nodeName);
				rs = get_node_subscriptions.executeQuery();
				NodeSubscriptions subscrs = NodeSubscriptions.create();
				if (rs.next()) {
					String data = rs.getString(1);
					subscrs.parse(data);
				}
				return subscrs.getSubscriptions();		
			}
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not retreive node subscriptions", ex);
		}
		finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ex) {
					Logger.getLogger(PubSubOldDAOJDBC.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}

	@Override
	public String[] getItemsIds(BareJID serviceJid, String nodeName) throws RepositoryException {
		ResultSet rs = null;
		try {
			synchronized (get_item_ids) {
				get_item_ids.setString(1, serviceJid.toString());
				get_item_ids.setString(2, nodeName);
				rs = get_item_ids.executeQuery();
				List<String> result = new ArrayList<String>();
				while (rs.next())
					result.add(rs.getString(1));
				return result.toArray(new String[result.size()]);			
			}
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not retreive node items ids", ex);
		}
		finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ex) {
					Logger.getLogger(PubSubOldDAOJDBC.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}

	@Override
	public Item getItem(BareJID serviceJid, String nodeName, String id) throws RepositoryException {
		ResultSet rs = null;
		try {
			synchronized (get_item) {
				get_item.setString(1, serviceJid.toString());
				get_item.setString(2, nodeName);
				get_item.setString(3, id);
				rs = get_item.executeQuery();
				
				if (rs.next()) {
					Item item = new Item();
					item.id = id;
					item.creationDate = rs.getTimestamp(2);
					item.updateDate = rs.getTimestamp(3);
					item.publisher = rs.getString(4);
					
					char[] data = rs.getString(5).toCharArray();
					DomBuilderHandler domHandler = new DomBuilderHandler();
					parser.parse(domHandler, data, 0, data.length);
					Queue<Element> q = domHandler.getParsedElements();
					
					item.item = (q != null && !q.isEmpty()) ? q.poll() : null;
					return item;					
				}
				return null;			
			}
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not retreive node items ids", ex);
		}
		finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ex) {
					Logger.getLogger(PubSubOldDAOJDBC.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}

	@Override
	public void init() throws RepositoryException {
		try {
			get_service_jids_st = conn.prepareStatement("select distinct service_jid from tig_pubsub_nodes_1");
			get_nodes_list_st = conn.prepareStatement("select name from tig_pubsub_nodes_1 where service_jid = ?");
			get_node_creation_date = conn.prepareStatement("select creation_date from tig_pubsub_nodes_1 where service_jid = ? and name = ?");
			get_node_creator = conn.prepareStatement("select creator from tig_pubsub_nodes_1 where service_jid = ? and name = ?");
			get_node_config = conn.prepareStatement("select configuration from tig_pubsub_nodes_1 where service_jid = ? and name = ?");
			get_node_affiliations = conn.prepareStatement("select affiliations from tig_pubsub_nodes_1 where service_jid = ? and name = ?");
			get_node_subscriptions = conn.prepareStatement("select subscriptions from tig_pubsub_subscriptions_1 s"
					+ " inner join tig_pubsub_nodes_1 n on s.service_jid_sha1 = n.service_jid_sha1 and s.node_name_sha1 = n.name_sha1"
					+ " where service_jid = ? and name = ? order by index");
			get_item_ids = conn.prepareStatement("select id from tig_pubsub_items_1 i"
					+ " inner join tig_pubsub_nodes_1 n on i.service_jid_sha1 = n.service_jid_sha1 and i.node_name_sha1 = n.name_sha1"
					+ " where service_jid = ? and name = ?");
			get_item = conn.prepareStatement("select id, creation_date, update_date, publisher, data from tig_pubsub_items_1 i"
					+ " inner join tig_pubsub_nodes_1 n on i.service_jid_sha1 = n.service_jid_sha1 and i.node_name_sha1 = n.name_sha1"
					+ " where service_jid = ? and name = ? and id = ?");
		}
		catch (SQLException ex) {
			throw new RepositoryException("could not initialize repository", ex);
		}
	}
	
	public <T extends AbstractNodeConfig> T getNodeConfig(final Class<T> nodeConfigClass, final String nodeName,
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
	
	private Form readNodeConfigForm(String cnfData) throws UserNotFoundException,
			TigaseDBException {
		if (cnfData == null) {
			return null;
		}

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
	
}
