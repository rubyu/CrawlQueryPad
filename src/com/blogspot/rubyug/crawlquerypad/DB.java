
package com.blogspot.rubyug.crawlquerypad;

import java.io.*;
import java.sql.*;
import org.h2.jdbcx.JdbcConnectionPool;
import com.devx.io.TempFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DB {
  protected static Logger logger = LoggerFactory.getLogger(DB.class);

  private static String profile = null;
  private static JdbcConnectionPool connectionPool = null;

  public static void initialize()
  throws IOException, SQLException {
    File dbFile = TempFileManager.createTempFile("h2db", null);
    String dbPath = dbFile.getPath().replace("\\", "/");
    logger.debug("database path: " + dbPath);
    connectionPool = JdbcConnectionPool.create(
          "jdbc:h2:file:" +  dbPath + ";DEFAULT_LOCK_TIMEOUT=1000;DB_CLOSE_ON_EXIT=FALSE", "sa", "sa"
        );
    schemaUpdate();
  }
  public static Connection getConnection()
  throws SQLException {
    if (null == connectionPool) {
      throw new IllegalStateException();
    }
    return connectionPool.getConnection();
  }
  private static void schemaUpdate()
  throws SQLException {
    Connection conn = null;
    PreparedStatement prep = null;
    Statement st = null;
    ResultSet rs = null;
    try {
      conn = connectionPool.getConnection();
      st   = conn.createStatement();

      //always clear resonse_cache
      st.execute(
        "DROP TABLE IF EXISTS response_cache;");
      st.execute(
        "CREATE TABLE IF NOT EXISTS response_cache(" +
        "url VARCHAR PRIMARY KEY NOT NULL," +
        "state CLOB NOT NULL," +
        "header CLOB," +
        "title CLOB," +
        "text CLOB," +
        "content BLOB" +
        ");");
      //global
      st.execute(
        "CREATE TABLE IF NOT EXISTS global(" +
          "key VARCHAR NOT NULL PRIMARY KEY," +
          "state CLOB NOT NULL" +
        ");");

      //get schema state and schema version
      State schemaState = Global.getState(conn, "schema");
      Long schemaVersion = schemaState.getFirstOr("version", 0);

      logger.info("current schema version: " + schemaVersion);

      if (schemaVersion < 1) {
        st.execute(
          "CREATE TABLE IF NOT EXISTS api_state(" +
          "key VARCHAR NOT NULL PRIMARY KEY," +
          "state CLOB NOT NULL" +
          ");");
      }
      //save schema state
      schemaState.set("version", schemaVersion);
      Global.setState(conn, "schema", schemaState);
      conn.commit();
    } catch(SQLException e){
      conn.rollback();
      throw e;
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch(Exception e) {}
      }
      if (conn != null) {
        try {
          conn.close();
        } catch(Exception e) {}
      }
    }
  }
  public static class Global{
    /**
     * Gets a state.
     * @param Connection conn
     * @param String key
     * @return State
     * @throws SQLException
     */
    public static State getState(Connection conn, String key)
    throws SQLException{
      PreparedStatement prep = null;
      ResultSet rs = null;
      try {
        //get schema
        prep = conn.prepareStatement("SELECT * FROM global WHERE key=?");
        prep.setString(1, key);
        rs = prep.executeQuery();
        if (rs.next()) {
          return new State(rs.getString("state"));
        }
      } catch(UnsupportedEncodingException e) {
        logger.error(Utils.ThrowableToString(e));
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch(Exception e) {}
        }
      }
      return new State();
    }
    /**
     * Sets a state.
     * @param Connection conn
     * @param String key
     * @param State state
     * @throws SQLException
     */
    public static void setState(Connection conn, String key, State state)
    throws SQLException{
      StringReader sr = null;
      try {
        PreparedStatement prep = conn.prepareStatement("MERGE INTO global(key, state) VALUES(?, ?)");
        prep.setString(1, key);
        sr = new StringReader(state.toXML());
        prep.setClob(2, sr);
        prep.execute();
      } finally {
        if (sr != null) {
          try {
            sr.close();
          } catch(Exception e) {}
        }
      }
    }
  }
}
