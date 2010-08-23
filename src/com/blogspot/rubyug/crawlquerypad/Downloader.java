
package com.blogspot.rubyug.crawlquerypad;

import java.io.*;
import java.net.*;
import java.util.*;
import com.devx.io.TempFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ダウンロードを行うクラス。
 * 可能な限り、接続回数を減らすように実装している。
 * @author rubyu <@ruby_U>
 */
public class Downloader implements Runnable {
  protected static Logger logger = LoggerFactory.getLogger(Downloader.class);
  //
  private boolean  completed = false;
  private boolean  failed    = false;
  //
  private int contentLength  = -1;
  private int downloadedSize = 0;
  //
  private URL url         = null;
  private String protocol = null;
  private boolean isHttp  = false;
  //
  private URLConnection con = null;
  //
  private File file = null;
  //
  private Date startDate = null;
  private Date endDate   = null;
  //
  private Map<String,List<String>> header = null;
  /**
   * URLを与え、Downloaderインタンスを返す。
   */
  public Downloader(String url)
  throws java.io.IOException {
    this.url = new URL(url);
    protocol = this.url.getProtocol().toLowerCase();
    con      = this.url.openConnection();
    if (protocol.startsWith("http")) {
      logger.debug("protocol is http(s)");
      isHttp = true;
    }
    if (isHttp) {
      logger.debug("set Redirect: false");
      logger.debug("set request headers");
      HttpURLConnection httpConnection = (HttpURLConnection)con;
      httpConnection.setInstanceFollowRedirects(false);
      httpConnection.setRequestProperty("Accept", "application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
      httpConnection.setRequestProperty("Accept-Charset", "Shift_JIS,utf-8;q=0.7,*;q=0.3");
      httpConnection.setRequestProperty("Accept-Language", "ja,en-US;q=0.8,en;q=0.6");
      httpConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) AppleWebKit/532.5 (KHTML, like Gecko) Chrome/4.0.249.89 Safari/532.5");
    }
    file = TempFileManager.createTempFile("download", null);
  }
  /**
   * ダウンロードを実行する。
   * 完了していれば即時終了する。
   * ・ヘッダのキャッシュ
   * ・ダウンロード
   * ・コネクションのクローズ
   * の順に行う。
   */
  public void run() {
    if (completed) {
      return;
    }
    logger.debug("start download");
    //caching
    getHeader();
    
    InputStream in   = null;
    OutputStream out = null;
    startDate = new Date();
    try {
      contentLength = con.getContentLength();
      in = con.getInputStream();
      out = new FileOutputStream(file);
      byte[] buf = new byte[1024];
      int size;
      while ((size=in.read(buf)) != -1) {
        downloadedSize += size;
        out.write(buf, 0, size);
      }
      contentLength = downloadedSize;
      logger.debug("download completed");
    } catch(Exception e) {
      logger.error("download failed: " + Utils.ThrowableToString(e));
      failed = true;
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch(Exception e) {}
      }
      if (out != null) {
        try {
          out.close();
        } catch(Exception e) {}
      }
    }
    completed = true;
    endDate = new Date();
    if (isHttp) {
      logger.debug("disconnect");
      ((HttpURLConnection)con).disconnect();
    }
    con = null;
  }
  /**
   * クッキーを設定する。
   * @param cookie
   */
  public void setCookie(String cookie) {
    if (isHttp && !isCompleted()) {
      logger.debug("set cookie: " + cookie);
      HttpURLConnection httpConnection = (HttpURLConnection)con;
      httpConnection.setRequestProperty("Cookie", cookie);
    }
  }
  /**
   * 経過時間を取得する。
   * @return
   */
  public long getErapsedTime() {
    return endDate.getTime() - startDate.getTime();
  }
  /**
   * トラフィックレートを取得する。
   * 単位はkb/s。
   * @return
   */
  public long getTrafficRate() { // kb/s
    if (isCompleted() && !isFailed()) {
      try {
        long fileSize = getSize();
        long erapsed = getErapsedTime();
        if (0 < fileSize && 0 < erapsed) {
          return fileSize / erapsed;
        }
      } catch(Exception e){}
    }
    return 0;
  }
  /**
   * 進行状況を取得する。
   * @return
   */
  public float getProgress() {
    if (contentLength == -1) {
      return 0.0f;
    }
    if (downloadedSize <= contentLength) {
      return (float)downloadedSize / contentLength;
    }
    return 1.0f;
  }
  /**
   * ダウンロードが完了し、成功しているなら、ダウンロードしたファイルのサイズを返す。
   * @return
   */
  public long getSize() {
    if (isCompleted() && !isFailed()) {
      try {
        return file.length();
      } catch(Exception e) {}
    }
    return 0;
  }
  /**
   * ダウンロードが完了していればtrueを返す。
   * @return
   */
  public boolean isCompleted() {
    return completed;
  }
  /**
   * ダウンロードが失敗していればtrueを返す。
   * @return
   */
  public boolean isFailed() {
    return failed;
  }
  /**
   * ダウンロードが完了していれば、ダウンロードしたファイルのストリームを返す。
   * @return
   * @throws java.io.IOException
   */
  public InputStream getContentStream()
  throws java.io.IOException{
    if (isCompleted()) {
      return new FileInputStream(file);
    }
    throw new IOException();
  }
  /**
   * ヘッダを返す。
   * 一度取得すれば次からはキャッシュしたものを返す。
   * @return
   */
  public Map<String,List<String>> getHeader() {
    if (null == header) {
      if(!isCompleted()) { //null != con
        header = con.getHeaderFields();
        logger.debug("header cached");
      }
    }
    return header;
  }
  /**
   * プロトコルを返す。
   * @return
   */
  public String getProtocol() {
    return protocol;
  }
  /**
   * コンストラクタで与えられたURLをそのまま返す。
   * @return
   */
  public String getUrl() {
    return url.toString();
  }
}
