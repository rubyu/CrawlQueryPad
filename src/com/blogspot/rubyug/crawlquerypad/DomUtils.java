
package com.blogspot.rubyug.crawlquerypad;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.nio.charset.*;
import org.mozilla.universalchardet.UniversalDetector;
import net.htmlparser.jericho.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class of manager of HTML/CSS files.
 */
public class DomUtils {
  protected static Logger logger = LoggerFactory.getLogger(DomUtils.class);
  private static final String defaultCharset = "us-ascii";
  private static final Pattern urlSchemePattern = Pattern.compile(
          "^([a-zA-Z-0-9+.-]+)://"
          );
  private static final Pattern urlParentPattern = Pattern.compile(
          "[^/]*/\\.\\./"
          );
  private static final Pattern charsetPattern = Pattern.compile(
          "charset\\s*=\\s*([0-9a-zA-Z\\-_]+)",
          Pattern.CASE_INSENSITIVE
          );
  private static final Pattern cssCommentPattern = Pattern.compile(
          "/\\*.*?\\*/",
          Pattern.DOTALL
          );
  private static final Pattern cssSrcUrlPattern = Pattern.compile(
          "(src|url)\\s*\\(\\s*(.*?)\\s*\\)",
          Pattern.CASE_INSENSITIVE
          );
  private static final Pattern cssImportPattern = Pattern.compile(
          "@import\\s*([\"'](.*?)[\"'])",
          Pattern.CASE_INSENSITIVE
          );
  private static final Pattern cssCharsetPattern = Pattern.compile(
          "@charset\\s*[\"'](.*?)[\"']",
          Pattern.CASE_INSENSITIVE
          );
  private static final Pattern denySchemePattern = Pattern.compile(
          "^\\s*((data|javascript|file|ftp|gopher|hdl|imap|mailto|sms|smsto|mms|mmsto|news|nntp|prospero|rsync|rtsp|rtspu|sftp|shttp|sip|sips|snews|svn|svn\\+ssh|telnet|wais|tel):)",
          Pattern.CASE_INSENSITIVE
          );
  private static final Pattern metaRefleshPattern = Pattern.compile(
          "url\\s*=\\s*(.+)",
          Pattern.CASE_INSENSITIVE
          );
  /**
   * Guesses charset of plain text.
   * @param State header
   * @param InputStream contentStream
   * @return String
   */
  public static String guessCharset(State header, InputStream in) {
    String charset = null;
    if (header != null) {
      charset = guessCharsetByHeader(header);
      if (charset != null) {
        logger.debug("charset founded at header: " + charset);
        return charset;
      }
    }
    if (in != null) {
      try {
        charset = guessCharsetByUniversalDetector(in);
        if (charset != null) {
          logger.debug("charset detected by UniversalDetector: " + charset);
          return charset;
        }
      } catch(Exception e) {
        logger.error(Utils.ThrowableToString(e));
      } finally {
        if (in != null) {
          try {
            in.close();
          } catch(Exception e) {}
        }
      }
    }
    logger.debug("default charset : " + defaultCharset);
    return defaultCharset;
  }
  /**
   * Guesses charset of CSS.
   * @param State header
   * @param InputStream contentStream
   * @return String
   */
  public static String guessCssCharset(State header, InputStream in) {
    String charset = null;
    if (header != null) {
      charset = guessCharsetByHeader(header);
      if (charset != null) {
        logger.debug("charset founded at header: " + charset);
        return charset;
      }
    }
    if (in != null) {
      try {
        byte[] content = Utils.InputStreamToByteArray(in, 8192);
        try {
          in.close();
        } catch(Exception e) {}
        in = new ByteArrayInputStream(content);
        //find charset from content
        charset = guessCssCharsetByContent(in);
        if (charset != null) {
          logger.debug("charset founded at content: " + charset);
          return charset;
        }
        try {
          in.close();
        } catch(Exception e) {}
        in = new ByteArrayInputStream(content);
        //autodetect
        charset = guessCharsetByUniversalDetector(in);
        if (charset != null) {
          logger.debug("charset detected by UniversalDetector: " + charset);
          return charset;
        }
      } catch(Exception e) {
        logger.error(Utils.ThrowableToString(e));
      } finally {
        if (in != null) {
          try {
            in.close();
          } catch(Exception e) {}
        }
      }
    }
    logger.debug("default charset : " + defaultCharset);
    return defaultCharset;
  }
  /**
   * Guesses charset of HTML.
   * @param State header
   * @param InputStream contentStream
   * @return String
   */
  public static String guessHtmlCharset(State header, InputStream in) {
    String charset = null;
    if (header != null) {
      charset = guessCharsetByHeader(header);
      if (charset != null) {
        logger.debug("charset founded at header: " + charset);
        return charset;
      }
    }
    if (in != null) {
      try {
        byte[] content = Utils.InputStreamToByteArray(in, 8192);
        try {
          in.close();
        } catch(Exception e) {}
        in = new ByteArrayInputStream(content);
        //find charset from content
        charset = guessHtmlCharsetByContent(in);
        if (charset != null) {
          logger.debug("charset founded at content: " + charset);
          return charset;
        }
        try {
          in.close();
        } catch(Exception e) {}
        in = new ByteArrayInputStream(content);
        //autodetect
        charset = guessCharsetByUniversalDetector(in);
        if (charset != null) {
          logger.debug("charset detected by UniversalDetector: " + charset);
          return charset;
        }
      } catch(Exception e) {
        logger.error(Utils.ThrowableToString(e));
      } finally {
        if (in != null) {
          try {
            in.close();
          } catch(Exception e) {}
        }
      }
    }
    logger.debug("default charset : " + defaultCharset);
    return defaultCharset;
  }
  /**
   * UniversalDetector detects charset from InputStream and returns it.
   * @param InputStream in
   * @return String
   */
  public static String guessCharsetByUniversalDetector(InputStream in) {
    String charset = null;
    UniversalDetector detector = new UniversalDetector(null);
    try {
      byte[] buf = new byte[4096];
      int nread;
      while ((nread = in.read(buf)) > 0 && !detector.isDone()) {
        detector.handleData(buf, 0, nread);
      }
      detector.dataEnd();
      charset = detector.getDetectedCharset();
    } catch(Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      detector.reset();
      if (in != null) {
        try {
          in.close();
        } catch(Exception e) {}
      }
    }
    return charset;
  }
  /**
   * Returns charset of @charset directive.
   * @param InputStream in
   * @return String
   */
  public static String guessCssCharsetByContent(InputStream in) {
    String charset = null;
    String content = null;
    try {
      content = Utils.InputStreamToString(in, "us-ascii");
    } catch(Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch(Exception e) {}
      }
    }
    logger.debug("content: " + content);
    if (content != null && !content.equals("")) {
      Matcher m = cssCharsetPattern.matcher(content);
      if (m.find()) {
        charset = m.group(1);
        if (charset.equals("") ||
            !Charset.isSupported(charset)) {
          charset = null;
        }
      }
    }
    return charset;
  }
  /**
   * Returns charset in Content-Type of content.
   * @param InputStream in
   * @return String
   */
  public static String guessHtmlCharsetByContent(InputStream in) {
    String charset = null;
    String content = null;
    try {
      content = Utils.InputStreamToString(in, "us-ascii");
    } catch(Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch(Exception e) {}
      }
    }
    logger.debug("content: " + content);
    if (content != null && !content.equals("")) {
      Matcher m = charsetPattern.matcher(content);
      if (m.find()) {
        charset = m.group(1);
        if (!Charset.isSupported(charset)) {
          charset = null;
        }
      }
    }
    return charset;
  }
  /**
   * Returns charset in Content-Type of http header.
   * @param State header
   * @return String
   */
  public static String guessCharsetByHeader(State header) {
    String charset = null;
    String contentType = header.getFirstOr("content-type", "");
    if (contentType != null && !contentType.equals("")) {
      logger.debug("content-type: " + contentType);
      Matcher m = charsetPattern.matcher(contentType);
      if (m.find()) {
        charset = m.group(1);
        if (!Charset.isSupported(charset)) {
          charset = null;
        }
      }
    }
    return charset;
  }
  /**
   * Extracts links of CSS and returns Array of Object.
   * obj[0] internalLinks
   *
   * @param String baseUrl
   * @param InputStream in
   * @param String charset
   * @return Object[]
   */
  public static Object[] extractCssLinks(String baseUrl, InputStream in, String charset) {
    String css = null;
    try {
      css = Utils.InputStreamToString(in, charset);
    } catch(Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch(Exception e) {}
      }
    }
    Object[] ret = rewriteCssLinks(new SnapFinder(), baseUrl, css);
    Set<String> internalFound = (Set<String>)ret[0];
    Set<String> internalNotFound = (Set<String>)ret[1];
    css = (String)ret[2];

    return new Object[]{internalNotFound};
  }
 /**
   * Rewrites links of CSS and returns Array of Object and CSS String.
   * obj[0] internalLinksFound
   * obj[1] internalLinksNotFound
   * obj[2] CSS string
   *
   * @param SnapFinder sf
   * @param String baseUrl
   * @param String css
   * @return Object[]
   */
  public static Object[] rewriteCssLinks(SnapFinder sf, String baseUrl, String css) {

    Set<String> internalLinksFound = new HashSet();
    Set<String> internalLinksNotFound = new HashSet();
    Matcher m;

    m = cssCommentPattern.matcher(css);
    css = m.replaceAll("");
    
    m = cssImportPattern.matcher(css);
    while (m.find()) {
      String link = m.group(2);
      if (link.equals("")) {
        continue;
      }
      String quote = null;
      if (m.group(1).startsWith("\"")) {
        quote = "\"";
      } else {
        quote = "'";
      }
      if (!hasDenyScheme(link)) {
        link = getResolved(baseUrl, link);
        link = (String)getSplitedByAnchor(link)[0];
        if (isValidURL(link)) {
          if (sf.find(link)) {
            internalLinksFound.add(link);
            css = css.replace(m.group(0), "@import " + quote + sf.getInternalPath(link) + quote);
          } else {
            internalLinksNotFound.add(link);
            css = css.replace(m.group(0), "@import " + quote + link + quote);
          }
        }
      }
    }
    
    m = cssSrcUrlPattern.matcher(css);
    while (m.find()) {
      String type = m.group(1).toLowerCase();
      String link = m.group(2);
      if (link.equals("")) {
        continue;
      }
      String quote = null;
      if (link.startsWith("\"")) {
        quote = "\"";
        link = link.substring(1, link.length() - 1);
      } else if(link.startsWith("'")) {
        quote = "'";
        link = link.substring(1, link.length() - 1);
      } else {
        quote = "\"";
      }
      if (!hasDenyScheme(link)) {
        link = getResolved(baseUrl, link);
        link = (String)getSplitedByAnchor(link)[0];
        if (isValidURL(link)) {
          if (sf.find(link)) {
            internalLinksFound.add(link);
            css = css.replace(m.group(0), type + "(" + quote + sf.getInternalPath(link) + quote + ")");
          } else {
            internalLinksNotFound.add(link);
            css = css.replace(m.group(0), type + "(" + quote + link + quote + ")");
          }
        }
      }
    }

    return new Object[]{internalLinksFound, internalLinksNotFound, css};
  }
  /**
   * Checks whether given String has deny scheme or not.
   * @param String s
   * @return boolean
   */
  public static boolean hasDenyScheme(String s) {
    return denySchemePattern.matcher(s).find();
  }
  /**
   * Splits given String and returns Array of Object.
   * obj[0] scheme
   * obj[1] another
   *
   * @param String target
   * @return Object[]
   */
  public static Object[] getSplitedByScheme(String target) {
    Matcher m = urlSchemePattern.matcher(target);
    String scheme = null;
    if (m.find()) {
      scheme = m.group(1);
      target = m.replaceFirst("");
    }
    return new Object[] {scheme, target};
  }
  /**
   * Splits given String and returns Array of Object.
   * obj[0] another
   * obj[1] query
   *
   * @param String target
   * @return Object[]
   */
  public static Object[] getSplitedByQuery(String target) {
    String query = null;
    int queryIndex = target.indexOf("?");
    if (queryIndex != -1) {
      query = target.substring(queryIndex + 1);
      target = target.substring(0, queryIndex);
    }
    return new Object[] {target, query};
  }
  /**
   * Splits given String and returns Array of Object.
   * obj[0] another
   * obj[1] anchor
   *
   * @param String target
   * @return Object[]
   */
  public static Object[] getSplitedByAnchor(String target) {
    String anchor = null;
    int anchorIndex = target.indexOf("#");
    if (anchorIndex != -1) {
      anchor = target.substring(anchorIndex + 1);
      target = target.substring(0, anchorIndex);
    }
    return new Object[] {target, anchor};
  }
  /**
   * Splits given String and returns Array of Object.
   * obj[0] scheme
   * obj[1] userInfo
   * obj[2] host
   * obj[3] port
   * obj[4] path
   * obj[5] query
   * obj[6] anchor
   * 
   * @param String target
   * @return Object[]
   */
  public static Object[] getSplited(String target) {
    String s = null;
    //anchor
    Object[] ret = getSplitedByAnchor(target);
    s = (String)ret[0];
    String anchor = (String)ret[1];
    logger.trace("anchor: " + anchor);
    //scheme
    ret = getSplitedByScheme(s);
    String scheme = (String)ret[0];
    s = (String)ret[1];
    logger.trace("scheme: " + scheme);
    //userInfo, host, port
    String host = s;
    int slashIndex = s.indexOf("/");
    if (slashIndex != -1) {
      host = s.substring(0, slashIndex);
      s = s.substring(slashIndex + 1);
    } else {
      s = "";
    }
    String userInfo = null;
    int userInfoIndex = host.indexOf("@");
    if (userInfoIndex != -1) {
      userInfo = host.substring(0, userInfoIndex);
      host = host.substring(userInfoIndex + 1);
    }
    String port = null;
    int portIndex = host.indexOf(":");
    if (portIndex != -1) {
      port = host.substring(portIndex + 1);
      host = host.substring(0, portIndex);
    }
    logger.trace("userInfo:" + userInfo);
    logger.trace("host:" + host);
    logger.trace("port:" + port);
    //query
    ret = getSplitedByQuery(s);
    s = (String)ret[0];
    String query = (String)ret[1];
    logger.trace("path:" + s);
    logger.trace("query: " + query);
    return new Object[] {scheme, userInfo, host, port, s, query, anchor};
  }
  /**
   * Solves given target to given base.
   * @param String base
   * @param String target
   * @return String
   */
  public static String getResolved(String base, String target) {
    logger.debug("base: " + base);
    logger.debug("target: " + target);
    target = StringUtils.strip(target); //strip

    Object[] ret;
    String anchor = null;
    String s = null;
    if (urlSchemePattern.matcher(target).find()) {
      s = target;
    } else {
      ret = getSplitedByAnchor(target);
      target = (String)ret[0];
      anchor = (String)ret[1];

      if (target.startsWith("//")) {
        ret = getSplited(base);
        String scheme = (String)ret[0];
        s = scheme + ":" + target;
      } else if (target.startsWith("/")) {
        ret = getSplited(base);
        String scheme = (String)ret[0];
        String userInfo = (String)ret[1];
        String host = (String)ret[2];
        String port = (String)ret[3];
        s = scheme + "://";
        if (userInfo != null) {
          s += userInfo + "@";
        }
        s += host;
        if (port != null) {
          s += ":" + port;
        }
        s += "/" + target.substring(1);
      } else if (target.equals(".") ||
                 target.equals("")) {
        s = base;
        s = (String)getSplitedByAnchor(s)[0]; //cut off anchor
        s = (String)getSplitedByQuery(s)[0]; //cut off query
      } else {
        s = base;
        s = (String)getSplitedByAnchor(s)[0]; //cut off anchor
        s = (String)getSplitedByQuery(s)[0]; //cut off query
        s = s.substring(0, s.lastIndexOf("/") + 1);
        s += target;
      }
    }
    logger.trace("temporary: " + s);

    ret = getSplited(s);
    String scheme = (String)ret[0];
    String userInfo = (String)ret[1];
    String host = (String)ret[2];
    String port = (String)ret[3];
    String path = (String)ret[4];
    String query = (String)ret[5];
    
    //normalize
    logger.trace("path before normalize: " + path);
    if (path.startsWith("./")) {
      path = path.substring(2);
    }
    if (path.endsWith("/.") ||
        path.endsWith("/..")) {
      path += "/";
    }
    path = path.replace("/./", "/");
    Matcher m;
    while ((m = urlParentPattern.matcher(path)) != null && m.find()) {
      path = m.replaceFirst("");
    }
    if (path.equals(".") ||
        path.equals("..")) {
      path = "";
    }
    logger.trace("path normalized: " + path);

    if (userInfo != null) {
      host = userInfo + "@" + host;
    }
    if (port != null) {
      host += ":" + port;
    }
    String result = scheme + "://" + host + "/" + path;
    if (query != null) {
      result += "?" + query;
    }
    if (anchor != null) {
      result += "#" + anchor;
    }
    logger.debug("resolved: " + result);
    return result;
  }
  /**
   * Checks whether the given String is valid URL or not.
   * @param String s
   * @return boolean
   */
  public static boolean isValidURL(String s) {
    try {
      new URL(s);
      return true;
    } catch(MalformedURLException e) {
      logger.debug("invalid URL: " + s);
    }
    return false;
  }
  /**
   * Extracts links of html and returns Array of Object.
   * obj[0] internalLinksNotFound
   * obj[0] externalLinksNotFound
   *
   * @param String baseUrl
   * @param InputStream in
   * @param String charset
   * @return Object[]
   */
  public static Object[] extractHtmlLinks(String baseUrl, InputStream in, String charset) {

    Object[] ret = rewriteHtmlLinks(new SnapFinder(), baseUrl, in, charset, null);
    Set<String> internalFound = (Set<String>)ret[0];
    Set<String> internalNotFound = (Set<String>)ret[1];
    Set<String> externalFound = (Set<String>)ret[2];
    Set<String> externalNotFound = (Set<String>)ret[3];

    return new Object[]{internalNotFound, externalNotFound};
  }
 /**
   * Rewrites links of html and returns Array of Object.
   * obj[0] internalLinksFound
   * obj[1] internalLinksNotFound
   * obj[2] externalLinksFound
   * obj[3] externalLinksNotFound
   *
   * @param SnapFinder sf
   * @param String baseUrl
   * @param InputStream in
   * @param String charset
   * @param OutputStream out
   * @return Object[]
   */
  public static Object[] rewriteHtmlLinks(SnapFinder sf, String baseUrl, InputStream in, String charset, OutputStream out) {

    Set<String> internalLinksFound = new HashSet();
    Set<String> internalLinksNotFound = new HashSet();
    Set<String> externalLinksFound = new HashSet();
    Set<String> externalLinksNotFound = new HashSet();

    InputStreamReader isr = null;
    try {
      isr = new InputStreamReader(in, charset);
      StreamedSource streamedSource = new StreamedSource(isr);
      int lastSegmentEnd = 0;
      boolean inHeader = false;
      boolean inStyle = false;

      for (Segment segment: streamedSource) {
        logger.trace(segment.getDebugInfo());
        if (segment.getEnd() <= lastSegmentEnd) {
          logger.trace("skip");
          continue;
        }
        lastSegmentEnd = segment.getEnd();

        if (segment instanceof EndTag) {
          EndTag endTag = (EndTag)segment;
          String tagName = endTag.getName().toLowerCase();
          String tagStr = endTag.toString();

          if (tagName.equals("head")) {
            inHeader = false;
          } else if (tagName.equals("style")) {
            inStyle = false;
          } else if (tagName.equals("base")) {
            //Does not output end of base tag
            continue;
          }
          //Outputs end tags
          if (out != null) {
            out.write(tagStr.getBytes("utf-8"));
          }

        } else if (segment instanceof StartTag) {
          StartTag startTag = (StartTag)segment;
          String tagName = startTag.getName().toLowerCase();
          String tagStr = startTag.toString();
          //attributes
          Attributes tagAttributes = startTag.getAttributes();
          Map<String, String> tagAtts = new HashMap();
          if (tagAttributes != null) {
            for(Attribute a: startTag.getAttributes()) {
              tagAtts.put(a.getKey(), a.getValue());
            }
          }

          if (tagName.equals("head")) {
            inHeader = true;
          }
          if (tagName.equals("body")) {
            inHeader = false;
          }
          if (tagName.equals("style")) {
            inStyle = true;
          }

          if (tagName.equals("meta") && inHeader) {
            String httpEquiv = tagAtts.get("http-equiv");
            String content = tagAtts.get("content");
            if (httpEquiv != null &&
                content != null &&
                httpEquiv.equals("reflesh")) {
              Matcher m = metaRefleshPattern.matcher(content);
              if (m.find()) {
                String link = m.group(1);
                if (!hasDenyScheme(link)){
                  link = getResolved(baseUrl, link);
                  Object[] ret = getSplitedByAnchor(link);
                  link = (String)ret[0];
                  String anchor = (String)ret[1];
                  if (isValidURL(link)) {
                    if (sf.find(link)) {
                      internalLinksFound.add(link);
                      if (anchor != null) {
                        tagAtts.put("content", content.replace(m.group(1), sf.getInternalPath(link) + "#" + anchor));
                      } else {
                        tagAtts.put("content", content.replace(m.group(1), sf.getInternalPath(link)));
                      }
                    } else {
                      internalLinksNotFound.add(link);
                      if (anchor != null) {
                        tagAtts.put("content", content.replace(m.group(1), link + "#" + anchor));
                      } else {
                        tagAtts.put("content", content.replace(m.group(1), link));
                      }
                    }
                  }
                }
              }
            }

          } else if (tagName.equals("link")) {// && inHeader) {
            String rel = tagAtts.get("rel");
            String link = tagAtts.get("href");
            if (rel != null && link != null) {
              if (!hasDenyScheme(link)) {
                if (rel.equals("stylesheet") ||
                    rel.equals("shortcut icon") ||
                    rel.equals("apple-touch-icon")) {
                  link = getResolved(baseUrl, link);
                  link = (String)getSplitedByAnchor(link)[0]; //cut off anchor
                  if (isValidURL(link)) {
                    if (sf.find(link)) {
                      internalLinksFound.add(link);
                      tagAtts.put("href", sf.getInternalPath(link));
                    } else {
                      internalLinksNotFound.add(link);
                      tagAtts.put("href", link);
                    }
                  }
                } else {
                  link = getResolved(baseUrl, link);
                  link = (String)getSplitedByAnchor(link)[0]; //cut off anchor
                  if (isValidURL(link)) {
                    if (sf.find(link)) {
                      externalLinksFound.add(link);
                      tagAtts.put("href", sf.getExternalPath(link));
                    } else {
                      externalLinksNotFound.add(link);
                      tagAtts.put("href", link);
                    }
                    tagAtts.put("target", "_top");
                  }
                }
              }
            }

          } else if (tagName.equals("base")){ // && inHeader) {
            String link = tagAtts.get("href");
            if (link != null) {
              if (!hasDenyScheme(link)) {
                link = (String)getSplitedByAnchor(link)[0]; //cut off anchor
                if (isValidURL(link)) {
                  baseUrl = link;
                }
              }
            }
            //Does not output start of base tag
            continue;

          } else if (tagName.equals("![cdata[") ||
                     tagName.equals("!--")) {
            if (inStyle) {
              // Rewrites CSS:
              //   <style type="text/css">/*<![CDATA[*/
              //   ...
              // or
              //   <style type="text/css">
              //   <!--
              //   ...

              Object[] cssExtractResult = rewriteCssLinks(sf, baseUrl, tagStr);
              Set<String> cssInternalFound = (Set<String>)cssExtractResult[0];
              Set<String> cssInternalNotFound = (Set<String>)cssExtractResult[1];
              String css = (String)cssExtractResult[2];

              internalLinksFound.addAll(cssInternalFound);
              internalLinksNotFound.addAll(cssInternalNotFound);
              tagStr = css;
            }
            if (out != null) {
              out.write(tagStr.getBytes("utf-8"));
            }
            continue;

          } else if (tagName.equals("img") ||
                     tagName.equals("script") ||
                     tagName.equals("iframe") ||
                     tagName.equals("frame") ||
                     tagName.equals("input") ||
                     tagName.equals("embed")) { //internal
            String link = tagAtts.get("src");
            if (link != null) {
              if (!hasDenyScheme(link)) {
                link = getResolved(baseUrl, link);
                link = (String)getSplitedByAnchor(link)[0]; //cut off anchor
                if (isValidURL(link)) {
                  if (sf.find(link)) {
                    internalLinksFound.add(link);
                    tagAtts.put("src", sf.getInternalPath(link));
                  } else {
                    internalLinksNotFound.add(link);
                    tagAtts.put("src", link);
                  }
                }
              }
            }

          } else if (tagName.equals("body") ||
                     tagName.equals("table") ||
                     tagName.equals("tr") ||
                     tagName.equals("td") ||
                     tagName.equals("th")) { //internal
            String link = tagAtts.get("background");
            if (link != null) {
              if (!hasDenyScheme(link)) {
                link = getResolved(baseUrl, link);
                link = (String)getSplitedByAnchor(link)[0]; //cut off anchor
                if (isValidURL(link)) {
                  if (sf.find(link)) {
                    internalLinksFound.add(link);
                    tagAtts.put("background", sf.getInternalPath(link));
                  } else {
                    internalLinksNotFound.add(link);
                    tagAtts.put("background", link);
                  }
                }
              }
            }

          } else if (tagName.equals("a") ||
                     tagName.equals("area")) { //external
            String link = tagAtts.get("href");
            if (link != null) {
              if (!hasDenyScheme(link)) {
                link = getResolved(baseUrl, link);
                Object[] ret = getSplitedByAnchor(link);
                link = (String)ret[0];
                String anchor = (String)ret[1];
                if (isValidURL(link)) {
                  if (sf.find(link)) {
                    externalLinksFound.add(link);
                    if (anchor != null) {
                      tagAtts.put("href", sf.getExternalPath(link) + "#" + anchor);
                    } else {
                      tagAtts.put("href", sf.getExternalPath(link));
                    }
                  } else {
                    externalLinksNotFound.add(link);
                    if (anchor != null) {
                      tagAtts.put("href", link + "#" + anchor);
                    } else {
                      tagAtts.put("href", link);
                    }
                  }
                }
              }
              tagAtts.put("target", "_top");
            }
          }

          String style = tagAtts.get("style");
          if (style != null) {
            // Rewrites CSS:
            // <div style=" ... ">

            Object[] cssExtractResult = rewriteCssLinks(sf, baseUrl, style);
            Set<String> cssInternalFound = (Set<String>)cssExtractResult[0];
            Set<String> cssInternalNotFound = (Set<String>)cssExtractResult[1];
            String css = (String)cssExtractResult[2];

            internalLinksFound.addAll(cssInternalFound);
            internalLinksNotFound.addAll(cssInternalNotFound);
            tagAtts.put("style", css);
          }

          //Outputs start tags
          if (out != null) {
            StringBuffer sb = new StringBuffer();
            sb.append("<");
            sb.append(tagName);
            sb.append(" ");
            for (String key: tagAtts.keySet()) {
              sb.append(key);
              if (key.equals("style")) {
                sb.append("='");
                sb.append(tagAtts.get(key));
                sb.append("' ");
              } else {
                sb.append("=\"");
                sb.append(tagAtts.get(key));
                sb.append("\" ");
              }
            }
            sb.deleteCharAt(sb.length() - 1);
            if (startTag.isEmptyElementTag()) {
              sb.append(" />");
            } else {
              sb.append(">");
            }
            out.write(sb.toString().getBytes("utf-8"));
          }

        } else if (segment instanceof CharacterReference) {
          // HANDLE CHARACTER REFERENCE
        } else {
          // HANDLE PLAIN TEXT
          if (inStyle) {
            // Rewrites CSS:
            // <style type="text/css">
            // body {
            // ...

            Object[] cssExtractResult = rewriteCssLinks(sf, baseUrl, segment.toString());
            Set<String> cssInternalFound = (Set<String>)cssExtractResult[0];
            Set<String> cssInternalNotFound = (Set<String>)cssExtractResult[1];
            String css = (String)cssExtractResult[2];

            internalLinksFound.addAll(cssInternalFound);
            internalLinksNotFound.addAll(cssInternalNotFound);

            //Outputs text nodes in style tag
            if (out != null) {
              out.write(css.getBytes("utf-8"));
            }
            continue;
          }

          //Outputs text nodes
          if (out != null) {
            out.write(segment.toString().getBytes("utf-8"));
          }
        }
      }
    } catch(Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (isr != null) {
        try {
          isr.close();
        } catch(Exception e) {}
      }
    }
    return new Object[]{
      internalLinksFound, internalLinksNotFound,
      externalLinksFound, externalLinksNotFound
    };
  }
  /**
   * Extracts texts from InputStream to OutputStream and returns title.
   *
   * @param InputStream in
   * @param String charset
   * @param OutputStream out
   */
  public static String extractText(InputStream in, String charset, Writer writer) {

    InputStreamReader isr = null;
    String title = null;
    try {
      isr = new InputStreamReader(in, charset);
      StreamedSource streamedSource = new StreamedSource(isr);
      int lastSegmentEnd = 0;
      boolean inStyle = false;
      boolean inScript = false;
      boolean inTitle = false;

      for (Segment segment: streamedSource) {
        if (segment.getEnd() <= lastSegmentEnd) {
          continue;
        }
        lastSegmentEnd = segment.getEnd();

        if (segment instanceof EndTag) {
          EndTag endTag = (EndTag)segment;
          String tagName = endTag.getName().toLowerCase();

          if (tagName.equals("style")) {
            inStyle = false;
          } else if (tagName.equals("script")) {
            inScript = false;
          } else if (tagName.equals("title")) {
            inTitle = false;
          }

        } else if (segment instanceof StartTag) {
          StartTag startTag = (StartTag)segment;
          String tagName = startTag.getName().toLowerCase();
          if (tagName.equals("style") && !startTag.isEmptyElementTag()) {
            inStyle = true;
          } else if (tagName.equals("script") && !startTag.isEmptyElementTag()) {
            inScript = true;
          } else if (tagName.equals("title") && !startTag.isEmptyElementTag()) {
            inTitle = true;
          }

        } else if (segment instanceof CharacterReference) {
          // HANDLE CHARACTER REFERENCE
        } else {
          // HANDLE PLAIN TEXT
          if (inTitle) {
            title = segment.toString();
            continue; //Does not output title
          }
          if (writer != null && !inStyle && !inScript) {
            writer.write(segment.toString());
          }
        }
      }
    } catch(Exception e) {
      logger.error(Utils.ThrowableToString(e));
    } finally {
      if (isr != null) {
        try {
          isr.close();
        } catch(Exception e) {}
      }
    }
    return title;
  }
}
