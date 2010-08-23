
package com.blogspot.rubyug.crawlquerypad.condition;

import com.blogspot.rubyug.crawlquerypad.*;

public class Cond implements ICondition {
  ICondition cond = null;
  public Cond(ICondition cond) {
    this.cond = cond;
  }
  public boolean test(LazyLoader loader) {
    return cond.test(loader);
  }
}
