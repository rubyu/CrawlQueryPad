
package com.blogspot.rubyug.crawlquerypad;

import java.util.*;
import java.sql.*;

public class LazyLoaderManager {
  /*
   * LazyLoaderを管理するクラス
   * getLoader(URL) が適切なLazyLoaderを返す。
   * URLはnumberingされ、（アンカをのぞく）URLに対し、一意に割り当てられる。
   */
  private void init(Connection conn)
  throws SQLException {
    Statement st = null;
    st = conn.createStatement();
    st.execute(
      "CREATE TABLE IF NOT EXISTS response_cache(" +
        "url VARCHAR PRIMARY KEY NOT NULL," +
        "state CLOB NOT NULL," +
        "header CLOB," +
        "content BLOB" +
      ");");
  }
  public LazyLoaderManager(Connection conn)
  throws SQLException {
    init(conn);
  }

  private Set<String>  urlSet  = new HashSet<String>();
  private List<String> urlList = new ArrayList<String>();

  private int getIndexOfUrl(String fullUrl) {
    if (urlSet.contains(fullUrl)) {
      return urlList.indexOf(fullUrl);
    } else {
      urlSet.add(fullUrl);
      urlList.add(fullUrl);
      return urlList.indexOf(fullUrl);
    }
  }
  public int register(String fullUrl) {
    return getIndexOfUrl(fullUrl);
  }
  public LazyLoader getLazyLoader(Connection conn, String fullUrl) {
    int id = getIndexOfUrl(fullUrl);
    return new LazyLoader(conn, id, fullUrl);
  }
  public LazyLoader getLazyLoader(Connection conn, int id) {
    String fullUrl = urlList.get(id);
    return new LazyLoader(conn, id, fullUrl);
  }
}
