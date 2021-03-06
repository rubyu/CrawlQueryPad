
package com.blogspot.rubyug.crawlquerypad;

import java.io.*;
import java.sql.*;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * URLに対するレスポンスをロードするクラス。
 * header, bodyそれぞれ、必要になった段階でロードする。
 * 全てのリクエストをキャッシュする。
 * @author rubyu <@ruby_U>
 */
public class LazyLoader {
  protected static Logger logger = LoggerFactory.getLogger(LazyLoader.class);

  private Connection conn = null;
  private Integer id = null;
  private String fullUrl = null;

  private static int maxRetry = 0;
  /**
   * ダウンロードに失敗した際のリトライの最大回数を設定する。
   * @param n
   */
  public static void setMaxRetry(int n) {
    if (n < 0) {
      logger.warn("n < 0");
      return;
    }
    logger.info("maxRetry setted to " + n);
    maxRetry = n;
  }
  private static int waitTime = 0; //msec
  /**
   * ダウンロード後のウェイトを設定する。
   * @param n
   */
  public static void setWait(int n) {
    if (n < 0) {
      logger.warn("n < 0");
      return;
    }
    logger.info("Wait after download setted to " + n);
    waitTime = n * 1000;
  }
  public LazyLoader(Connection conn, int id, String fullUrl) {
    this.conn = conn;
    this.id = id;
    this.fullUrl = fullUrl;
    try {
      createCache(getUrl(), new State());
    } catch (SQLException e) {
      //IGNORE
    }
  }
  /**
   * loaderがDBへのアクセスに使用するConnectionを設定する。
   * @param conn
   */
  public void setConnection(Connection conn) {
    this.conn = conn;
  }
  /**
   * LazyLoaderManagerで一意に割り振られたIDを返す。
   * @return
   */
  public Integer getId() {
    return this.id;
  }
  /**
   * アンカーを除いたURLを返す。
   * @return
   */
  public String getUrl() {
    return (String)DomUtils.getSplitedByAnchor(this.fullUrl)[0];
  }
  /**
   * URLを返す。
   * @return
   */
  public String getFullUrl() {
    return this.fullUrl;
  }
  /**
   * loaderの状態を格納したStateを取得する。
   * 必ずStateを返す。nullは返さない。
   * @return
   */
  public State getState() {
    logger.debug("getState: " + getFullUrl());
    ResultSet rs = null;
    try {
      rs = getResultSet(getUrl());
      rs.next();
      String ret = rs.getString("state");
      if (null != ret) {
        return new State(ret);
      }
    } catch (Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (null != rs) {
        try {
          rs.close();
        } catch (Exception e) {}
      }
    }
    return new State();
  }
  /**
   * ヘッダ情報を格納したStateを取得する。
   * 必ずStateを返す。nullは返さない。
   * @return
   */
  public State getHeader() {
    logger.debug("getHeader: " + getFullUrl());
    ResultSet rs = null;
    try {
      rs = getResultSet(getUrl());
      rs.next();
      String ret = rs.getString("header");
      if (null != ret) {
        logger.debug("use cache");
        return new State(ret);
      }
      State state = getState();
      long fail = state.getFirstOr("fail_on_getheader", 0);
      while (true) {
        if (maxRetry < fail) {
          logger.debug("fail(" + fail + ") exceeds maxRetry(" + maxRetry + ")");
          break;
        }
        logger.debug("fail(" + fail + ") belows maxRetry(" + maxRetry + ")");
        try {
          if (waitTime > 0) {
            logger.info("waiting " + waitTime + " msec ...");
            Thread.sleep(waitTime);
          }
          Downloader dl = new Downloader(getUrl());
          //dl.run();
          State header = new State(dl.getHeader());
          String responseCode = header.getFirstOr(null, "");
          String location     = header.getFirstOr("location", null);
          if (location == null && -1 == responseCode.indexOf("200")) {
            throw new IllegalStateException("HTTP Status Code of the response is not 200");
          }
          setHeader(getUrl(), header);
          return header;
          
        } catch (Exception e) {
          logger.debug("download failed: " + Utils.ThrowableToString(e));
          fail++;
          state.set("fail_on_getheader", fail);
          setState(getUrl(), state);
        }
      }
    } catch (Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (null != rs) {
        try {
          rs.close();
        } catch (Exception e) {}
      }
    }
    return new State(); //not return null
  }
  /**
   * loaderのコンテンツを返す。
   * 何らかの原因でコンテンツの取得に失敗した際はnullを返す。
   * @return
   */
  public InputStream getContent() {
    logger.debug("getContent: " + getFullUrl());
    ResultSet rs = null;
    try {
      rs = getResultSet(getUrl());
      rs.next();
      InputStream in = rs.getBinaryStream("content");
      if (null != in) {
        logger.debug("use cache");
        return in;
      }
      State state = getState();
      long fail = state.getFirstOr("fail_on_getcontent", 0);
      while (true) {
        if (maxRetry < fail) {
          logger.debug("fail(" + fail + ") exceeds maxRetry(" + maxRetry + ")");
          break;
        }
        logger.debug("fail(" + fail + ") belows maxRetry(" + maxRetry + ")");
        try {
          if (waitTime > 0) {
            logger.info("waiting " + waitTime + " msec ...");
            Thread.sleep(waitTime);
          }
          Downloader dl = new Downloader(getUrl());
          if (null != rs.getString("header")) { //headerが存在しないならついでに格納しておく
            State header = new State(dl.getHeader());
            setHeader(getUrl(), header);
          }
          dl.run();
          if (dl.isFailed()) {
            throw new Exception("error occured while dl.run()");
          }
          setContent(getUrl(), dl.getContentStream());
          return dl.getContentStream();

        } catch (Exception e) {
          logger.debug("download failed: " + Utils.ThrowableToString(e));
          fail++;
          state.set("fail_on_getcontent", fail);
          setState(getUrl(), state);
        }
      }
    } catch (Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (null != rs) {
        try {
          rs.close();
        } catch (Exception e) {}
      }
    }
    return null;
  }
  /**
   * 文字コードを推定し取得する。
   * 必ずStringを返す。nullは返さない。
   * デフォルト値は"us-ascii"。
   * @return
   */
  public String guessCharset() {
    InputStream in = null;
    try {
      in           = getContent();
      State header = getHeader();
      return DomUtils.guessCharset(header, in);
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (Exception e) {}
      }
    }
  }
  /**
   * getContent()の内容をStringで取得する。
   * 必ずStringを返す。nullは返さない。
   * @return
   */
  public String getContentString() {
    InputStream in = null;
    try {
      in = getContent();
      String charset = guessCharset();
      if (in != null) {
        return Utils.InputStreamToString(in, charset);
      }
    } catch (IOException e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (Exception e) {}
      }
    }
    return null;
  }
  /**
   * タイトルを取得する。
   * 必ずStringを返す。nullは返さない。
   * @return
   */
  public String getTitle() {
    logger.debug("getTitle: " + getFullUrl());
    ResultSet rs = null;
    InputStream in = null;
    try {
      rs = getResultSet(getUrl());
      rs.next();
      String ret = rs.getString("title");
      if (null != ret) {
        return ret;
      }
      in = getContent();
      String charset = guessCharset();
      if (in != null) {
        String title = DomUtils.extractText(in, charset, null);
        if (null == title) {
          title = "";
        }
        setTitle(getUrl(), title);
        return title;
      }
    } catch (Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (Exception e) {}
      }
    }
    return "";
  }
  /**
   * テキストを取得する。
   * 必ずStringを返す。nullは返さない。
   * @return
   */
  public String getText() {
    logger.debug("getText: " + getFullUrl());
    ResultSet rs = null;
    InputStream in = null;
    try {
      rs = getResultSet(getUrl());
      rs.next();
      String ret = rs.getString("text");
      if (null != ret) {
        return ret;
      }
      in = getContent();
      String charset = guessCharset();
      if (in != null) {
        StringWriter sw = new StringWriter();
        WhitespaceFilterWriter writer = new WhitespaceFilterWriter(sw);
        DomUtils.extractText(in, charset, writer);
        String text = sw.toString();
        setText(getUrl(), text);
        return text;
      }
    } catch (Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (Exception e) {}
      }
    }
    return "";
  }
  private void createCache(String url, State state)
  throws SQLException {
    PreparedStatement prep = null;
    StringReader sr = null;
    try {
      prep = conn.prepareStatement(
          "INSERT INTO response_cache(url, state) " +
          "VALUES(?, ?) "
          );
      prep.setString(1, url);
      sr = new StringReader(state.toXML());
      prep.setClob(2, sr);
      prep.execute();
      logger.info("cache created: " + url);
    } finally {
      if (sr != null) {
        try {
          sr.close();
        } catch (Exception e) {}
      }
    }
  }
  private void setState(String url, State state)
  throws SQLException {
    PreparedStatement prep = null;
    StringReader sr = null;
    try {
      prep = conn.prepareStatement(
          "UPDATE response_cache " +
          "SET state = ? " +
          "WHERE url = ? "
          );
      sr = new StringReader(state.toXML());
      prep.setClob(1, sr);
      prep.setString(2, url);
      prep.execute();
      logger.debug("state saved");
    } finally {
      if (sr != null) {
        try {
          sr.close();
        } catch (Exception e) {}
      }
    }
  }
  private void setHeader(String url, State header)
  throws SQLException {
    PreparedStatement prep = null;
    StringReader sr = null;
    try {
      prep = conn.prepareStatement(
          "UPDATE response_cache " +
          "SET header = ? " +
          "WHERE url = ? "
          );
      sr = new StringReader(header.toXML());
      prep.setClob(1, sr);
      prep.setString(2, url);
      prep.execute();
      logger.debug("header saved");
    } finally {
      if (sr != null) {
        try {
          sr.close();
        } catch (Exception e) {}
      }
    }
  }
  private void setContent(String url, InputStream content)
  throws SQLException {
    PreparedStatement prep = null;
    try {
      prep = conn.prepareStatement(
          "UPDATE response_cache " +
          "SET content = ? " +
          "WHERE url = ? "
          );
      prep.setBinaryStream(1, content);
      prep.setString(2, url);
      prep.execute();
      logger.debug("content saved");
    } finally {
      if (content != null) {
        try {
          content.close();
        } catch (Exception e) {}
      }
    }
  }
  private void setTitle(String url, String title)
  throws SQLException {
    PreparedStatement prep = null;
    prep = conn.prepareStatement(
        "UPDATE response_cache " +
        "SET title = ? " +
        "WHERE url = ? "
        );
    prep.setString(1, title);
    prep.setString(2, url);
    prep.execute();
    logger.debug("title saved");
  }
  private void setText(String url, String text)
  throws SQLException {
    PreparedStatement prep = null;
    prep = conn.prepareStatement(
        "UPDATE response_cache " +
        "SET text = ? " +
        "WHERE url = ? "
        );
    prep.setString(1, text);
    prep.setString(2, url);
    prep.execute();
    logger.debug("text saved");
  }
  private ResultSet getResultSet(String url)
  throws SQLException {
    PreparedStatement prep = conn.prepareStatement(
        "SELECT * FROM response_cache " +
        "WHERE url = ? "
        );
    prep.setString(1, url);
    return prep.executeQuery();
  }
}
