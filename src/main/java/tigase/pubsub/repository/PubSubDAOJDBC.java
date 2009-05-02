/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2009 "Tomasz Sterna" <tomek@xiaoka.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev: $
 * Last modified by $Author: $
 * $Date: $
 */
package tigase.pubsub.repository;

//import java.lang.reflect.Constructor;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
//import java.util.Queue;
import java.util.logging.Level;
//import tigase.db.TigaseDBException;
//import tigase.db.UserNotFoundException;
//import tigase.db.TigaseDBException;
import tigase.db.TigaseDBException;
import tigase.db.UserExistsException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
//import tigase.db.jdbc.AuthorizationException;
//import tigase.db.jdbc.DBInitException;
//import tigase.db.jdbc.UserExistsException;
//import tigase.form.Form;
import tigase.pubsub.AbstractNodeConfig;
//import tigase.pubsub.CollectionNodeConfig;
//import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.PubSubDAO;
import tigase.util.JIDUtils;
//import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
//import tigase.xml.SimpleParser;
//import tigase.xml.SingletonFactory;

public class PubSubDAOJDBC extends PubSubDAO { private Exception e;
	
	/**
	 * Database connection string.
	 */
	private String db_conn = null;
	/**
	 * Database active connection.
	 */
	private Connection conn = null;
	
	private CallableStatement create_node_sp = null;
	private CallableStatement remove_node_sp = null;
	private CallableStatement get_item_sp = null;
	private CallableStatement write_item_sp = null;
	private CallableStatement delete_item_sp = null;
	private CallableStatement get_node_items_ids_sp = null;
	/**
	 * Prepared statement for testing whether database connection is still
	 * working. If not connection to database is recreated.
	 */
	private PreparedStatement conn_valid_st = null;

	/**
	 * Connection validation helper.
	 */
	private long lastConnectionValidated = 0;
	/**
	 * Connection validation helper.
	 */
	private long connectionValidateInterval = 1000*60;
	
	
	public PubSubDAOJDBC(UserRepository repository, PubSubConfig pubSubConfig, final String connection_str) {
		super(repository, pubSubConfig);
		this.db_conn = connection_str;
	}

	/**
	 * <code>initPreparedStatements</code> method initializes internal
	 * database connection variables such as prepared statements.
	 *
	 * @exception SQLException if an error occurs on database query.
	 */
	private void initPreparedStatements() throws SQLException {
		String query = "select 1";
		conn_valid_st = conn.prepareStatement(query);

		query = "{ call TigPubSubCreateNode(?, ?, ?) }";
		create_node_sp = conn.prepareCall(query);

		query = "{ call TigPubSubRemoveNode(?) }";
		remove_node_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetItem(?, ?) }";
		get_item_sp = conn.prepareCall(query);

		query = "{ call TigPubSubWriteItem(?, ?, ?, ?) }";
		write_item_sp = conn.prepareCall(query);

		query = "{ call TigPubSubDeleteItem(?, ?) }";
		delete_item_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetNodeItemsIds(?) }";
		get_node_items_ids_sp = conn.prepareCall(query);

/*		query = "{ call TigUserLoginPlainPw(?, ?) }";
		user_login_plain_pw_sp = conn.prepareCall(query);

		query = "{ call TigUserLogout(?) }";
		user_logout_sp = conn.prepareCall(query);*/
	}

	/**
	 * <code>initRepo</code> method initializes database connection
	 * and data repository.
	 *
	 * @exception SQLException if an error occurs on database query.
	 */
	private void initRepo() throws SQLException {
		synchronized (db_conn) {
			conn = DriverManager.getConnection(db_conn);
			initPreparedStatements();
		}
	}

	/**
	 * <code>checkConnection</code> method checks database connection before any
	 * query. For some database servers (or JDBC drivers) it happens the connection
	 * is dropped if not in use for a long time or after certain timeout passes.
	 * This method allows us to detect the problem and reinitialize database
	 * connection.
	 *
	 * @return a <code>boolean</code> value if the database connection is working.
	 * @exception SQLException if an error occurs on database query.
	 */
	private boolean checkConnection() throws SQLException {
		ResultSet rs = null;
		try {
			synchronized (conn_valid_st) {
				long tmp = System.currentTimeMillis();
				if ((tmp - lastConnectionValidated) >= connectionValidateInterval) {
					rs = conn_valid_st.executeQuery();
					lastConnectionValidated = tmp;
				} // end of if ()
			}
		} catch (Exception e) {
			initRepo();
		} finally {
			release(null, rs);
		} // end of try-catch
		return true;
	}

	private void release(Statement stmt, ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException sqlEx) { }
		}
		if (stmt != null) {
			try {
				stmt.close();
			} catch (SQLException sqlEx) { }
		}
	}

	public String getResourceUri() { return db_conn; }

	@Override
	public void addToRootCollection(String nodeName) throws RepositoryException {
		// We do not support collections yet, so all nodes are in root collection.
		return;
	}

	@Override
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
		throws RepositoryException {

		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (create_node_sp) {
				create_node_sp.setString(1, nodeName);
				create_node_sp.setInt(2, nodeType.ordinal());
				create_node_sp.setString(3, JIDUtils.getNodeID(ownerJid));
				rs = create_node_sp.executeQuery();
			}
			
			if (nodeConfig != null)
				update(nodeName, nodeConfig);
			
		} catch (SQLIntegrityConstraintViolationException e) {
			throw new RepositoryException("Error while adding node to repository, already exists?", e);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new RepositoryException("Problem accessing repository.", e);
		} finally {
			release(null, rs);
		}

	}

	@Override
	public void deleteNode(String nodeName) throws RepositoryException {
		try {
			checkConnection();
			synchronized (remove_node_sp) {
				remove_node_sp.setString(1, nodeName);
				remove_node_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node deleting error", e);
		}
	}

	@Override
	public void writeItem(final String nodeName, long timeInMilis, final String id, final String publisher, final Element item)
			throws RepositoryException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (write_item_sp) {
				write_item_sp.setString(1, nodeName);
				write_item_sp.setString(2, id);
				write_item_sp.setString(3, publisher);
				write_item_sp.setString(4, item.toString());
				rs = write_item_sp.executeQuery();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item writing error", e);
		} finally {
			release(null, rs);
		}
	}

	@Override
	public void deleteItem(String nodeName, String id) throws RepositoryException {
		try {
			checkConnection();
			synchronized (delete_item_sp) {
				delete_item_sp.setString(1, nodeName);
				delete_item_sp.setString(2, id);
				delete_item_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item removing error", e);
		}
	}

	@Override
	public String[] getItemsIds(String nodeName) throws RepositoryException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (get_node_items_ids_sp) {
				get_node_items_ids_sp.setString(1, nodeName);
				rs = get_item_sp.executeQuery();
				List<String> ids = new ArrayList<String>();
				while (rs.next()) {
					ids.add(rs.getString(1));
				}
				return (String[]) ids.toArray();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Items list reading error", e);
		} finally {
			release(null, rs);
		} // end of catch
	}

	@Override
	public Element getItem(String nodeName, String id) throws RepositoryException {
		return itemDataToElement(getStringFromItem(nodeName, id, 1).toCharArray());
	}

	public String getItemPublisher(String nodeName, String id) throws RepositoryException {
		return getStringFromItem(nodeName, id, 2);
	}

	@Override
	public Date getItemCreationDate(final String nodeName, final String id) throws RepositoryException {
		return getDateFromItem(nodeName, id, 3);
	}

	@Override
	public Date getItemUpdateDate(String nodeName, String id) throws RepositoryException {
		return getDateFromItem(nodeName, id, 4);
	}
	
	protected String getStringFromItem(String nodeName, String id, int field) throws RepositoryException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (get_item_sp) {
				get_item_sp.setString(1, nodeName);
				get_item_sp.setString(2, id);
				rs = get_item_sp.executeQuery();
				if (rs.next()) {
					return rs.getString(field);
				}
				return null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item field " + field + " reading error", e);
		} finally {
			release(null, rs);
		} // end of catch
	}
	
	protected Date getDateFromItem(String nodeName, String id, int field) throws RepositoryException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (get_item_sp) {
				get_item_sp.setString(1, nodeName);
				get_item_sp.setString(2, id);
				rs = get_item_sp.executeQuery();
				if (rs.next()) {
					String date = rs.getString(field);
					if (date == null)
						return null;
					return DateFormat.getDateInstance().parse(date);
				}
				return null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Item field " + field + " reading error", e);
		} catch (ParseException e) {
			throw new RepositoryException("Item field " + field + " parsing error", e);
		} finally {
			release(null, rs);
		} // end of catch
	}

	@Override
	public NodeAffiliations getNodeAffiliations(String nodeName) throws RepositoryException {
		throw new RepositoryException("Node subscribers reading error", e);
	}

	@Override
	public AbstractNodeConfig getNodeConfig(final String nodeName) throws RepositoryException {
		log.finest("in getNodeConfig("+nodeName+")");
		return null;
	}

	@Override
	public String[] getNodesList() throws RepositoryException {
		throw new RepositoryException("Nodes list getting error", e);
	}

	@Override
	public NodeSubscriptions getNodeSubscriptions(String nodeName) throws RepositoryException {
		return NodeSubscriptions.create();
	}

	@Override
	public String[] getRootNodes() throws RepositoryException {
		throw new RepositoryException("Getting root collection error", e);
	}

	@Override
	public void init() throws RepositoryException {
		try {
			initRepo();
		} catch (SQLException e) {
			conn = null;
			throw new RepositoryException("Problem initializing jdbc connection: " + db_conn, e);
		}
		super.init();
	}

	@Override
	public void destroy() {
		try {
			if (!conn.isClosed()) conn.close();
		} catch (SQLException e) {
			log.log(Level.WARNING, "Problem closing jdbc connection: " + db_conn, e);
		}
		super.destroy();
	}

	@Override
	public void removeFromRootCollection(String nodeName) throws RepositoryException {
		throw new RepositoryException("Removing from root collection error", e);
	}

	@Override
	public void update(final String nodeName, final AbstractNodeConfig nodeConfig) throws RepositoryException {
		log.finest("in update( nodeConfig )");
		return;
	}

	@Override
	public void update(String nodeName, IAffiliations affiliations) throws RepositoryException {
		log.finest("in update( affiliations )");
		return;
	}

}
