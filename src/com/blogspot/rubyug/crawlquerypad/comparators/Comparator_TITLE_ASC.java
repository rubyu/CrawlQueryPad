
package com.blogspot.rubyug.crawlquerypad.comparators;

import com.blogspot.rubyug.crawlquerypad.*;
import java.util.*;

public class Comparator_TITLE_ASC implements Comparator<LazyLoader> {
  public int compare(LazyLoader loader1, LazyLoader loader2) {
    return loader1.getTitle().compareToIgnoreCase(loader2.getTitle());
  }
}
