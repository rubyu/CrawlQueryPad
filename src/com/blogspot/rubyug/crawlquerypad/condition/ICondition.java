
package com.blogspot.rubyug.crawlquerypad.condition;

import com.blogspot.rubyug.crawlquerypad.*;
/**
 * LazyLoaderに対するtest関数を提供するためのインターフェイス。
 * @author rubyu <@ruby_U>
 */
public interface ICondition {
  public boolean test(LazyLoader loader);
}
