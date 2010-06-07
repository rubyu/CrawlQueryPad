
package com.blogspot.rubyug.crawlquerypad;

import java.sql.*;
import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtensionAPI {
  protected static Logger logger = LoggerFactory.getLogger(Downloader.class);

  String name = null;
  public ExtensionAPI(String name) {
    this.name = name;
  }
  public State getState() {
    Connection conn = null;
    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      //get schema
      conn = CrawlQueryPadView.connectionPool.getConnection();
      prep = conn.prepareStatement("SELECT * FROM api_state WHERE key=?");
      prep.setString(1, name);
      rs = prep.executeQuery();
      if (rs.next()) {
        return new State(rs.getString("state"));
      }
    } catch(Exception e) {
      logger.error( Utils.ThrowableToString(e) );
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
    return new State();
  }
  public boolean setState(State state) {
    Connection conn = null;
    PreparedStatement prep = null;
    StringReader sr = null;
    try {
      conn = CrawlQueryPadView.connectionPool.getConnection();
      prep = conn.prepareStatement("MERGE INTO api_state(key, state) VALUES(?, ?)");
      prep.setString(1, name);
      sr = new StringReader(state.toXML());
      prep.setClob(2, sr);
      prep.execute();
      return true;
    } catch (Exception e) {
      logger.error( Utils.ThrowableToString(e) );
    } finally {
      if (conn != null) {
        try {
          conn.close();
        } catch(Exception e) {}
      }
      if (sr != null) {
        try {
          sr.close();
        } catch(Exception e) {}
      }
    }
    return false;
  }

}
