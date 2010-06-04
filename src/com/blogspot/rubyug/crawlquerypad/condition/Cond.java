
package com.blogspot.rubyug.crawlquerypad.condition;

import com.blogspot.rubyug.crawlquerypad.*;
import java.sql.*;

public class Cond implements ICondition {
  ICondition cond = null;
  public Cond(ICondition cond) {
    this.cond = cond;
  }
  public boolean test(Connection conn, LazyLoader loader) {
    return cond.test(conn, loader);
  }
}
