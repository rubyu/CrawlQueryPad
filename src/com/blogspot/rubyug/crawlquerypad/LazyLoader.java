
package com.blogspot.rubyug.crawlquerypad;

import java.io.*;
import java.sql.*;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LazyLoader {
  /*
   * URLに対するレスポンスをロードするクラス
   * header, bodyそれぞれ、必要になった段階でロードする
   * 全てのリクエストをキャッシュする
   * 
   */
  protected static Logger logger = LoggerFactory.getLogger(LazyLoader.class);

  private Connection conn = null;
  private Integer id = null;
  private String fullUrl = null;

  private static int maxRetry = 0;
  public static void setMaxRetry(int n) {
    if (n < 0) {
      logger.warn("n < 0");
      return;
    }
    logger.info("maxRetry setted to " + n);
    maxRetry = n;
  }
  private Date lastDownload = new Date();
  private static int waitTime = 0; //msec
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
  public void setConnection(Connection conn) {
    this.conn = conn;
  }
  public Integer getId() {
    return this.id;
  }
  public String getUrl() {
    return (String)DomUtils.getSplitedByAnchor(this.fullUrl)[0];
  }
  public String getFullUrl() {
    return this.fullUrl;
  }
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
    } catch (SQLException e) {
      logger.error(Utils.ThrowableToString(e));
    } catch (UnsupportedEncodingException e) {
    } finally {
      if (null != rs) {
        try {
          rs.close();
        } catch (Exception e) {}
      }
    }
    return null;
  }
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
          long diff = lastDownload.getTime() + waitTime - (new Date()).getTime();
          if (0 < diff) { //残りwait時間がある
            logger.info("waiting " + diff + "msec");
            try {
              Thread.sleep(diff);
            } catch(Exception e) {}
          }
          lastDownload = new Date();
          
          Downloader dl = new Downloader(getUrl());
          //dl.run();
          State header = new State(dl.getHeader());
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
    return null;
  }
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
          long diff = lastDownload.getTime() + waitTime - (new Date()).getTime();
          if (0 < diff) { //残りwait時間がある
            logger.info("waiting " + diff + "msec");
            try {
              Thread.sleep(diff);
            } catch(Exception e) {}
          }
          lastDownload = new Date();

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
  private String guessCharset() {
    InputStream in = null;
    try {
      in = getContent();
      State header = getHeader();
      if (in != null &&
          header != null) {
        return DomUtils.guessCharset(header, in);
      }
      return null;
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (Exception e) {}
      }
    }
  }
  public String getContentString() {
    InputStream in = null;
    try {
      in = getContent();
      String charset = guessCharset();
      if (in != null &&
          charset != null) {
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
      if (in != null &&
          charset != null) {
        String title = DomUtils.extractText(in, charset, null);
        if (null == title) {
          title = "";
        }
        setTitle(getUrl(), title);
        return title;
      }
    } catch (SQLException e) {
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
      if (in != null &&
          charset != null) {
        StringWriter sw = new StringWriter();
        WhitespaceFilterWriter writer = new WhitespaceFilterWriter(sw);
        DomUtils.extractText(in, charset, writer);
        String text = sw.toString();
        setText(getUrl(), text);
        return text;
      }
    } catch (SQLException e) {
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
      logger.debug("State saved");
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
      logger.debug("Header saved");
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
      logger.debug("Content saved");
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
    logger.debug("Title saved");
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
    logger.debug("Text saved");
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
