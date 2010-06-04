
package com.blogspot.rubyug.crawlquerypad.condition;

import com.blogspot.rubyug.crawlquerypad.*;
import java.sql.*;

public class CondAND implements ICondition {
  ICondition cond1 = null;
  ICondition cond2 = null;
  public CondAND(ICondition cond1, ICondition cond2) {
    this.cond1 = cond1;
    this.cond2 = cond2;
  }
  public boolean test(Connection conn, LazyLoader loader) {
    return cond1.test(conn, loader) && cond2.test(conn, loader);
  }
}
