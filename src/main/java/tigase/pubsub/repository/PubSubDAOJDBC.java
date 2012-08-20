/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2009 "Tomasz Sterna" <tomek@xiaoka.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev: $
 * Last modified by $Author: $
 * $Date: $
 */
package tigase.pubsub.repository;

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
import java.util.logging.Level;

import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.util.JIDUtils;
import tigase.xml.Element;

public class PubSubDAOJDBC extends PubSubDAO {

	/**
	 * Database active connection.
	 */
	private Connection conn = null;
	/**
	 * Prepared statement for testing whether database connection is still
	 * working. If not connection to database is recreated.
	 */
	private PreparedStatement conn_valid_st = null;

	/**
	 * Connection validation helper.
	 */
	private long connectionValidateInterval = 1000 * 60;
	private CallableStatement create_node_sp = null;
	/**
	 * Database connection string.
	 */
	private String db_conn = null;
	private CallableStatement delete_all_nodes_sp = null;
	private CallableStatement delete_item_sp = null;
	private CallableStatement delete_node_subscriptions_sp = null;
	private CallableStatement get_all_nodes_sp = null;
	private CallableStatement get_item_sp = null;
	private CallableStatement get_node_affiliations_sp = null;
	private CallableStatement get_node_configuration_sp = null;
	private CallableStatement get_node_items_ids_sp = null;
	private CallableStatement get_node_subscriptions_sp = null;
	/**
	 * Connection validation helper.
	 */
	private long lastConnectionValidated = 0;
	private CallableStatement remove_node_sp = null;
	private CallableStatement set_node_affiliations_sp = null;
	private CallableStatement set_node_configuration_sp = null;

	private CallableStatement set_node_subscriptions_sp = null;
	private CallableStatement write_item_sp = null;

	public PubSubDAOJDBC(UserRepository repository, PubSubConfig pubSubConfig, final String connection_str) {
		super(repository, pubSubConfig);
		this.db_conn = connection_str;
	}

	@Override
	public void addToRootCollection(String nodeName) throws RepositoryException {
		// We do not support collections yet, so all nodes are in root
		// collection.
		return;
	}

	/**
	 * <code>checkConnection</code> method checks database connection before any
	 * query. For some database servers (or JDBC drivers) it happens the
	 * connection is dropped if not in use for a long time or after certain
	 * timeout passes. This method allows us to detect the problem and
	 * reinitialize database connection.
	 * 
	 * @return a <code>boolean</code> value if the database connection is
	 *         working.
	 * @exception SQLException
	 *                if an error occurs on database query.
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

	@Override
	public void createNode(String nodeName, String ownerJid, AbstractNodeConfig nodeConfig, NodeType nodeType, String collection)
			throws RepositoryException {

		ResultSet rs = null;
		try {
			String serializedNodeConfig = null;
			if (nodeConfig != null) {
				nodeConfig.setNodeType(nodeType);
				serializedNodeConfig = nodeConfig.getFormElement().toString();
			}

			checkConnection();
			synchronized (create_node_sp) {
				create_node_sp.setString(1, nodeName);
				create_node_sp.setInt(2, nodeType.ordinal());
				create_node_sp.setString(3, JIDUtils.getNodeID(ownerJid));
				create_node_sp.setString(4, serializedNodeConfig);
				rs = create_node_sp.executeQuery();
			}
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
	public void destroy() {
		try {
			if (!conn.isClosed())
				conn.close();
		} catch (SQLException e) {
			log.log(Level.WARNING, "Problem closing jdbc connection: " + db_conn, e);
		}
		super.destroy();
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
	public Element getItem(String nodeName, String id) throws RepositoryException {
		return itemDataToElement(getStringFromItem(nodeName, id, 1).toCharArray());
	}

	@Override
	public Date getItemCreationDate(final String nodeName, final String id) throws RepositoryException {
		return getDateFromItem(nodeName, id, 3);
	}

	@Override
	public String getItemPublisher(String nodeName, String id) throws RepositoryException {
		return getStringFromItem(nodeName, id, 2);
	}

	@Override
	public String[] getItemsIds(String nodeName) throws RepositoryException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (get_node_items_ids_sp) {
				get_node_items_ids_sp.setString(1, nodeName);
				rs = get_node_items_ids_sp.executeQuery();
				List<String> ids = new ArrayList<String>();
				while (rs.next()) {
					ids.add(rs.getString(1));
				}
				return ids.toArray(new String[0]);
			}
		} catch (SQLException e) {
			throw new RepositoryException("Items list reading error", e);
		} finally {
			release(null, rs);
		} // end of catch
	}

	@Override
	public Date getItemUpdateDate(String nodeName, String id) throws RepositoryException {
		return getDateFromItem(nodeName, id, 4);
	}

	@Override
	public NodeAffiliations getNodeAffiliations(String nodeName) throws RepositoryException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (get_node_affiliations_sp) {
				get_node_affiliations_sp.setString(1, nodeName);
				rs = get_node_affiliations_sp.executeQuery();
				if (rs.next()) {
					return NodeAffiliations.create(rs.getString(1));
				}
				return null;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers reading error", e);
		} finally {
			release(null, rs);
		} // end of catch
	}

	@Override
	public String[] getNodesList() throws RepositoryException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (get_all_nodes_sp) {
				rs = get_all_nodes_sp.executeQuery();
				List<String> names = new ArrayList<String>();
				while (rs.next()) {
					names.add(rs.getString(1));
				}
				return names.toArray(new String[0]);
			}
		} catch (SQLException e) {
			throw new RepositoryException("Nodes list getting error", e);
		} finally {
			release(null, rs);
		} // end of catch
	}

	@Override
	public NodeSubscriptions getNodeSubscriptions(String nodeName) throws RepositoryException {
		ResultSet rs = null;
		try {
			final NodeSubscriptions ns = NodeSubscriptions.create();
			checkConnection();
			synchronized (get_node_subscriptions_sp) {
				get_node_subscriptions_sp.setString(1, nodeName);
				rs = get_node_subscriptions_sp.executeQuery();
				while (rs.next()) {
					ns.parse(rs.getString(1));
				}
				return ns;
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers reading error", e);
		} finally {
			release(null, rs);
		} // end of catch
	}

	public String getResourceUri() {
		return db_conn;
	}

	@Override
	public String[] getRootNodes() throws RepositoryException {
		return getNodesList();
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

	/**
	 * <code>initPreparedStatements</code> method initializes internal database
	 * connection variables such as prepared statements.
	 * 
	 * @exception SQLException
	 *                if an error occurs on database query.
	 */
	private void initPreparedStatements() throws SQLException {
		String query = "select 1";
		conn_valid_st = conn.prepareStatement(query);

		query = "{ call TigPubSubCreateNode(?, ?, ?, ?) }";
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

		query = "{ call TigPubSubGetAllNodes() }";
		get_all_nodes_sp = conn.prepareCall(query);

		query = "{ call TigPubSubDeleteAllNodes() }";
		delete_all_nodes_sp = conn.prepareCall(query);

		query = "{ call TigPubSubSetNodeConfiguration(?, ?) }";
		set_node_configuration_sp = conn.prepareCall(query);

		query = "{ call TigPubSubSetNodeAffiliations(?, ?) }";
		set_node_affiliations_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetNodeConfiguration(?) }";
		get_node_configuration_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetNodeAffiliations(?) }";
		get_node_affiliations_sp = conn.prepareCall(query);

		query = "{ call TigPubSubGetNodeSubscriptions(?) }";
		get_node_subscriptions_sp = conn.prepareCall(query);

		query = "{ call TigPubSubSetNodeSubscriptions(?, ?, ?) }";
		set_node_subscriptions_sp = conn.prepareCall(query);

		query = "{ call TigPubSubDeleteNodeSubscriptions(?, ?) }";
		delete_node_subscriptions_sp = conn.prepareCall(query);
	}

	/**
	 * <code>initRepo</code> method initializes database connection and data
	 * repository.
	 * 
	 * @exception SQLException
	 *                if an error occurs on database query.
	 */
	private void initRepo() throws SQLException {
		synchronized (db_conn) {
			conn = DriverManager.getConnection(db_conn);
			initPreparedStatements();
		}
	}

	@Override
	protected String readNodeConfigFormData(final String nodeName) throws TigaseDBException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (get_node_configuration_sp) {
				get_node_configuration_sp.setString(1, nodeName);
				rs = get_node_configuration_sp.executeQuery();
				if (rs.next()) {
					return rs.getString(1);
				}
				return null;
			}
		} catch (SQLException e) {
			throw new TigaseDBException("Node subscribers reading error", e);
		} finally {
			release(null, rs);
		} // end of catch
	}

	private void release(Statement stmt, ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException sqlEx) {
			}
		}
		if (stmt != null) {
			try {
				stmt.close();
			} catch (SQLException sqlEx) {
			}
		}
	}

	@Override
	public void removeAllFromRootCollection() throws RepositoryException {
		try {
			checkConnection();
			synchronized (delete_all_nodes_sp) {
				delete_all_nodes_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Removing root collection error", e);
		}
	}

	@Override
	public void removeFromRootCollection(String nodeName) throws RepositoryException {
		deleteNode(nodeName);
	}

	@Override
	public void removeSubscriptions(String nodeName, int changedIndex) throws RepositoryException {
		try {
			checkConnection();
			synchronized (delete_node_subscriptions_sp) {
				delete_node_subscriptions_sp.setString(1, nodeName);
				delete_node_subscriptions_sp.setInt(2, changedIndex);
				delete_node_subscriptions_sp.execute();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers fragment removing error", e);
		}
	}

	@Override
	public void updateAffiliations(String nodeName, String serializedData) throws RepositoryException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (set_node_affiliations_sp) {
				set_node_affiliations_sp.setString(1, nodeName);
				set_node_affiliations_sp.setString(2, serializedData);
				rs = set_node_affiliations_sp.executeQuery();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers writing error", e);
		} finally {
			release(null, rs);
		} // end of catch
	}

	@Override
	public void updateNodeConfig(final String nodeName, final String serializedData) throws RepositoryException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (set_node_configuration_sp) {
				set_node_configuration_sp.setString(1, nodeName);
				set_node_configuration_sp.setString(2, serializedData);
				rs = set_node_configuration_sp.executeQuery();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node configuration writing error", e);
		} finally {
			release(null, rs);
		} // end of catch
	}

	@Override
	public void updateSubscriptions(String nodeName, int changedIndex, String serializedData) throws RepositoryException {
		ResultSet rs = null;
		try {
			checkConnection();
			synchronized (set_node_subscriptions_sp) {
				set_node_subscriptions_sp.setString(1, nodeName);
				set_node_subscriptions_sp.setInt(2, changedIndex);
				set_node_subscriptions_sp.setString(3, serializedData);
				rs = set_node_subscriptions_sp.executeQuery();
			}
		} catch (SQLException e) {
			throw new RepositoryException("Node subscribers writing error", e);
		} finally {
			release(null, rs);
		} // end of catch
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

}
