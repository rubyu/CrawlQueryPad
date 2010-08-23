
package com.blogspot.rubyug.crawlquerypad;

import java.util.*;
import java.sql.*;

/**
 * LazyLoaderを管理するクラス。
 * getLoader(URL) が適切なLazyLoaderを返す。
 * URLはナンバリングされ、（アンカをのぞく）URLに対し、一意に割り当てられる。
 * @author rubyu <@ruby_U>
 */
public class LazyLoaderManager {
  Connection conn = null;
  public LazyLoaderManager(Connection conn) {
    this.conn = conn;
  }
  private Set<String>  urlSet  = new HashSet<String>();
  private List<String> urlList = new ArrayList<String>();
  /**
   * URLからIDへの変換を行う。
   * @param fullUrl
   * @return
   */
  private int getIndexOfUrl(String fullUrl) {
    if (urlSet.contains(fullUrl)) {
      return urlList.indexOf(fullUrl);
    } else {
      urlSet.add(fullUrl);
      urlList.add(fullUrl);
      return urlList.indexOf(fullUrl);
    }
  }
  /**
   * URLを登録し、IDを返す。
   * @param fullUrl
   * @return
   */
  public int register(String fullUrl) {
    return getIndexOfUrl(fullUrl);
  }
  /**
   * 与えられたURLと結びついたloaderを返す。
   * @param fullUrl
   * @return
   */
  public LazyLoader getLazyLoader(String fullUrl) {
    int id = getIndexOfUrl(fullUrl);
    return new LazyLoader(conn, id, fullUrl);
  }
  /**
   * 与えられたIDと結びついたloaderを返す。
   * @param id
   * @return
   */
  public LazyLoader getLazyLoader(int id) {
    String fullUrl = urlList.get(id);
    return new LazyLoader(conn, id, fullUrl);
  }
}
