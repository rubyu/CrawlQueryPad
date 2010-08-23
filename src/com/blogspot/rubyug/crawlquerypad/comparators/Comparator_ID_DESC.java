
package com.blogspot.rubyug.crawlquerypad.comparators;

import com.blogspot.rubyug.crawlquerypad.*;
import java.util.*;
/**
 * LazyLoaderをIDの降順でソートするComparatorクラス。
 * @author rubyu <@ruby_U>
 */
public class Comparator_ID_DESC implements Comparator<LazyLoader> {
  public int compare(LazyLoader loader1, LazyLoader loader2) {
    return loader2.getId() - loader1.getId();
  }
}
