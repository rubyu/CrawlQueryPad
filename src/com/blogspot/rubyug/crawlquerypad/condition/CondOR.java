
package com.blogspot.rubyug.crawlquerypad.condition;

import com.blogspot.rubyug.crawlquerypad.*;
/**
 * 二つの子のtest関数の論理和を返すICondition実装クラス。
 * @author rubyu <@ruby_U>
 */
public class CondOR implements ICondition {
  ICondition cond1 = null;
  ICondition cond2 = null;
  /**
   * 与えられた二つの条件を持つクラスを生成して返す。
   * @param cond1
   * @param cond2
   */
  public CondOR(ICondition cond1, ICondition cond2) {
    this.cond1 = cond1;
    this.cond2 = cond2;
  }
  /**
   * 論理和を返す。
   * @param loader
   * @return
   */
  public boolean test(LazyLoader loader) {
    return cond1.test(loader) || cond2.test(loader);
  }
}
