
package com.blogspot.rubyug.crawlquerypad.condition;

import com.blogspot.rubyug.crawlquerypad.*;

public class CondAND implements ICondition {
  ICondition cond1 = null;
  ICondition cond2 = null;
  public CondAND(ICondition cond1, ICondition cond2) {
    this.cond1 = cond1;
    this.cond2 = cond2;
  }
  public boolean test(LazyLoader loader) {
    return cond1.test(loader) && cond2.test(loader);
  }
}
