
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
  private static final Pattern mailSchemePattern = Pattern.compile(
          "^\\s*((mailto|sms|smsto|mms|mmsto|tel):)",
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
   * Checks whether given String has deny scheme or not.
   * @param String s
   * @return boolean
   */
  public static boolean hasDenyScheme(String s) {
    return denySchemePattern.matcher(s).find();
  }
  public static boolean hasMailScheme(String s) {
    return mailSchemePattern.matcher(s).find();
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
  public static List<String> extractHtmlLinks(String baseUrl, InputStream in, String charset) {

    Set<String>  externalSet  = new HashSet<String>();
    List<String> externalList = new ArrayList<String>();

    InputStreamReader isr = null;
    try {
      isr = new InputStreamReader(in, charset);
      StreamedSource streamedSource = new StreamedSource(isr);
      int lastSegmentEnd = 0;

      for (Segment segment: streamedSource) {
        if (segment.getEnd() <= lastSegmentEnd) {
          continue;
        }
        lastSegmentEnd = segment.getEnd();

        if (segment instanceof StartTag) {
          StartTag startTag = (StartTag)segment;
          String tagName = startTag.getName().toLowerCase();
          //attributes
          Attributes tagAttributes = startTag.getAttributes();
          Map<String, String> tagAtts = new HashMap();
          if (tagAttributes != null) {
            for(Attribute a: startTag.getAttributes()) {
              tagAtts.put(a.getKey(), a.getValue());
            }
          }

          if (tagName.equals("a") ||
              tagName.equals("area")) { //external
            String link = tagAtts.get("href");
            if (link != null && !hasDenyScheme(link)) {
              link = getResolved(baseUrl, link);
              Object[] ret = getSplitedByAnchor(link);
              link = (String)ret[0];
              if (isValidURL(link)) {
                if (externalSet.contains(link)) {
                  externalSet.add(link);
                } else {
                  externalList.add(link);
                }
              }
            }
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
    return externalList;
  }
  public static Set<String> extractMails(String baseUrl, InputStream in, String charset) {

    Set<String>  externalSet  = new HashSet<String>();

    InputStreamReader isr = null;
    try {
      isr = new InputStreamReader(in, charset);
      StreamedSource streamedSource = new StreamedSource(isr);
      int lastSegmentEnd = 0;

      for (Segment segment: streamedSource) {
        if (segment.getEnd() <= lastSegmentEnd) {
          continue;
        }
        lastSegmentEnd = segment.getEnd();

        if (segment instanceof StartTag) {
          StartTag startTag = (StartTag)segment;
          String tagName = startTag.getName().toLowerCase();
          //attributes
          Attributes tagAttributes = startTag.getAttributes();
          Map<String, String> tagAtts = new HashMap();
          if (tagAttributes != null) {
            for(Attribute a: startTag.getAttributes()) {
              tagAtts.put(a.getKey(), a.getValue());
            }
          }

          if (tagName.equals("a") ||
              tagName.equals("area")) { //external
            String link = tagAtts.get("href");
            if (link != null && hasMailScheme(link)) {
              if (!externalSet.contains(link)) {
                externalSet.add(link);
              }
            }
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
    return externalSet;
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
          } else if ( tagName.equals("br") ||
                      tagName.equals("div") ||
                      tagName.equals("p") ||
                      tagName.equals("center") ||
                      tagName.equals("table") ||
                      tagName.equals("tr") ||
                      tagName.equals("h1") ||
                      tagName.equals("h2") ||
                      tagName.equals("h3") ||
                      tagName.equals("h4") ||
                      tagName.equals("h5") ||
                      tagName.equals("h6") ||
                      tagName.equals("pre") ||
                      tagName.equals("ol") ||
                      tagName.equals("ul") ||
                      tagName.equals("li") ||
                      tagName.equals("hr")
                    ) {
            if (writer != null && !inStyle && !inScript) {
              writer.write("\n");
            }
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
          } else if ( tagName.equals("br") ||
                      tagName.equals("div") ||
                      tagName.equals("p") ||
                      tagName.equals("center") ||
                      tagName.equals("table") ||
                      tagName.equals("tr") ||
                      tagName.equals("h1") ||
                      tagName.equals("h2") ||
                      tagName.equals("h3") ||
                      tagName.equals("h4") ||
                      tagName.equals("h5") ||
                      tagName.equals("h6") ||
                      tagName.equals("pre") ||
                      tagName.equals("ol") ||
                      tagName.equals("ul") ||
                      tagName.equals("li") ||
                      tagName.equals("hr")
                    ) {
            if (writer != null && !inStyle && !inScript) {
              writer.write("\n");
            }
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
