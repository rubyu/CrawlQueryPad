
package com.blogspot.rubyug.crawlquerypad.condition;

import com.blogspot.rubyug.crawlquerypad.*;
/**
 * 子のtest関数の結果をそのまま返すICondition実装クラス。
 * @author rubyu <@ruby_U>
 */
public class Cond implements ICondition {
  ICondition cond = null;
  /**
   * 与えられた条件を持つクラスを生成して返す。
   * @param cond
   */
  public Cond(ICondition cond) {
    this.cond = cond;
  }
  /**
   * テストを行う。
   * @param loader
   * @return
   */
  public boolean test(LazyLoader loader) {
    return cond.test(loader);
  }
}
