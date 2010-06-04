
package com.blogspot.rubyug.crawlquerypad;

import java.io.*;
import java.sql.*;

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
  
  private Integer id = null;
  private String fullUrl = null;

  public LazyLoader(Connection conn, int id, String fullUrl) {
    this.id = id;
    this.fullUrl = fullUrl;
    try {
      createCache(conn, getUrl(), new State());
    } catch (SQLException e) {
      //IGNORE
    }
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
  public State getState(Connection conn) {
    ResultSet rs = null;
    try {
      rs = getResultSet(conn, getUrl());
      rs.next();
      String ret = rs.getString("state");
      if (null != ret) {
        return new State(ret);
      }
    } catch (SQLException e) {
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
  public State getHeader(Connection conn) {
    ResultSet rs = null;
    try {
      rs = getResultSet(conn, getUrl());
      rs.next();
      String ret = rs.getString("header");
      if (null != ret) {
        return new State(ret);
      }
      State state = getState(conn);
      long fail = state.getFirstOr("fail_on_getheader", 0);
      if (0 == fail) {
        try {
          Downloader dl = new Downloader(getUrl());
          //dl.run();
          State header = new State(dl.getHeader());
          setHeader(conn, getUrl(), header);
          return header;
          
        } catch (IOException e) {
          logger.debug("download fail on getheader: " + Utils.ThrowableToString(e));
          fail++;
          state.set("fail_on_getheader", fail);
          setState(conn, getUrl(), state);
        }
      }
    } catch (SQLException e) {
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
  public InputStream getContent(Connection conn) {
    ResultSet rs = null;
    try {
      rs = getResultSet(conn, getUrl());
      rs.next();
      InputStream in = rs.getBinaryStream("content");
      if (null != in) {
        return in;
      }
      State state = getState(conn);
      long fail = state.getFirstOr("fail_on_getcontent", 0);
      if (0 == fail) {
        try {
          Downloader dl = new Downloader(getUrl());
          dl.run();
          setContent(conn, getUrl(), dl.getContentStream());
          return dl.getContentStream();

        } catch (IOException e) {
          logger.debug("download fail on getcontent: " + Utils.ThrowableToString(e));
          fail++;
          state.set("fail_on_getcontent", fail);
          setState(conn, getUrl(), state);
        }
      }
    } catch (SQLException e) {
    } finally {
      if (null != rs) {
        try {
          rs.close();
        } catch (Exception e) {}
      }
    }
    return null;
  }
  private String guessCharset(Connection conn) {
    InputStream in = null;
    try {
      in = getContent(conn);
      State header = getHeader(conn);
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
  public String getContentString(Connection conn) {
    InputStream in = null;
    try {
      in = getContent(conn);
      String charset = guessCharset(conn);
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
  public String getTitle(Connection conn) {
    InputStream in = null;
    try {
      in = getContent(conn);
      String charset = guessCharset(conn);
      if (in != null &&
          charset != null) {
        return DomUtils.extractText(in, charset, null);
      }
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (Exception e) {}
      }
    }
    return null;
  }
  public String getText(Connection conn) {
    InputStream in = null;
    try {
      in = getContent(conn);
      String charset = guessCharset(conn);
      if (in != null &&
          charset != null) {
        StringWriter writer = new StringWriter();
        DomUtils.extractText(in, charset, writer);
        return writer.toString();
      }
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (Exception e) {}
      }
    }
    return null;
  }
  private void createCache(Connection conn, String url, State state)
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
  private void setState(Connection conn, String url, State state)
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
    } finally {
      if (sr != null) {
        try {
          sr.close();
        } catch (Exception e) {}
      }
    }
  }
  private void setHeader(Connection conn, String url, State header)
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
    } finally {
      if (sr != null) {
        try {
          sr.close();
        } catch (Exception e) {}
      }
    }
  }
  private void setContent(Connection conn, String url, InputStream content)
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
    } finally {
      if (content != null) {
        try {
          content.close();
        } catch (Exception e) {}
      }
    }
  }
  private ResultSet getResultSet(Connection conn, String url)
  throws SQLException {
    PreparedStatement prep = conn.prepareStatement(
        "SELECT * FROM response_cache " +
        "WHERE url = ? "
        );
    prep.setString(1, url);
    return prep.executeQuery();
  }
}
