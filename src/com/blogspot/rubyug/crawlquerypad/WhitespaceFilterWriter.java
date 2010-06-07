
package com.blogspot.rubyug.crawlquerypad;

import java.io.*;

/**
 * WhitespaceFilterWriter is a FilterWriter that normalizes
 * continued whitespace.
 */
public class WhitespaceFilterWriter extends FilterWriter {
  private int whitespaceCount = 0;
  public WhitespaceFilterWriter(Writer writer) {
    super(writer);
  }
  @Override
  public void write(char[] cbuf, int off, int len)
  throws IOException {
    for (int i=off; i < off + len; i++) {
      this.write(cbuf[i]);
    }
  }
  @Override
  public void write(String str, int off, int len)
  throws IOException {
    for (int i=off; i < off + len; i++) {
      this.write(str.charAt(i));
    }
  }
  @Override
  public void write(int c)
  throws IOException {
    if (Character.isWhitespace(c)) {
      if (whitespaceCount < 2) {
        out.write(c);
      }
      whitespaceCount++;
    } else {
      out.write(c);
      whitespaceCount = 0;
    }
  }
}
