
package com.blogspot.rubyug.crawlquerypad.comparators;

import com.blogspot.rubyug.crawlquerypad.*;
import java.util.*;
/**
 * LazyLoaderをURLの降順でソートするComparatorクラス。
 * @author rubyu <@ruby_U>
 */
public class Comparator_URL_DESC implements Comparator<LazyLoader> {
  public int compare(LazyLoader loader1, LazyLoader loader2) {
    return loader2.getFullUrl().compareToIgnoreCase(loader1.getFullUrl());
  }
}