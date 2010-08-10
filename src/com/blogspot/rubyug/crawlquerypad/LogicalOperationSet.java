
package com.blogspot.rubyug.crawlquerypad;

import com.blogspot.rubyug.crawlquerypad.condition.*;
import java.sql.*;
import java.util.*;
import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogicalOperationSet extends HashSet<Integer> {
  protected static Logger logger = LoggerFactory.getLogger(LogicalOperationSet.class);

  Connection conn = null;
  LazyLoaderManager manager = null;
  public LogicalOperationSet(Connection conn, LazyLoaderManager manager) {
    super();
    this.conn = conn;
    this.manager = manager;
  }
  public Connection getConnection() {
    return this.conn;
  }
  public LazyLoaderManager getManager() {
    return this.manager;
  }
  public LogicalOperationSet getCrawled(int depth, List<Cond> conds, CrawlQueryPadView.CrawlExcecuteWorker worker) {
    LogicalOperationSet newSet     = this;
    LogicalOperationSet currentSet = this;
    LogicalOperationSet tempSet    = null;
    
    for (int i=0; i < depth; i++) {
      tempSet = new LogicalOperationSet(getConnection(), getManager());

      int current_i = 0;
      int current_size = currentSet.size();
      for (int id : currentSet) {
        current_i++;
        LazyLoader loader = manager.getLazyLoader(conn, id);
        worker.publish(
          "Crawling " +
            "Depth " + (i+1) + "/" + depth + ", " +
            "Item " + current_i + "/" + current_size + " " +
            "Id " + id + ": " + loader.getUrl()
          );

        if (worker.isCancelled()) {
          break;
        }

        InputStream in = null;
        try {
          in           = loader.getContent();
          State header = loader.getHeader();
          String charset = DomUtils.guessCharset(header, in);
          try {
            in.close();
          } catch (Exception e) {}

          in = loader.getContent();
          List<String> externalNotFound = DomUtils.extractHtmlLinks(loader.getUrl(), in, charset);
          for (String url : externalNotFound) { //isValidURLs
            int newId = manager.register(url);
            if (!newSet.contains(newId)) { //新しいもののみ
              logger.debug("extracted: " + url);
              tempSet.add(newId);
            }
          }
        } finally {
          if (null != in) {
            try {
              in.close();
            } catch (Exception e) {}
          }
        }
      }

      if (worker.isCancelled()) {
        logger.debug("worker is Cancelled");
        break;
      }

      logger.debug("size :" + tempSet.size());

      /*
       * 以下のフィルタの順序は、Locationを持つような、ブラウザからは
       * 意識されないURLを経由できるようにした方がいいのではないかという点で
       * 考慮の余地がある。
       * 現在は、処理速度のため、
       * ・条件でのフィルタ
       * ・レスポンスコードでのフィルタ
       * ・Content-Typeでのフィルタ
       * ・（リダイレクトが発生した際のためもう一度）条件でのフィルタ
       * を行っている。
       * Setの同一判定を行えば最後の処理は軽減できる。
       */
      worker.publish(
        "Crawling " +
          "Depth " + (i+1) + "/" + depth +
          "Filtering by condition(1)  1/4"
        );
      logger.debug("Cond Filter");
      tempSet = tempSet.getCondsFiltered(conds);
      logger.debug("size :" + tempSet.size());

      worker.publish(
        "Crawling " +
          "Depth " + (i+1) + "/" + depth +
          "Filtering by response code  2/4"
        );
      logger.debug("ResponseCode Filter");
      tempSet = tempSet.getResponseCodeFiltered();
      logger.debug("size :" + tempSet.size());

      worker.publish(
        "Crawling " +
          "Depth " + (i+1) + "/" + depth +
          "Filtering by conent type  3/4"
        );
      logger.debug("ContentType Filter");
      tempSet = tempSet.getContentTypeFiltered();
      logger.debug("size :" + tempSet.size());

      worker.publish(
        "Crawling " +
          "Depth " + (i+1) + "/" + depth +
          "Filtering by condition(2)  4/4"
        );
      logger.debug("Cond Filter");
      tempSet = tempSet.getCondsFiltered(conds);
      logger.debug("size :" + tempSet.size());

      currentSet = tempSet;
      newSet = newSet.getUnion(tempSet);
    }
    return newSet;
  }
  public LogicalOperationSet getResponseCodeFiltered() {
    LogicalOperationSet newSet = new LogicalOperationSet(getConnection(), getManager());
    boolean hasRedirect = false;
    for (Integer id : this) {
      LazyLoader loader = manager.getLazyLoader(conn, id);
      State header = loader.getHeader();
      String responseCode = header.getFirstOr(null, "");
      String location     = header.getFirstOr("location", null);
      logger.debug("response code: " + responseCode);
      logger.debug("location: " + location);
      if (location != null) {
        if (DomUtils.isValidURL(location)) {
          hasRedirect = true;
          int newId = manager.register(location);
          newSet.add(newId);
          logger.debug("redirect: " + loader.getUrl() + " -> " + location);
        }
        continue;
      }
      if (-1 != responseCode.indexOf("200")) { //OK
        newSet.add(id);
        logger.debug("url: " + loader.getUrl());
        continue;
      }
    }
    if (hasRedirect) {
      newSet = newSet.getResponseCodeFiltered(); //LOOP
    }
    return newSet;
  }

  public LogicalOperationSet getContentTypeFiltered() {
    LogicalOperationSet newSet = new LogicalOperationSet(getConnection(), getManager());
    for (Integer id : this) {
      LazyLoader loader = manager.getLazyLoader(conn, id);
      State header = loader.getHeader();
      String contentType = header.getFirstOr("content-type", null);
      if (//contentType == null ||
          -1 != contentType.indexOf("htm") ||
          -1 != contentType.indexOf("text/plain") ) {
        newSet.add(id);
      }
    }
    return newSet;
  }

  public LogicalOperationSet getCondsFiltered(List<Cond> conds) {
    LogicalOperationSet newSet = new LogicalOperationSet(getConnection(), getManager());
    for (Integer id : this) {
      LazyLoader loader = manager.getLazyLoader(conn, id);
      boolean match = true;
      for (Cond cond : conds) {
        if (cond.test(conn, loader)) {
          logger.debug("match");
        } else {
          logger.debug("not match");
          match = false;
          break;
        }
      }
      if (match) {
        newSet.add(id);
      }
    }
    return newSet;
  }
  
  public LogicalOperationSet getDifference(LogicalOperationSet loSet) {
    LogicalOperationSet newSet = new LogicalOperationSet(getConnection(), getManager());
    for (Integer id : this) {
      newSet.add(id);
    }
    for (Integer id : loSet) {
      newSet.remove(id);
    }
    return newSet;
  }
  public LogicalOperationSet getIntersection(LogicalOperationSet loSet) {
    LogicalOperationSet newSet = new LogicalOperationSet(getConnection(), getManager());
    for (Integer id: loSet) {
      if (this.contains(id)) {
        newSet.add(id);
      }
    }
    return newSet;
  }
  public LogicalOperationSet getSymmetricDifference(LogicalOperationSet loSet) {
    LogicalOperationSet newSet = new LogicalOperationSet(getConnection(), getManager());
    for (Integer id : this) {
      if (!loSet.contains(id)) {
        newSet.add(id);
      }
    }
    for (Integer id : loSet) {
      if (!this.contains(id)) {
        newSet.add(id);
      }
    }
    return newSet;
  }
  public LogicalOperationSet getUnion(LogicalOperationSet loSet) {
    LogicalOperationSet newSet = new LogicalOperationSet(getConnection(), getManager());
    for (Integer id : this) {
      newSet.add(id);
    }
    for (Integer id : loSet) {
      newSet.add(id);
    }
    return newSet;
  }

}
