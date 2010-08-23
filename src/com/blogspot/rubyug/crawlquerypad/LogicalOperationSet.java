
package com.blogspot.rubyug.crawlquerypad;

import com.blogspot.rubyug.crawlquerypad.condition.*;
import java.sql.*;
import java.util.*;
import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogicalOperationSet extends HashSet<Integer> {
  protected static Logger logger = LoggerFactory.getLogger(LogicalOperationSet.class);

  LazyLoaderManager manager = null;
  CrawlQueryPadView.CrawlExcecuteWorker worker = null;
  public LogicalOperationSet(LazyLoaderManager manager, CrawlQueryPadView.CrawlExcecuteWorker worker) {
    super();
    this.manager = manager;
    this.worker = worker;
  }
  public LazyLoaderManager getManager() {
    return this.manager;
  }
  public CrawlQueryPadView.CrawlExcecuteWorker getWorker() {
    return this.worker;
  }
  public LogicalOperationSet getCrawled(int depth, List<Cond> conds) {
    logger.debug(Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
    LogicalOperationSet newSet     = this;
    LogicalOperationSet currentSet = this;
    LogicalOperationSet tempSet    = null;
    
    for (int i=0; i < depth; i++) {
      tempSet = new LogicalOperationSet(manager, worker);

      int current_i = 0;
      int current_size = currentSet.size();
      for (int id : currentSet) {
        current_i++;
        LazyLoader loader = manager.getLazyLoader(id);
        logger.debug(
          "Crawling " +
            "Depth " + (i+1) + "/" + depth + ", " +
            "Item " + current_i + "/" + current_size + " " +
            "Id " + id + ": " + loader.getUrl()
          );
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
          if (null != in) {
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
          } else {
            logger.debug("in is null");
          }
        } catch(Exception e) {
          logger.error(Utils.ThrowableToString(e));
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

      logger.debug("size: " + tempSet.size());

      /*
       * 以下でのフィルタは、Locationでの連続した遷移過程のURLを対象としない。
       * 処理速度のため、全てのアイテムにgetContent()を行うような処理もしない。
       * （frame src であれば自身との置換、が正しいが、外部リンク扱いしている）
       * 
       * 以下では
       * ・条件でのフィルタ
       * ・レスポンスコードでのフィルタ
       * ・Content-Typeでのフィルタ
       * ・（リダイレクトが発生した際のためもう一度）条件でのフィルタ
       * を行っている。
       * Setの同一判定を行えば最後の処理は軽減できる。
       */
      
      logger.debug("Cond Filter");
      tempSet = tempSet.getCondsFiltered(conds);
      logger.debug("size: " + tempSet.size());

      if (worker.isCancelled()) {
        logger.debug("worker is Cancelled");
        break;
      }

      logger.debug("ResponseCode Filter");
      tempSet = tempSet.getResponseCodeFiltered();
      logger.debug("size: " + tempSet.size());

      if (worker.isCancelled()) {
        logger.debug("worker is Cancelled");
        break;
      }

      logger.debug("ContentType Filter");
      tempSet = tempSet.getContentTypeFiltered();
      logger.debug("size: " + tempSet.size());

      if (worker.isCancelled()) {
        logger.debug("worker is Cancelled");
        break;
      }

      logger.debug("Cond Filter");
      tempSet = tempSet.getCondsFiltered(conds);
      logger.debug("size: " + tempSet.size());

      if (worker.isCancelled()) {
        logger.debug("worker is Cancelled");
        break;
      }
      
      currentSet = tempSet;
      newSet = newSet.getUnion(tempSet);
    }
    return newSet;
  }
  public LogicalOperationSet getResponseCodeFiltered() {
    logger.debug(Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
    LogicalOperationSet newSet = new LogicalOperationSet(manager, worker);
    boolean hasRedirect = false;
    int i = 0;
    for (Integer id : this) {
      if (worker.isCancelled()) {
        break;
      }
      i++;
      worker.publish(
        "Filtering by ResponseCode " + i + "/" + this.size()
        );
      LazyLoader loader = manager.getLazyLoader(id);
      logger.debug("checking: " + loader.getUrl());
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
          logger.debug("redirected -> " + location);
        } else {
          logger.debug("has location but is invalid");
        }
        continue;
      }
      if (-1 != responseCode.indexOf("200")) { //OK
        newSet.add(id);
        logger.debug("passed");
        continue;
      } else {
        logger.debug("not passed");
      }
    }
    if (hasRedirect) {
      logger.debug("recursive filtering");
      newSet = newSet.getResponseCodeFiltered(); //LOOP
    }
    return newSet;
  }

  public LogicalOperationSet getContentTypeFiltered() {
    logger.debug(Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
    LogicalOperationSet newSet = new LogicalOperationSet(manager, worker);
    int i = 0;
    for (Integer id : this) {
      if (worker.isCancelled()) {
        break;
      }
      i++;
      worker.publish(
        "Filtering by ContentType " + i + "/" + this.size()
        );
      LazyLoader loader = manager.getLazyLoader(id);
      logger.debug("checking: " + loader.getUrl());
      State header = loader.getHeader();
      String contentType = header.getFirstOr("content-type", null);
      if (//contentType == null ||
          -1 != contentType.indexOf("htm") ||
          -1 != contentType.indexOf("text/plain") ) {
        newSet.add(id);
        logger.debug("passed");
      } else {
        logger.debug("not passed");
      }
    }
    return newSet;
  }

  public LogicalOperationSet getCondsFiltered(List<Cond> conds) {
    logger.debug(Thread.currentThread().getStackTrace()[1].getMethodName() + "()");
    LogicalOperationSet newSet = new LogicalOperationSet(manager, worker);
    int i = 0;
    for (Integer id : this) {
      if (worker.isCancelled()) {
        break;
      }
      i++;
      worker.publish(
        "Filtering by Condition " + i + "/" + this.size()
        );
      LazyLoader loader = manager.getLazyLoader(id);
      logger.debug("checking: " + loader.getUrl());
      boolean match = true;
      for (Cond cond : conds) {
        if (cond.test(loader)) {
          logger.debug("match");
        } else {
          logger.debug("not match");
          match = false;
          break;
        }
      }
      if (match) {
        newSet.add(id);
        logger.debug("passed");
      } else {
        logger.debug("not passed");
      }
    }
    return newSet;
  }
  
  public LogicalOperationSet getDifference(LogicalOperationSet loSet) {
    LogicalOperationSet newSet = new LogicalOperationSet(manager, worker);
    for (Integer id : this) {
      newSet.add(id);
    }
    for (Integer id : loSet) {
      newSet.remove(id);
    }
    return newSet;
  }
  public LogicalOperationSet getIntersection(LogicalOperationSet loSet) {
    LogicalOperationSet newSet = new LogicalOperationSet(manager, worker);
    for (Integer id: loSet) {
      if (this.contains(id)) {
        newSet.add(id);
      }
    }
    return newSet;
  }
  public LogicalOperationSet getSymmetricDifference(LogicalOperationSet loSet) {
    LogicalOperationSet newSet = new LogicalOperationSet(manager, worker);
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
    LogicalOperationSet newSet = new LogicalOperationSet(manager, worker);
    for (Integer id : this) {
      newSet.add(id);
    }
    for (Integer id : loSet) {
      newSet.add(id);
    }
    return newSet;
  }

}
