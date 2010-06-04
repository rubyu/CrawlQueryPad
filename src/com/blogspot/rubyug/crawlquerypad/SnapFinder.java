
package com.blogspot.rubyug.crawlquerypad;

import java.util.*;
import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class of converter that converts URL to Path.
 */
  public class SnapFinder {
    protected static Logger logger = LoggerFactory.getLogger(SnapFinder.class);
    Map<String, Long> idMap = new HashMap();
    private Connection conn = null;
    private Long jobId = null;
    private String internalPrefix = null;
    private String internalPostfix = null;
    private String externalPrefix = null;
    private String externalPostfix = null;
    /**
     * Dummy constractor.
     */
    public SnapFinder() {
    }
    /**
     * Constractor of SnapFinder. 
     * Requires instance of Connection, id of job and internal/external
     * prefix and postfix.
     * @param Connection conn
     * @param Long parent
     * @param String internalPrefix
     * @param String internalPostfix
     * @param String externalPrefix
     * @param String externalPostfix
     */
    public SnapFinder(Connection conn, Long parent,
            String internalPrefix, String internalPostfix, String externalPrefix, String externalPostfix) {
      this.conn = conn;
      jobId = parent;
      this.internalPrefix = internalPrefix;
      this.internalPostfix = internalPostfix;
      this.externalPrefix = externalPrefix;
      this.externalPostfix = externalPostfix;
    }
    /**
     * Finds a snap that has given URL and returns boolean of whether
     * snap exists or not.
     * @param String url
     * @return boolean
     */
    public boolean find(String url) {
      if (idMap.containsKey(url)) {
        return true;
      }
      if (jobId == null || conn == null) {
        return false;
      }
      PreparedStatement prep = null;
      ResultSet rs = null;
      try {
        prep = conn.prepareStatement(
                "SELECT * FROM snap WHERE job_id = ? AND url = ?"
                );
        prep.setLong(1, jobId);
        prep.setString(2, url);
        rs = prep.executeQuery();
        if (rs.next()) {
          long id = rs.getLong("id");
          idMap.put(url, id);
          return true;
        }
      } catch(Exception e) {
        logger.error(Utils.ThrowableToString(e));
      } finally {
        if (rs != null) {
          try {
            rs.close();
          } catch(Exception e) {}
        }
      }
      return false;
    }
    /**
     * Returns id of snap from given URL.
     * Returns null, if not found.
     * @param String url
     * @return Long
     */
    public Long getId(String url) {
      return idMap.get(url);
    }
    /**
     * Returns internal path from given URL.
     * @param String url
     * @return String
     */
    public String getInternalPath(String url) {
      return internalPrefix + getId(url) + internalPostfix;
    }
    /**
     * Returns external path from given URL.
     * @param String url
     * @return String
     */
    public String getExternalPath(String url) {
      return externalPrefix + getId(url) + externalPostfix;
    }
  }
