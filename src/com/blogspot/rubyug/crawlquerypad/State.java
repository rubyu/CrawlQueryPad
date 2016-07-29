
package com.blogspot.rubyug.crawlquerypad;

import java.util.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.Files;
import javax.xml.stream.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Class of manager of MAP<String, List<String>>, supports serialize 
 * and deserialize to/from XML.
 */
public class State {
  private Map<String,List<String>> map = null;
  private Pattern encodePattern = Pattern.compile("^__(.+)__$");
  private Pattern decodePattern = Pattern.compile("^____(.+)____$");
  public State() {
    this.map = new HashMap();
  }
  /**
   * Creates state from Map<String,List<String>>.
   * @param Map<String,List<String>> map
   */
  public State(Map<String,List<String>> map) {
    Map<String,List<String>> temp = new HashMap();
    for (String key: map.keySet()) {
      List<String> list = map.get(key);
      if (list == null) {
        continue;
      }
      String lowerKey = null;
      if (key != null) {
        lowerKey = key.toLowerCase();
      }
      List<String> tempList;
      if (temp.containsKey(lowerKey)) {
        tempList = temp.get(lowerKey);
      } else {
        tempList = new ArrayList();
        temp.put(lowerKey, tempList);
      }
      for (String v: list) {
        if (v != null) {
          tempList.add(v);
        }
      }
    }
    this.map = temp;
  }
  /**
   * Creates state from XML.
   * @param String xml
   * @throws UnsupportedEncodingException
   */
  public State(String xml)
  throws UnsupportedEncodingException {
    this(new ByteArrayInputStream(xml.getBytes("utf-8")));
  }
  /**
   * Creates state from InputStream.
   * @param InputStream in
   */
  public State(InputStream in) {
    Map<String,List<String>> map = new HashMap();
    try {
      DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = dbfactory.newDocumentBuilder();
      Document doc = builder.parse(in);
      NodeList keys = doc.getElementsByTagName("key");
      for (int i=0; i < keys.getLength(); i++) {
        Node key = keys.item(i);
        String keyName = key.getAttributes().getNamedItem("name").getNodeValue();
        NodeList values = key.getChildNodes();
        List valueList = new ArrayList();
        for (int j=0; j < values.getLength(); j++) {
          Node value = values.item(j);
          String valueContent = value.getTextContent();
          valueList.add(valueContent);
        }
        map.put(keyDecode(keyName), valueList);
      }
    } catch(Exception e) {
      e.printStackTrace();
    } finally {
      try {
        in.close();
      } catch(Exception e) {}
    }
    this.map = map;
  }
  /**
   * Inserts a value to List.
   * @param String key
   * @param String value
   */
  public void add(String key, String value) {
    if (key != null) {
      key = key.toLowerCase();
    }
    if (value == null) {
      throw new NullPointerException();
    }
    if (!map.containsKey(key)) {
      map.put(key, new ArrayList());
    }
    List list = map.get(key);
    list.add(value);
  }
  /**
   * Inserts a value to List.
   * @param String key
   * @param long value
   */
  public void add(String key, long value) {
    if (key != null) {
      key = key.toLowerCase();
    }
    if (!map.containsKey(key)) {
      map.put(key, new ArrayList());
    }
    List<String> list = map.get(key);
    list.add(new Long(value).toString());
  }
  /**
   * Replaces List.
   * @param String key
   * @param List<String> list
   */
  public void put(String key, List<String> list) {
    if (key != null) {
      key = key.toLowerCase();
    }
    for (String s: list) {
      if (s == null) {
        throw new NullPointerException();
      }
    }
    map.put(key, list);
  }
  /**
   * Replaces List that contains only given value.
   * @param String key
   * @param String value
   */
  public void set(String key, String value) {
    if (key != null) {
      key = key.toLowerCase();
    }
    if (value == null) {
      throw new NullPointerException();
    }
    List<String> array = new ArrayList();
    array.add(value);
    map.put(key, array);
  }
  /**
   * Replaces List that contains only given value.
   * @param String key
   * @param long value
   */
  public void set(String key, long value) {
    if (key != null) {
      key = key.toLowerCase();
    }
    List<String> array = new ArrayList();
    array.add(new Long(value).toString());
    map.put(key, array);
  }
  /**
   * Returns a List.
   * @param String key
   * @return List<String>
   */
  public List<String> getAll(String key) {
    if (key != null) {
      key = key.toLowerCase();
    }
    if (map.containsKey(key)) {
      return new ArrayList<String>(map.get(key));
    }
    return null;
  }
  /**
   * Returns keyset of map.
   * @return Set<String>
   */
  public Set<String> getKeys() {
    return map.keySet();
  }
  /**
   * Returns copy of map.
   * @return Map<String, List<String>>
   */
  public Map<String, List<String>> getAll() {
    Map m = new HashMap();
    for (String key: getKeys()) {
      m.put(key, getAll(key));
    }
    return m;
  }
  /**
   * Returns first value of List.
   * If List not exists or it is empty, returns given default.
   * @param String key
   * @param String def
   * @return String
   */
  public String getFirstOr(String key, String def) {
    if (key != null) {
      key = key.toLowerCase();
    }
    if (map.containsKey(key)) {
      List<String> list = map.get(key);
      if (0 < list.size()) {
        return list.get(0);
      }
    }
    return def;
  }
  /**
   * Returns first value of List that converted to long.
   * If List not exists or it is empty, returns given default.
   * @param String key
   * @param Long def
   * @return long
   */
  public long getFirstOr(String key, long def) {
    if (key != null) {
      key = key.toLowerCase();
    }
    if (map.containsKey(key)) {
      List<String> list = map.get(key);
      if (0 < list.size()) {
        String value = list.get(0);
        try {
          return Long.parseLong(value);
        }catch(Exception e) {}
      }
    }
    return def;
  }
  /**
   * Deletes a List.
   * @param String key
   */
  public void remove(String key) {
    if (key != null) {
      key = key.toLowerCase();
    }
    if (map.containsKey(key)) {
      map.remove(key);
    }
  }
  /**
   * Returns XML String.
   * @return String
   */
  public String toXML() {
    String result = null;
    StringWriter sw = new StringWriter();
    XMLStreamWriter xsw  = null;
    try {
      XMLOutputFactory xof = XMLOutputFactory.newInstance();
      xsw  = xof.createXMLStreamWriter(sw);
      xsw.writeStartDocument("utf-8", "1.0");
      xsw.writeStartElement("state");
      for (String key: map.keySet()) {
        xsw.writeStartElement("key");
        xsw.writeAttribute("name", keyEncode(key));
        for (String value: map.get(key)) {
          xsw.writeStartElement("value");
          xsw.writeCharacters(value);
          xsw.writeEndElement();
        }
        xsw.writeEndElement();
      }
    xsw.writeEndElement();
    xsw.writeEndDocument();
    result = sw.toString();
    } catch(Exception e) {
      e.printStackTrace();
    } finally {
      if (sw != null) {
        try {
          sw.close();
        } catch(Exception e) {}
      }
      if (xsw != null) {
        try {
          xsw.close();
        } catch(Exception e) {}
      }
    }
    return result;
  }
  /**
   * Encodes a key.
   * @param String key
   * @return String
   */
  private String keyEncode(String key) {
    if (null == key) {
      key = "__NULL__";
    } else {
      Matcher m = encodePattern.matcher(key);
      if (m.find()) {
        key = "____"+ m.group(1) + "____";
      }
    }
    return key;
  }
  /**
   * Decodes a key.
   * @param String key
   * @return String
   */
  private String keyDecode(String key) {
    Matcher m = decodePattern.matcher(key);
    if (key.equals("__NULL__")) {
      key = null;
    } else {
      if (m.find()) {
        key = m.group(1);
      }
      key = key.toLowerCase();
    }
    return key;
  }
  
  public static State load(String name) {
    File file = new File(CrawlQueryPadApp.appHome, name + ".xml");
    if (file.exists())
    {
      FileInputStream fs = null;
      try {
        fs = new FileInputStream(file);
        return new State(fs);    
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        try {
          fs.close();
        } catch (Exception e) {}
      }
    }
    return new State();
  }
    
  public void save(String name) {
    File file = new File(CrawlQueryPadApp.appHome, name + ".xml");  
    FileOutputStream fs = null;
    FileChannel fc = null;
    FileLock lock = null;
    OutputStreamWriter writer = null;
    try {
      fs = new FileOutputStream(file);
      fc = fs.getChannel();
      lock = fc.lock();
      writer = new OutputStreamWriter(fs, "UTF-8");
      writer.write(toXML());
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        writer.close();
      } catch (Exception e) {}
    }
  }
}
