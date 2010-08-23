
package com.blogspot.rubyug.crawlquerypad.comparators;

import com.blogspot.rubyug.crawlquerypad.*;
import java.util.*;
/**
 * LazyLoaderをURLの昇順でソートするComparatorクラス。
 * @author rubyu <@ruby_U>
 */
public class Comparator_URL_ASC implements Comparator<LazyLoader> {
  public int compare(LazyLoader loader1, LazyLoader loader2) {
    return loader1.getFullUrl().compareToIgnoreCase(loader2.getFullUrl());
  }
}
