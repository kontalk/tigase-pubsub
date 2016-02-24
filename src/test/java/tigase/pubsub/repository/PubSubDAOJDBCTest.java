/*
 * PubSubDAOJDBCTest.java
 *
 * Tigase PubSub Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
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
 */
package tigase.pubsub.repository;

import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.MethodSorters;
import org.junit.runners.model.Statement;
import tigase.db.DBInitException;
import tigase.pubsub.LeafNodeConfig;
import tigase.pubsub.NodeType;
import tigase.pubsub.repository.stateless.NodeMeta;
import tigase.util.SchemaLoader;
import tigase.util.TigaseStringprepException;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Created by andrzej on 23.02.2016.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PubSubDAOJDBCTest {

	private static final String PROJECT_ID = "pubsub";
	private static final String VERSION = "3.2.0";

	private static final String uri = System.getProperty("testDbUri");

	@ClassRule
	public static TestRule rule = new TestRule() {
		@Override
		public Statement apply(Statement stmnt, Description d) {
			if (uri == null) {
				return new Statement() {
					@Override
					public void evaluate() throws Throwable {
						Assume.assumeTrue("Ignored due to not passed DB URI!", false);
					}
				};
			}
			return stmnt;
		}
	};

	@BeforeClass
	public static void loadSchema() {
		if (uri.startsWith("jdbc:")) {
			String dbType;
			String dbName = null;
			String dbHostname = null;
			String dbUser = null;
			String dbPass = null;

			int idx = uri.indexOf(":", 5);
			dbType = uri.substring(5, idx);
			if ("jtds".equals(dbType)) dbType = "sqlserver";

			String rest = null;
			switch (dbType) {
				case "derby":
					dbName = uri.substring(idx+1, uri.indexOf(";"));
					break;
				case "sqlserver":
					idx = uri.indexOf("//", idx) + 2;
					rest = uri.substring(idx);
					for (String x : rest.split(";")) {
						if (!x.contains("=")) {
							dbHostname = x;
						} else {
							String p[] = x.split("=");
							switch (p[0]) {
								case "databaseName":
									dbName = p[1];
									break;
								case "user":
									dbUser = p[1];
									break;
								case "password":
									dbPass = p[1];
									break;
								default:
									// unknown setting
									break;
							}
						}
					}
					break;
				default:
					idx = uri.indexOf("//", idx) + 2;
					rest = uri.substring(idx);
					idx = rest.indexOf("/");
					dbHostname = rest.substring(0, idx);
					rest = rest.substring(idx+1);
					idx = rest.indexOf("?");
					dbName = rest.substring(0, idx);
					rest = rest.substring(idx + 1);
					for (String x : rest.split("&")) {
						String p[] = x.split("=");
						if (p.length < 2)
							continue;
						switch (p[0]) {
							case "user":
								dbUser = p[1];
								break;
							case "password":
								dbPass = p[1];
								break;
							default:
								break;
						}
					}
					break;
			}

			Properties props = new Properties();
			if (dbType != null)
				props.put("dbType", dbType);
			if (dbName != null)
				props.put("dbName", dbName);
			if (dbHostname != null)
				props.put("dbHostname", dbHostname);
			if (dbUser != null)
				props.put("rootUser", dbUser);
			if (dbPass != null)
				props.put("rootPass", dbPass);
			if (dbUser != null)
				props.put("dbUser", dbUser);
			if (dbPass != null)
				props.put("dbPass", dbPass);

			SchemaLoader loader = SchemaLoader.newInstance(props);
			loader.validateDBConnection(props);
			loader.validateDBExists(props);
			props.put("file", "database/" + dbType + "-" + PROJECT_ID + "-schema-" + VERSION + ".sql");
			Assert.assertEquals(SchemaLoader.Result.ok, loader.loadSchemaFile(props));
			loader.shutdown(props);
		}
	}

	@AfterClass
	public static void cleanDerby() {
		if (uri.contains("jdbc:derby:")) {
			File f = new File("derby_test");
			if (f.exists()) {
				if (f.listFiles() != null) {
					Arrays.asList(f.listFiles()).forEach(f2 -> {
						if (f2.listFiles() != null) {
							Arrays.asList(f2.listFiles()).forEach(f3 -> f3.delete());
						}
						f2.delete();
					});
				}
				f.delete();
			}
		}
	}

	private PubSubDAOJDBC repo = new PubSubDAOJDBC();

	private BareJID serviceJid;
	private String nodeName;
	private Long nodeId;

	@Before
	public void setup() throws RepositoryException, DBInitException {
		repo.initRepository(uri, new HashMap<>());
	}

	@After
	public void tearDown() {
		if (serviceJid != null && nodeId != null) {
			try {
				repo.deleteNode(serviceJid, nodeId);
			} catch (RepositoryException e) {
				e.printStackTrace();
			}
		}
		repo.destroy();
	}

	@Test
	public void test1_nodeCreationMetaRetrievalNodeRemoval() throws TigaseStringprepException, RepositoryException {
		serviceJid  = BareJID.bareJIDInstance("pubsub.example.com");
		String nodeName = "test1_" + UUID.randomUUID();
		BareJID owner = BareJID.bareJIDInstance("owner1_" + UUID.randomUUID(), "example.com");
		LeafNodeConfig config = new LeafNodeConfig(nodeName);

		long timeBefore = System.currentTimeMillis() / 1000;
		nodeId = repo.createNode(serviceJid, nodeName, owner, config, NodeType.leaf, null);
		long timeAfter = System.currentTimeMillis() / 1000;
		assertNotNull(nodeId);

		NodeMeta meta = repo.getNodeMeta(serviceJid, nodeName);
		assertNotNull(meta);
		assertEquals(nodeId, meta.getNodeId());
		assertEquals(config.getNodeName(), meta.getNodeConfig().getNodeName());
		assertEquals(owner, meta.getCreator());
		assertNotNull(meta.getCreationTime());
		// division is required as some databases store time in seconds
//		long creationTime = meta.getCreationTime().getTime() / 1000;
//		assertTrue("got creation time = " + creationTime + " and period was (" + timeBefore + "," + timeAfter + ") = time drift " + (creationTime - timeBefore),
//				timeBefore < creationTime && timeAfter > creationTime);
	}

}
