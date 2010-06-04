
package com.blogspot.rubyug.crawlquerypad.comparators;

import com.blogspot.rubyug.crawlquerypad.*;
import com.blogspot.rubyug.crawlquerypad.condition.*;
import java.util.*;
import java.sql.*;

public class Comparator_URL_Match implements Comparator<LazyLoader> {
  Cond cond       = null;
  Connection conn = null;
  public Comparator_URL_Match(Connection conn, Cond cond) {
    super();
    this.cond = cond;
    this.conn = conn;
  }
  public int compare(LazyLoader loader1, LazyLoader loader2) {
    boolean m1 = cond.test(conn, loader1);
    boolean m2 = cond.test(conn, loader2);
    if (m1 == m2) {
      return 0;
    }
    if (m1) {
      return -1;
    }
    return 1;
  }
}