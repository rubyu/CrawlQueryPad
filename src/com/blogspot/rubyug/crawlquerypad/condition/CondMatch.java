
package com.blogspot.rubyug.crawlquerypad.condition;

import com.blogspot.rubyug.crawlquerypad.*;
import java.util.regex.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 実際のtestを行うICondition実装クラス。
 * @author rubyu <@ruby_U>
 */
public class CondMatch implements ICondition {
  protected static Logger logger = LoggerFactory.getLogger(CondMatch.class);
    
  Fields.Field field = null;
  Pattern pattern = null;
  /**
   * フィールド、パターン、オプションを与え、正規表現をコンパイル。
   * それを保持するクラスを返す。
   * @param field
   * @param pattern
   * @param option
   */
  public CondMatch(Fields.Field field, String pattern, String option) {
    this.field = field;
    Set<Integer> flags = new HashSet<Integer>();
    if (null != option) {
      for (int i=0; i < option.length(); i++) {
        if ('i' == option.charAt(i)) {
          flags.add(Pattern.CASE_INSENSITIVE);
        } else if ('m' == option.charAt(i)) {
          flags.add(Pattern.MULTILINE);
        } else if ('s' == option.charAt(i)) {
          flags.add(Pattern.DOTALL);
        } else if ('d' == option.charAt(i)) {
          flags.add(Pattern.UNIX_LINES);
        } else if ('x' == option.charAt(i)) {
          flags.add(Pattern.COMMENTS);
        } else if ('u' == option.charAt(i)) {
          flags.add(Pattern.UNICODE_CASE);
        } else {
          //unsupported
        }
      }
    }
    int flag = 0;
    for (int f : flags) {
      flag = flag | f;
    }
    if (0 == flag) {
      this.pattern = Pattern.compile(pattern);
    } else {
      this.pattern = Pattern.compile(pattern, flag);
    }
  }
  /**
   * LazyLoaderの指定のフィールドに対し、正規表現でfind()を行い、結果を返す。
   * @param loader
   * @return
   */
  public boolean test(LazyLoader loader) {
    logger.debug("test()");
    logger.debug("field: " + this.field.name());
    logger.debug("pattern: " + this.pattern.toString());
    
    if (this.field == Fields.Field.ID) {
      String value = loader.getId().toString();
      logger.trace("value: " + value);
      return this.pattern.matcher(value).find();

    } else if (this.field == Fields.Field.URL) {
      String value = loader.getUrl();
      logger.trace("value: " + value);
      return this.pattern.matcher(value).find();

    } else if (this.field == Fields.Field.TITLE) {
      String value = loader.getTitle();
      logger.trace("value: " + value);
      return this.pattern.matcher(value).find();

    } else if (this.field == Fields.Field.BODY) {
      String value = loader.getContentString();
      logger.trace("value: " + value);
      return this.pattern.matcher(value).find();
      
    } else if (this.field == Fields.Field.TEXT) {
      String value = loader.getText();
      logger.trace("value: " + value);
      return this.pattern.matcher(value).find();

    } else {
      throw new RuntimeException("Unknown Field!");
    }
  }
}
