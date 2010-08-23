
package com.blogspot.rubyug.crawlquerypad;

import com.devx.io.*;

import java.sql.*;
import java.io.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Jython拡張の為のAPIを提供するクラス。
 * @author rubyu <@ruby_U>
 */
public class ExtensionAPI {
  protected static Logger logger = LoggerFactory.getLogger(Downloader.class);

  private Map data = new HashMap();
  private String name = null;
  /**
   * 拡張の名前を与え、それを保持するクラスを返す。
   * @param name
   */
  public ExtensionAPI(String name) {
    this.name = name;
  }
  /**
   * インスタンスのデータ保持用マップを返す。
   * ”このセッションで”拡張に渡すべきデータを格納する。
   * @return
   */
  public Map getData() {
    return data;
  }
  /**
   * テンポラリファイルを生成する。
   * @return
   * @throws IOException
   */
  public File createTemporaryFile()
  throws IOException {
    return TempFileManager.createTempFile(name, null);
  }
  /**
   * 拡張子を指定し、テンポラリファイルを生成する。
   * @param ext
   * @return
   * @throws IOException
   */
  public File createTemporaryFile(String ext)
  throws IOException {
    return TempFileManager.createTempFile(name, "." + ext);
  }
  /**
   * ”永続する”データを格納するStateオブジェクトを返す。
   * @return
   */
  public State getState() {
    Connection conn = null;
    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      //get schema
      conn = DB.getConnection();
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
  /**
   * 与えられたStateオブジェクトをデータベースに保存する。
   * @param state
   * @return
   */
  public boolean setState(State state) {
    Connection conn = null;
    PreparedStatement prep = null;
    StringReader sr = null;
    try {
      conn = DB.getConnection();
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
