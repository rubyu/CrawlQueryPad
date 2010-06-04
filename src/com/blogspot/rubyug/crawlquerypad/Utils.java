
package com.blogspot.rubyug.crawlquerypad;

import java.io.*;

/**
 * Class of utility functions.
 */
public class Utils {
  /**
   * Calls printStackTrace of given throwable and returns
   * string it output.
   * @param Throwable t
   * @return String
   */
  public static String ThrowableToString(Throwable t) {
      Writer writer = new StringWriter();
      PrintWriter pw = null;
      try {
        pw = new PrintWriter(writer);
        t.printStackTrace(pw);
        pw.flush();
      } finally {
        if (pw != null) {
            pw.close();
        }
      }
      return writer.toString();
  }
  /**
   * Converts InputStream to ByteArray.
   * @param InputStream in
   * @param int maxSize
   * @return byte[]
   * @throws IOException
   */
  public static byte[] InputStreamToByteArray(InputStream in, int maxSize)
  throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    try {
      byte [] buf = new byte[1024];
      int total = 0;
      int size;
      while ((size=in.read(buf)) != -1) {
        byteOut.write(buf, 0, size);
        total += size;
        if (maxSize <= total) {
          break;
        }
      }
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch(Exception e) {}
      }
    }
    return byteOut.toByteArray();
  }
  /**
   * Converts InputStream to String.
   * @param InputStream in
   * @param String charset
   * @return String
   * @throws IOException
   */
  public static String InputStreamToString(InputStream in, String charset)
  throws IOException{
    BufferedReader br = null;
    StringBuffer sb = new StringBuffer();
    try {
      br = new BufferedReader(new InputStreamReader(in, charset));
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line + "\n");
      }
      return sb.toString();
    } finally {
      if (br != null) {
        try {
          br.close();
        } catch(Exception e) {}
      }
      if (in != null) {
        try {
          in.close();
        } catch(Exception e) {}
      }
    }
  }
  /**
   * Convert InputStream to String.
   * @param InputStream in
   * @param String charset
   * @param int maxLength
   * @return String
   * @throws IOException
   */
  public static String InputStreamToString(InputStream in, String charset, int maxLength)
  throws IOException{
    BufferedReader br = null;
    StringBuffer sb = new StringBuffer();
    try {
      br = new BufferedReader(new InputStreamReader(in, charset));
      String line;
      while ((line = br.readLine()) != null) {
        if (maxLength <= sb.length() + line.length()) {
          line = line.substring(0, maxLength - sb.length());
          sb.append(line);
          break;
        }
        sb.append(line + "\n");
      }
      return sb.toString();
    } finally {
      if (br != null) {
        try {
          br.close();
        } catch(Exception e) {}
      }
      if (in != null) {
        try {
          in.close();
        } catch(Exception e) {}
      }
    }
  }
}
