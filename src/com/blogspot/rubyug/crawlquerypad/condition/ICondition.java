
package com.blogspot.rubyug.crawlquerypad.condition;

import com.blogspot.rubyug.crawlquerypad.*;
import java.sql.*;

public interface  ICondition {
  public boolean test(LazyLoader loader);
}
