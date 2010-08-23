
package com.blogspot.rubyug.crawlquerypad.comparators;

import com.blogspot.rubyug.crawlquerypad.*;
import com.blogspot.rubyug.crawlquerypad.condition.*;
import java.util.*;

/**
 * LazyLoaderを条件にマッチするかでソートするComparatorクラス。
 * @author rubyu <@ruby_U>
 */
public class Comparator_Cond implements Comparator<LazyLoader> {
  Cond cond = null;
  public Comparator_Cond(Cond cond) {
    super();
    this.cond = cond;
  }
  public int compare(LazyLoader loader1, LazyLoader loader2) {
    boolean m1 = cond.test(loader1);
    boolean m2 = cond.test(loader2);
    if (m1 == m2) {
      return 0;
    }
    if (m1) {
      return -1;
    }
    return 1;
  }
}