
package com.blogspot.rubyug.crawlquerypad.condition;

import com.blogspot.rubyug.crawlquerypad.*;
/**
 * 子のtest関数の結果の論理否定を返すICondition実装クラス。
 * @author rubyu <@ruby_U>
 */
public class CondNOT implements ICondition {
  ICondition cond = null;
  /**
   * 与えられた条件を持つクラスを生成して返す。
   * @param cond
   */
  public CondNOT(ICondition cond) {
    this.cond = cond;
  }
  /**
   * 論理否定を返す。
   * @param loader
   * @return
   */
  public boolean test(LazyLoader loader) {
    return !cond.test(loader);
  }
}
