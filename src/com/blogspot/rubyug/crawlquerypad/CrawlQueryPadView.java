/*
 * CrawlQueryPadView.java
 */

package com.blogspot.rubyug.crawlquerypad;

import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.TaskMonitor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.text.*;
import javax.swing.event.*;
import java.awt.Color;
import javax.swing.undo.*;

import com.blogspot.rubyug.crawlquery.*;
import javax.swing.table.DefaultTableModel;

import com.blogspot.rubyug.crawlquerypad.condition.*;
import com.blogspot.rubyug.crawlquerypad.comparators.*;
import org.h2.jdbcx.JdbcConnectionPool;
import java.sql.*;
import java.net.*;

import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The application's main frame.
 */
public class CrawlQueryPadView extends FrameView {
    protected static Logger logger = LoggerFactory.getLogger(CrawlQueryPadView.class);

    public static String getCurrent() {
      String top = CrawlQueryPadView.class.getResource("/com/blogspot/rubyug/crawlquerypad/CrawlQueryPadView.class").getFile();
      try {
        top = URLDecoder.decode(top, "utf-8");
      } catch(Exception e){}

      if(top.startsWith("file:/")){
        top = top.substring(5, top.toLowerCase().indexOf(".jar!/") + 4);
        if(top.matches("^/[A-Z]:/.*")){
          top = top.substring(1);
        }
        top = top.substring(0, top.lastIndexOf("/"));
        if(top.matches("^[A-Z]:")){
          top += "/";
        }
        top = top.replace("\\", "/");
      }else{
        top = ".";
      }
      return top;
    }
    public CrawlQueryPadView(SingleFrameApplication app) {
      super(app);

      initComponents();

      // status bar initialization - message timeout, idle icon and busy animation, etc
      ResourceMap resourceMap = getResourceMap();
      int messageTimeout = resourceMap.getInteger("StatusBar.messageTimeout");
      messageTimer = new Timer(messageTimeout, new ActionListener() {
          public void actionPerformed(ActionEvent e) {
              statusMessageLabel.setText("");
          }
      });
      messageTimer.setRepeats(false);
      int busyAnimationRate = resourceMap.getInteger("StatusBar.busyAnimationRate");
      for (int i = 0; i < busyIcons.length; i++) {
          busyIcons[i] = resourceMap.getIcon("StatusBar.busyIcons[" + i + "]");
      }
      busyIconTimer = new Timer(busyAnimationRate, new ActionListener() {
          public void actionPerformed(ActionEvent e) {
              busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
              statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
          }
      });
      idleIcon = resourceMap.getIcon("StatusBar.idleIcon");
      statusAnimationLabel.setIcon(idleIcon);
      progressBar.setVisible(false);

      queryPane.addCaretListener(new QueryPaneCaretListener());

      queryPaneDoc = (StyledDocument) queryPane.getDocument();
      queryPaneDoc.addDocumentListener(new QueryPaneDocumentListener());

      undomanager = new UndoManager();
      queryPaneDoc.addUndoableEditListener(undomanager);

      queryPane.getKeymap().addActionForKeyStroke(
        KeyStroke.getKeyStroke("ctrl Z"), new TextAction("Ctrl-Z") {
          public void actionPerformed(ActionEvent e) {
            if (undomanager.canUndo()) undomanager.undo();
          }
      });
      queryPane.getKeymap().addActionForKeyStroke(
        KeyStroke.getKeyStroke("ctrl Y"), new TextAction("Ctrl-Y") {
          public void actionPerformed(ActionEvent e) {
            if (undomanager.canRedo()) undomanager.redo();
          }
      });

      //DB
      connectionPool = JdbcConnectionPool.create("jdbc:h2:~/.cqpad/main;DEFAULT_LOCK_TIMEOUT=1000;DB_CLOSE_ON_EXIT=FALSE", "sa", "sa");
      try {
        conn = connectionPool.getConnection();
        Statement st = null;
        st = conn.createStatement();
        st.execute(
          "DROP TABLE IF EXISTS response_cache;");
        st.execute(
          "CREATE TABLE IF NOT EXISTS response_cache(" +
          "url VARCHAR PRIMARY KEY NOT NULL," +
          "state CLOB NOT NULL," +
          "header CLOB," +
          "title CLOB," +
          "text CLOB," +
          "content BLOB" +
          ");");
        st.execute(
          "CREATE TABLE IF NOT EXISTS api_state(" +
          "key VARCHAR NOT NULL PRIMARY KEY," +
          "state CLOB NOT NULL" +
          ");");
      } catch (SQLException e) {
        logger.error(Utils.ThrowableToString(e));
      }

      extensions = new ArrayList<String>();
      //exts
      File exts =  new File( getCurrent() + "/ext" );
      if (exts.exists() && exts.isDirectory()) {
        for (File f : exts.listFiles()) {
          if ( !f.getName().endsWith(".py") &&
               !f.getName().endsWith(".jpy") ) {
             continue;
          }
          InputStream in = null;
          try {
            logger.info("Adding extension script : " + f.getName());
            in = new FileInputStream(f);
            String charset = DomUtils.guessCharsetByUniversalDetector(in);
            if (null == charset) {
              charset = "utf-8";
            }
            try {
                in.close();
              } catch (Exception e) {}
            in = new FileInputStream(f);
            String str = Utils.InputStreamToString(in, charset);
            PythonInterpreter pyi = new PythonInterpreter();
            pyi.exec(str);

            PyObject name = pyi.eval("ext_name()");
            logger.info("name: " + name);
            PyObject desc = pyi.eval("ext_description()");
            logger.info("description: " + desc);
            /*
            pyi.set("title", "foo"); //dummy
            pyi.set("text",  "bar"); //dummy
            PyObject result = pyi.eval("call(title, text)");
            logger.debug("result: " + result);
            */
            logger.info("OK");
            extensions.add(str);
            DefaultComboBoxModel model = (DefaultComboBoxModel)jComboBox1.getModel();
            model.addElement(name + ": " + desc);
          } catch (Exception e) {
            logger.info("NG");
            logger.error(Utils.ThrowableToString(e));
          } finally {
            if (null != in) {
              try {
                in.close();
              } catch (Exception e) {}
            }
          }
        }
      }

      jButton1.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (jComboBox1.isEnabled()) {
              try {
                logger.info("call: " + jComboBox1.getSelectedItem());
                int index = jComboBox1.getSelectedIndex();
                String str = extensions.get(index);
                PythonInterpreter pyi = new PythonInterpreter();
                pyi.exec(str);
                PyObject ext_name = pyi.eval("ext_name()");
                //set api
                pyi.set("API", new ExtensionAPI(ext_name.toString()));

                Map map = new HashMap();
                map.put("title", titleTextField.getText());
                map.put("text", resultPane.getText());
                map.put("queryString", worker.getQueryString());
                pyi.set("____data____",  map);
                String result = pyi.eval("call(____data____)").toString();
                logger.info("result: " + result);
                //
                statusMessageLabel.setText("Script is called: " + result);
                messageTimer.restart();
              } catch (Exception ex) {
                logger.error(Utils.ThrowableToString(ex));
              }
            }
          }
        });

      jButton2.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (jComboBox1.isEnabled()) {
              try {
                logger.info("clear state: " + jComboBox1.getSelectedItem());
                int index = jComboBox1.getSelectedIndex();
                String str = extensions.get(index);
                PythonInterpreter pyi = new PythonInterpreter();
                pyi.exec(str);
                PyObject ext_name = pyi.eval("ext_name()");
                ExtensionAPI api = new ExtensionAPI(ext_name.toString());
                api.setState(new State());
                logger.info("success");
                //
                statusMessageLabel.setText("State is cleared");
                messageTimer.restart();
              } catch (Exception ex) {
                logger.error(Utils.ThrowableToString(ex));
              }
            }
          }
        });

      crawlButton.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            invokeQueryParse(true);
          }
        });

      urlButton.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            insertStringToJTextPane(queryPane, "\"\" ");
          }
        });
      setButton.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            insertStringToJTextPane(queryPane, "> $ // ");
          }
        });
      filterButton.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            insertStringToJTextPane(queryPane, "> # // ");
          }
        });
      depth1Button.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            insertStringToJTextPane(queryPane, "> 1 ");
          }
        });
      depth2Button.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            insertStringToJTextPane(queryPane, "> 2 ");
          }
        });
      sortButton.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            insertStringToJTextPane(queryPane, "> @ url ASC ");
          }
        });

      int wait = (Integer)downloadWaitSpinner.getValue();
      LazyLoader.setWait(wait);
      downloadWaitSpinner.addChangeListener( new ChangeListener() {
          public void stateChanged(ChangeEvent e) {
            JSpinner spinner = (JSpinner)e.getSource();
            int wait = (Integer)spinner.getValue();
            LazyLoader.setWait(wait);
          }
        });
    }

    void insertStringToJTextPane(JTextPane pane, String text) {
      Document doc = pane.getDocument();
      StyleContext sc = new StyleContext();
      int pos = pane.getCaretPosition();
      try {
        doc.insertString(pos, text, sc.getStyle(StyleContext.DEFAULT_STYLE));
      } catch (BadLocationException ex) {
        logger.error(Utils.ThrowableToString(ex));
      }
    }
    void setStringToJTextPane(JTextPane pane, String text) {
      pane.setText(text);
      pane.setCaretPosition(0);
    }

    void highlightUpdate() {
        if (null == query) {
          return;
        }
        //disconnect undomanager
        queryPaneDoc.removeUndoableEditListener(undomanager);

        String text = queryPane.getText();
        StyledDocument doc = (StyledDocument) queryPane.getDocument();
        // remove all atteributes
        SimpleAttributeSet plane = new SimpleAttributeSet();
        doc.setCharacterAttributes(0, text.length(), plane, true);

        List<Integer> textLineCount = new ArrayList<Integer>();
        textLineCount.add(0);
        for( int i=0; i < text.length(); i++ ) {
          textLineCount.set( textLineCount.size() - 1, textLineCount.get(textLineCount.size() - 1) + 1 );
          char c = text.charAt(i);
          if ( '\r' == c ||
               '\n' == c ) {
             if ( '\r' == c &&
                  i + 1 < text.length() ) {
                i++;
                c = text.charAt(i);
                if ( '\n' != c ) {
                  i--;
                }
             }
             textLineCount.add(0);
          }
        }

        //initialize
        List<Object[]> stack = new ArrayList<Object[]>();
        stack.add( new Object[]{query, -1} );
        int depth = 0;
        for(;;) {
          if ( depth == -1 ) {
            break;
          }
          Object[] o = stack.get(depth);
          SimpleNode node = (SimpleNode)o[0];
          int cursor = (Integer)o[1];
          cursor++;
          o[1] = cursor;

          if (cursor == 0) { //at first visit

            if (null != node.jjtGetValue()) { //if node has value
              Object[] arr = (Object[])node.jjtGetValue();
              Throwable throwable = (Throwable)arr[0];
              Token token = (Token)arr[1];
              String value = (String)arr[2];

              if (null != token) {
                //calculate node position
                int startPos = -1;
                for ( int i=0; i < token.beginLine - 1; i++ ) {
                  startPos += textLineCount.get(i);
                }
                startPos += token.beginColumn;

                int endPos = 0;
                for ( int i=0; i < token.endLine - 1; i++ ) {
                  endPos += textLineCount.get(i);
                }
                endPos += token.endColumn;

                int length = endPos - startPos;

                boolean caretOnToken = false;
                if ( startPos <= queryPane.getCaretPosition() &&
                     queryPane.getCaretPosition() <= endPos
                   ) {
                   caretOnToken = true;
                }

                //apply code highlight
                SimpleAttributeSet attr = new SimpleAttributeSet();
                if (token.kind == QueryParserConstants.URL) {
                  StyleConstants.setForeground(attr, new Color(78,191,233) ); //blue
                  if (caretOnToken) {
                    StyleConstants.setBackground(attr, new Color(252,245,167) );
                  }

                } else if ( token.kind == QueryParserConstants.REGEX ||
                            token.kind == QueryParserConstants.REGEX_OPTION
                          ) {
                  StyleConstants.setForeground(attr, new Color(225,40,133) ); //red
                  if (caretOnToken) {
                    StyleConstants.setBackground(attr, new Color(252,245,167) );
                  }

                } else if (token.kind == QueryParserConstants.NUMBER) {
                  StyleConstants.setBold(attr, true);
                  StyleConstants.setForeground(attr, new Color(85,86,88) ); //gray
                  if (caretOnToken) {
                    StyleConstants.setBackground(attr, new Color(252,245,167) );
                  }

                } else if (token.kind == QueryParserConstants.GT) {
                  StyleConstants.setBold(attr, true);
                  StyleConstants.setForeground(attr, new Color(55,59,62) ); //dark gray

                } else if ( token.kind == QueryParserConstants.L_PAREN ||
                            token.kind == QueryParserConstants.R_PAREN
                          ) {
                  StyleConstants.setBold(attr, true);
                  StyleConstants.setForeground(attr, new Color(85,86,88) ); //gray

                } else if ( token.kind == QueryParserConstants.ORDER_ASC ||
                            token.kind == QueryParserConstants.ORDER_DESC
                          ) {
                  StyleConstants.setForeground(attr, new Color(242,156,73) ); //orange
                  if (caretOnToken) {
                    StyleConstants.setBackground(attr, new Color(252,245,167) );
                  }

                } else if ( token.kind == QueryParserConstants.FIELD_ID ||
                            token.kind == QueryParserConstants.FIELD_BODY ||
                            token.kind == QueryParserConstants.FIELD_TEXT ||
                            token.kind == QueryParserConstants.FIELD_TITLE ||
                            token.kind == QueryParserConstants.FIELD_URL
                          ) {
                  StyleConstants.setForeground(attr, new Color(19,122,127) ); //green
                  if (caretOnToken) {
                    StyleConstants.setBackground(attr, new Color(252,245,167) );
                  }

                }
                doc.setCharacterAttributes(startPos, length, attr, true);
              }
            }
          }
          if ( node.jjtGetNumChildren() <= cursor ) {
            stack.remove(depth);
            depth--;
          } else {
            //depth-first
            stack.add( new Object[]{ node.jjtGetChild(cursor), -1 } );
            depth++;
          }
        }
        
        //reconnect undomanager
        queryPaneDoc.addUndoableEditListener(undomanager);

    }
    void queryParse(boolean doCrawl) {
      
      //clear
      javax.swing.table.DefaultTableModel model;
      model = (DefaultTableModel)instTable.getModel();
      model.setRowCount(0);
      model = (DefaultTableModel)resultTable.getModel();
      model.setRowCount(0);
      resultPane.setText("");
      //apiPanel
      jButton1.setEnabled(false);
      jButton2.setEnabled(false);
      jComboBox1.setEnabled(false);
      titleTextField.setText("");

      String queryString = queryPane.getText();
      QueryParser parser = new QueryParser( new StringReader(queryString) );

      //命令リスト
      List<Object[]> instructions = new ArrayList<Object[]>();
      //エラーリスト
      List<Throwable> throwables = new ArrayList<Throwable>();
      try {
        query = parser.parse();

        //ノードのナンバリングのためのリスト
        List<SimpleNode> nodes = new ArrayList<SimpleNode>();
        nodes.add(null); //dummy

        //initialize
        List<Object[]> stack = new ArrayList<Object[]>();
        stack.add( new Object[]{query, -1} );
        int depth = 0;

        for(;;) {
          if ( depth == -1 ) {
            break;
          }
          Object[] o = stack.get(depth);
          SimpleNode node = (SimpleNode)o[0];
          int cursor = (Integer)o[1];
          cursor++;
          o[1] = cursor;
          if (cursor == 0) { //at first visit
            //add node
            nodes.add(node);
            if (null != node.jjtGetValue()) { //if node has value
              Object[] arr = (Object[])node.jjtGetValue();
              Throwable throwable = (Throwable)arr[0];
              if (null != throwable) {
                //add throwable
                throwables.add(throwable);
              }
            }
          }
          if ( node.jjtGetNumChildren() <= cursor ) {

            //add operation
            if ( node instanceof Condition ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CONDITION", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Condition_AND ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CONDITION AND", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Condition_Match ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CONDITION MATCH", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Condition_OR ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CONDITION OR", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Condition_Unary_NOT ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CONDITION UNARY NOT", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );

            } else if ( node instanceof Crawl ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CRAWL", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Crawl_Execute ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CRAWL EXECUTE", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Crawl_Filter ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CRAWL FILTER", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Crawl_Operator_Difference ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CRAWL OP DIFFERENCE", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Crawl_Operator_Intersection ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CRAWL OP INTERSECTION", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Crawl_Operator_SymmetricDifference ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CRAWL OP SYMMETRIC DIFFERENCE", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Crawl_Operator_Union ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CRAWL OP UNION", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Crawl_Set_Condition ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "CRAWL SET CONDITION", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );

            } else if ( node instanceof Depth_Value ) {
              Object[] arr = (Object[])node.jjtGetValue();
              String value = (String)arr[2];
              instructions.add( new Object[] {
                  instructions.size() + 1, "DEPTH VALUE", nodes.indexOf(node.jjtGetParent()), value
                } );

            } else if ( node instanceof Field_BODY ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "FIELD BODY", nodes.indexOf(node.jjtGetParent()), "FIELD_BODY"
                } );
            } else if ( node instanceof Field_ID ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "FIELD ID", nodes.indexOf(node.jjtGetParent()), "FIELD_ID"
                } );
            } else if ( node instanceof Field_TEXT ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "FIELD TEXT", nodes.indexOf(node.jjtGetParent()), "FIELD_TEXT"
                } );
            } else if ( node instanceof Field_TITLE ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "FIELD TITLE", nodes.indexOf(node.jjtGetParent()), "FIELD_TITLE"
                } );
            } else if ( node instanceof Field_URL ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "FIELD URL", nodes.indexOf(node.jjtGetParent()), "FIELD_URL"
                } );

            } else if ( node instanceof Index_Order ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "INDEX ORDER", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );

            } else if ( node instanceof Order_ASC ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "ORDER ASC", nodes.indexOf(node.jjtGetParent()), "ORDER_ASC"
                } );
            } else if ( node instanceof Order_DESC ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "ORDER DESC", nodes.indexOf(node.jjtGetParent()), "ORDER_DESC"
                } );

            } else if ( node instanceof Query ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "QUERY", 0, nodes.indexOf(node)
                } );

            } else if ( node instanceof Query_Option_Index ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "QUERY OPTION INDEX", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );

            } else if ( node instanceof Regex ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "REGEX", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );
            } else if ( node instanceof Regex_Option_Value ) {
              Object[] arr = (Object[])node.jjtGetValue();
              String value = (String)arr[2];
              instructions.add( new Object[] {
                  instructions.size() + 1, "REGEX OPTION VALUE", nodes.indexOf(node.jjtGetParent()), value
                } );
            }  else if ( node instanceof Regex_Pattern_Value ) {
              Object[] arr = (Object[])node.jjtGetValue();
              String value = (String)arr[2];
              instructions.add( new Object[] {
                  instructions.size() + 1, "REGEX PATTERN VALUE", nodes.indexOf(node.jjtGetParent()), value
                } );

            } else if ( node instanceof Url_Value ) {
              Object[] arr = (Object[])node.jjtGetValue();
              String value = (String)arr[2];
              instructions.add( new Object[] {
                  instructions.size() + 1, "URL", nodes.indexOf(node.jjtGetParent()), value
                } );

            } else if ( node instanceof Urls ) {
              instructions.add( new Object[] {
                  instructions.size() + 1, "URLS", nodes.indexOf(node.jjtGetParent()), nodes.indexOf(node)
                } );

            } else if ( node instanceof Chain ||
                        node instanceof Left_Paren ||
                        node instanceof Right_Paren  ) {
              //pass
            } else {
              throw new Exception("Unknown Class!");
            }

            stack.remove(depth);
            depth--;
          } else {
            //depth-first
            stack.add( new Object[]{ node.jjtGetChild(cursor), -1 } );
            depth++;
          }
        }

      } catch (Throwable t) {
        String error = Utils.ThrowableToString(t);
        logger.error(error);
        statusMessageLabel.setText("Error");
        messageTimer.restart();
        setStringToJTextPane(resultPane, error);
        return;
      }

      model = (DefaultTableModel)instTable.getModel();
      model.setRowCount(0);
      for (Object[] arr: instructions) {
        model.addRow(arr);
      }

      if ( instructions.size() < 2 ||  //empty
           0 != throwables.size()      //has error
         ) {
        crawlButton.setEnabled(false);
      } else {
        crawlButton.setEnabled(true);
      }

      if ( 0 == throwables.size() && //no error
           doCrawl
         ) {
        worker = new CrawlExcecuteWorker(queryString, instructions);
        worker.addPropertyChangeListener(new PropertyChangeListener());
        worker.execute();
      }
    }
    public class PropertyChangeListener implements java.beans.PropertyChangeListener {
      public void propertyChange(java.beans.PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if("state".equalsIgnoreCase(propertyName)){
            SwingWorker.StateValue state = (SwingWorker.StateValue)evt.getNewValue();
            if(SwingWorker.StateValue.STARTED == state){
              if (!busyIconTimer.isRunning()) {
                statusAnimationLabel.setIcon(busyIcons[0]);
                busyIconIndex = 0;
                busyIconTimer.start();
              }
              progressBar.setVisible(true);
              progressBar.setIndeterminate(true);
            }else if(SwingWorker.StateValue.DONE == state){
              busyIconTimer.stop();
              statusAnimationLabel.setIcon(idleIcon);
              progressBar.setVisible(false);
              progressBar.setValue(0);
            }
        } else if ("progress".equals(propertyName)) {
          int value = (Integer)(evt.getNewValue());
          progressBar.setVisible(true);
          progressBar.setIndeterminate(false);
          progressBar.setValue(value);
        }
      }
    }
    void invokeHighlightUpdate() {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          highlightUpdate();
        }
      });
    }
    void invokeQueryParse(boolean doCrawl) {
      final boolean _doCrawl = doCrawl;
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          queryParse(_doCrawl);
          highlightUpdate();
        }
      });
    }
    class QueryPaneDocumentListener implements DocumentListener {
      public void insertUpdate(DocumentEvent e) {
        if (null != worker && !worker.isDone() && !worker.isCancelled()) {
          workerStop = true;
          worker.cancel(true);
        }
        invokeQueryParse(false);
      }
      public void removeUpdate(DocumentEvent e) {
        if (null != worker && !worker.isDone() && !worker.isCancelled()) {
          workerStop = true;
          worker.cancel(true);
        }
        invokeQueryParse(false);
      }
      public void changedUpdate(DocumentEvent e) {
      }
    }
    class QueryPaneCaretListener implements CaretListener {
      public void caretUpdate(CaretEvent e){
        invokeHighlightUpdate();
      }
    }
    class CrawlExcecuteWorker extends SwingWorker<Object[], String> {
      String queryString = null;
      List<Object[]> instructions = null;
      public CrawlExcecuteWorker(String queryString, List<Object[]> insts) {
        this.queryString  = queryString;
        this.instructions = insts;
      }
      public String getQueryString() {
        return this.queryString;
      }
      public void publish(String s){
        super.publish(s);

      }
      @Override
      protected Object[] doInBackground() throws Exception {
        publish("initializing...");
        setProgress(0);

        int maxPool = 0;
        for (Object[] instruction : instructions) {
          Integer to = (Integer)instruction[2];
          if (maxPool < to) {
            maxPool = to;
          }
        }
        List<List> pool = new ArrayList<List>();
        for (int i=0; i < maxPool + 2; i++) {
          pool.add( new ArrayList() );
        }

        //Conds
        List<Cond> filters = new ArrayList<Cond>();

        //Sort
        List sorts = new ArrayList();
        //Sort default
        List defaultSort = new ArrayList();
        defaultSort.add( Fields.Field.ID );
        defaultSort.add( Orders.Order.ASC );
        sorts.add( 0, defaultSort );

        if (null == conn) {
          logger.warn("Missing connection. Retry to get connection.");
          conn = connectionPool.getConnection();
        }

        LazyLoaderManager manager = new LazyLoaderManager(conn);

        for (Object[] instruction : instructions) {
          Integer id   = (Integer)instruction[0];
          String  inst = (String) instruction[1];
          Integer to   = (Integer)instruction[2];
          Object  from = instruction[3];

          if (workerStop) {
            logger.info("worker is stopped.");
            workerStop = false;
            throw new java.util.concurrent.CancellationException();
          }

          publish( "(" + id + "/" + instructions.size() + ")" + " [" + inst + "] " + to + ", " + from );
          setProgress(100 * id / instructions.size());

          logger.debug("--DUMP--");
          logger.debug("id: " + id);
          logger.debug("inst: " + inst);
          logger.debug("to: " + to);
          if (from instanceof Integer) {
            logger.debug("from: " + from);
            List fArr = pool.get((Integer)from);
            for (Object o : fArr) {
              logger.debug(o.toString());
            }
          } else {
            logger.debug("from(value): " + from);
          }

          if ( inst.equals("CONDITION") ) {
            List fromArr = pool.get((Integer)from);
            pool.get(to).add( new Cond((ICondition)fromArr.get(0)) );

          } else if ( inst.equals("CONDITION AND") ) {
            List fromArr = pool.get((Integer)from);
            pool.get(to).add( new CondAND((ICondition)fromArr.get(0), (ICondition)fromArr.get(1)) );

          } else if ( inst.equals("CONDITION MATCH") ) {
            List fromArr = pool.get((Integer)from);
            Fields.Field field = null;
            String pattern = null;
            String option = null;
            for (int i=0; i < fromArr.size(); i++) {
              Object o = fromArr.get(i);
              if (o instanceof Fields.Field) {
                field = (Fields.Field)o;
              } else if (null == pattern) {
                pattern = (String)o;
              } else if (null == option) {
                option = (String)o;
              }
            }
            if (null == field) {
              field = Fields.Field.URL; //apply default
            }
            pool.get(to).add( new CondMatch(field, pattern, option) );

          } else if ( inst.equals("CONDITION OR") ) {
            List fromArr = pool.get((Integer)from);
            pool.get(to).add( new CondOR((ICondition)fromArr.get(0), (ICondition)fromArr.get(1)) );

          } else if ( inst.equals("CONDITION UNARY NOT") ) {
            List fromArr = pool.get((Integer)from);
            pool.get(to).add( new CondNOT((ICondition)fromArr.get(0)) );


          } else if ( inst.equals("CRAWL") ) {
            List fromArr = pool.get((Integer)from);
            pool.get(to).add( fromArr.get(0) ); //copy one
            //reset filters
            filters.clear();

          } else if ( inst.equals("CRAWL EXECUTE") ) {
            List fromArr = pool.get((Integer)from);
            List toArr   = pool.get(to);
            int depth = (Integer)fromArr.get(0);
            LogicalOperationSet set = (LogicalOperationSet)toArr.get(0);
            set = set.getCrawled(depth, filters, this);
            toArr.clear();
            toArr.add(set);

          } else if ( inst.equals("CRAWL FILTER") ) {
            List fromArr = pool.get((Integer)from);
            List toArr   = pool.get(to);
            LogicalOperationSet set = (LogicalOperationSet)toArr.get(0);
            LogicalOperationSet newSet = set.getCondsFiltered(fromArr);
            toArr.clear();
            toArr.add(newSet);

          } else if ( inst.equals("CRAWL OP DIFFERENCE") ) {
            List fromArr = pool.get((Integer)from);
            List toArr   = pool.get(to);
            LogicalOperationSet set1 = (LogicalOperationSet)fromArr.get(0);
            LogicalOperationSet set2 = (LogicalOperationSet)fromArr.get(1);
            toArr.add(set1.getDifference(set2));

          } else if ( inst.equals("CRAWL OP INTERSECTION") ) {
            List fromArr = pool.get((Integer)from);
            List toArr   = pool.get(to);
            LogicalOperationSet set1 = (LogicalOperationSet)fromArr.get(0);
            LogicalOperationSet set2 = (LogicalOperationSet)fromArr.get(1);
            toArr.add(set1.getIntersection(set2));

          } else if ( inst.equals("CRAWL OP SYMMETRIC DIFFERENCE") ) {
            List fromArr = pool.get((Integer)from);
            List toArr   = pool.get(to);
            LogicalOperationSet set1 = (LogicalOperationSet)fromArr.get(0);
            LogicalOperationSet set2 = (LogicalOperationSet)fromArr.get(1);
            toArr.add(set1.getSymmetricDifference(set2));

          } else if ( inst.equals("CRAWL OP UNION") ) {
            List fromArr = pool.get((Integer)from);
            List toArr   = pool.get(to);
            LogicalOperationSet set1 = (LogicalOperationSet)fromArr.get(0);
            LogicalOperationSet set2 = (LogicalOperationSet)fromArr.get(1);
            toArr.add(set1.getUnion(set2));

          } else if ( inst.equals("CRAWL SET CONDITION") ) {
            List fromArr = pool.get((Integer)from);
            filters.add((Cond)fromArr.get(0));


          } else if ( inst.equals("DEPTH VALUE") ) {
            int depth = Integer.parseInt( (String)from );
            pool.get(to).add( depth );

          } else if ( inst.equals("FIELD BODY") ) {
            pool.get(to).add( Fields.Field.BODY );

          } else if ( inst.equals("FIELD ID") ) {
            pool.get(to).add( Fields.Field.ID );

          } else if ( inst.equals("FIELD TEXT") ) {
            pool.get(to).add( Fields.Field.TEXT );

          } else if ( inst.equals("FIELD TITLE") ) {
            pool.get(to).add( Fields.Field.TITLE );

          } else if ( inst.equals("FIELD URL") ) {
            pool.get(to).add( Fields.Field.URL );


          } else if ( inst.equals("INDEX ORDER") ) {
            List fromArr = pool.get((Integer)from);
            if (fromArr.size() == 1) {
              fromArr.add( 0, Fields.Field.URL ); //default
              pool.get(to).add( fromArr );
            } else if (fromArr.size() == 2) {
              pool.get(to).add( fromArr );
            }

          } else if ( inst.equals("ORDER ASC") ) {
            pool.get(to).add( Orders.Order.ASC );

          } else if ( inst.equals("ORDER DESC") ) {
            pool.get(to).add( Orders.Order.DESC );


          } else if ( inst.equals("QUERY") ) {
            List fromArr = pool.get((Integer)from);
            if (0 == fromArr.size()) {
              pool.get(0).add( new HashSet() );
            } else {
              pool.get(0).add( fromArr.get(0) ); //copy one
            }


          } else if ( inst.equals("QUERY OPTION INDEX") ) {
            List fromArr = pool.get((Integer)from);
            sorts.addAll(fromArr);


          } else if ( inst.equals("REGEX") ) {
            List fromArr = pool.get((Integer)from);
            pool.get(to).addAll( fromArr ); //copy all

          } else if ( inst.equals("REGEX OPTION VALUE") ) {
            String s = (String)from;
            pool.get(to).add( s );

          } else if ( inst.equals("REGEX PATTERN VALUE") ) {
            String s = (String)from;
            s = s.substring(1, s.length() - 1);
            pool.get(to).add( s );


          } else if ( inst.equals("URL") ) {
            String s = (String)from;
            s = s.substring(1, s.length() - 1);
            pool.get(to).add( s );

          } else if ( inst.equals("URLS") ) {
            List fromArr = pool.get((Integer)from);
            LogicalOperationSet urls = new LogicalOperationSet(conn, manager);
            for (Object o : fromArr) {
              String url = (String)DomUtils.getSplitedByAnchor((String)o)[0];
              if (!DomUtils.isValidURL(url)) {
                continue;
              }
              int urlId = manager.register(url);
              urls.add( urlId );
            }
            urls = urls.getResponseCodeFiltered();
            urls = urls.getContentTypeFiltered();
            pool.get(to).add( urls );


          } else {
            throw new Exception("Unknown Class!");
          }

          logger.debug("--------");
        }
        conn.commit();

        Set<LazyLoader> result = new HashSet<LazyLoader>();
        Object rootObj = pool.get(0).get(0);
        if (rootObj instanceof LogicalOperationSet) {
          LogicalOperationSet set = (LogicalOperationSet)rootObj;
          for (Integer id : set) {
            LazyLoader loader = manager.getLazyLoader(conn, id);
            result.add(loader);
          }
        }

        LazyLoader[] resultArr = result.toArray(new LazyLoader[]{});
        //sort
        publish("Sorting...");
        for (Object o: sorts) {
          if (o instanceof Cond) {
            Cond cond = (Cond)o;
            Arrays.sort( resultArr, new Comparator_Cond(conn, cond) );
          } else {
            List sort = (List)o;
            Fields.Field field = (Fields.Field)sort.get(0);
            Orders.Order order = (Orders.Order)sort.get(1);
            if (field == Fields.Field.ID) {
              if (order == Orders.Order.ASC) {
                Arrays.sort( resultArr, new Comparator_ID_ASC() );
              } else {
                Arrays.sort( resultArr, new Comparator_ID_DESC() );
              }
            } else if (field == Fields.Field.URL) {
              if (order == Orders.Order.ASC) {
                Arrays.sort( resultArr, new Comparator_URL_ASC() );
              } else {
                Arrays.sort( resultArr, new Comparator_URL_DESC() );
              }
            } else if (field == Fields.Field.TITLE) {
              if (order == Orders.Order.ASC) {
                Arrays.sort( resultArr, new Comparator_TITLE_ASC() );
              } else {
                Arrays.sort( resultArr, new Comparator_TITLE_DESC() );
              }
            }
          }
        }
        //extract
        Set<String> mails = new HashSet<String>();
        List<Object[]> rows = new ArrayList<Object[]>();
        for (int i=0; i < resultArr.length; i++) {
          LazyLoader loader = resultArr[i];
          publish("Extracting Email Adresses (" + i + "/" + resultArr.length + ") " + loader.getUrl());
          rows.add(
            new Object[]{
              loader.getId(),
              loader.getFullUrl(),
              loader.getTitle(),
              loader.getText()
            }
          );
          InputStream in = null;
          try {
            in           = loader.getContent();
            State header = loader.getHeader();
            String charset = DomUtils.guessCharset(header, in);
            try {
              in.close();
            } catch (Exception e) {}

            in = loader.getContent();
            Set<String> newMails = DomUtils.extractMails(loader.getUrl(), in, charset);
            mails.addAll(newMails);
          } finally {
            if (null != in) {
              try {
                in.close();
              } catch (Exception e) {}
            }
          }
        }
        return new Object[]{rows, mails};
      }
      @Override
      protected void process(List<String> chunks) {
        String text = chunks.get(chunks.size() - 1);
        statusMessageLabel.setText((text == null) ? "" : text);
        //messageTimer.restart();
      }
      @Override
      protected void done() {
        publish("");
        try {
          Object[] result = get();
          List<Object[]> rows = (List<Object[]>)result[0];
          Set<String> mails   = (Set<String>)result[1];

          if (0 < rows.size()) {
            StringBuilder bf = new StringBuilder();
            javax.swing.table.DefaultTableModel model = (DefaultTableModel)resultTable.getModel();
            model.setRowCount(0);
            for (Object[] row: rows) {
              model.addRow(row);
            }
            for (String mail: mails) {
              bf.append( mail );
              bf.append( "\n" );
            }
            publish("Ready");
            setStringToJTextPane(resultPane, bf.toString());
            //apiPanel
            jButton1.setEnabled(true);
            jButton2.setEnabled(true);
            jComboBox1.setEnabled(true);
            titleTextField.setText("email adresses");
          }

        } catch (java.util.concurrent.CancellationException e) {
          publish("Cancelled");
        } catch (Exception e) {
          publish("Error");
          String error = Utils.ThrowableToString(e);
          logger.error(error);
          setStringToJTextPane(resultPane, error);
        }
      }
    }

    @Action
    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = CrawlQueryPadApp.getApplication().getMainFrame();
            aboutBox = new CrawlQueryPadAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        CrawlQueryPadApp.getApplication().show(aboutBox);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    mainPanel = new javax.swing.JPanel();
    jPanel1 = new javax.swing.JPanel();
    crawlButton = new javax.swing.JButton();
    urlButton = new javax.swing.JButton();
    depth1Button = new javax.swing.JButton();
    depth2Button = new javax.swing.JButton();
    setButton = new javax.swing.JButton();
    filterButton = new javax.swing.JButton();
    sortButton = new javax.swing.JButton();
    jSplitPane1 = new javax.swing.JSplitPane();
    jScrollPane1 = new javax.swing.JScrollPane();
    queryPane = new javax.swing.JTextPane();
    jSplitPane2 = new javax.swing.JSplitPane();
    jScrollPane2 = new javax.swing.JScrollPane();
    resultPane = new javax.swing.JTextPane();
    jSplitPane3 = new javax.swing.JSplitPane();
    jScrollPane4 = new javax.swing.JScrollPane();
    resultTable = new javax.swing.JTable();
    jScrollPane3 = new javax.swing.JScrollPane();
    instTable = new javax.swing.JTable();
    apiPanel = new javax.swing.JPanel();
    javax.swing.JSeparator statusPanelSeparator1 = new javax.swing.JSeparator();
    jButton1 = new javax.swing.JButton();
    jComboBox1 = new javax.swing.JComboBox();
    titleTextField = new javax.swing.JTextField();
    jButton2 = new javax.swing.JButton();
    jLabel1 = new javax.swing.JLabel();
    downloadWaitSpinner = new javax.swing.JSpinner();
    jSeparator1 = new javax.swing.JSeparator();
    jLabel2 = new javax.swing.JLabel();
    menuBar = new javax.swing.JMenuBar();
    javax.swing.JMenu fileMenu = new javax.swing.JMenu();
    javax.swing.JMenuItem exitMenuItem = new javax.swing.JMenuItem();
    javax.swing.JMenu helpMenu = new javax.swing.JMenu();
    javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
    statusPanel = new javax.swing.JPanel();
    javax.swing.JSeparator statusPanelSeparator = new javax.swing.JSeparator();
    statusMessageLabel = new javax.swing.JLabel();
    statusAnimationLabel = new javax.swing.JLabel();
    progressBar = new javax.swing.JProgressBar();

    mainPanel.setName("mainPanel"); // NOI18N
    mainPanel.setLayout(new java.awt.BorderLayout());

    jPanel1.setName("jPanel1"); // NOI18N

    org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(com.blogspot.rubyug.crawlquerypad.CrawlQueryPadApp.class).getContext().getResourceMap(CrawlQueryPadView.class);
    crawlButton.setText(resourceMap.getString("crawlButton.text")); // NOI18N
    crawlButton.setEnabled(false);
    crawlButton.setName("crawlButton"); // NOI18N

    urlButton.setText(resourceMap.getString("urlButton.text")); // NOI18N
    urlButton.setName("urlButton"); // NOI18N

    depth1Button.setText(resourceMap.getString("depth1Button.text")); // NOI18N
    depth1Button.setName("depth1Button"); // NOI18N

    depth2Button.setText(resourceMap.getString("depth2Button.text")); // NOI18N
    depth2Button.setName("depth2Button"); // NOI18N

    setButton.setText(resourceMap.getString("setButton.text")); // NOI18N
    setButton.setName("setButton"); // NOI18N

    filterButton.setText(resourceMap.getString("filterButton.text")); // NOI18N
    filterButton.setName("filterButton"); // NOI18N

    sortButton.setText(resourceMap.getString("sortButton.text")); // NOI18N
    sortButton.setName("sortButton"); // NOI18N

    javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
    jPanel1.setLayout(jPanel1Layout);
    jPanel1Layout.setHorizontalGroup(
      jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
        .addContainerGap()
        .addComponent(urlButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(setButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(filterButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(depth1Button)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(depth2Button)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(sortButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 297, Short.MAX_VALUE)
        .addComponent(crawlButton)
        .addContainerGap())
    );
    jPanel1Layout.setVerticalGroup(
      jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(crawlButton)
          .addComponent(urlButton)
          .addComponent(setButton)
          .addComponent(filterButton)
          .addComponent(depth1Button)
          .addComponent(depth2Button)
          .addComponent(sortButton))
        .addContainerGap())
    );

    mainPanel.add(jPanel1, java.awt.BorderLayout.PAGE_START);

    jSplitPane1.setBorder(null);
    jSplitPane1.setDividerLocation(120);
    jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
    jSplitPane1.setName("jSplitPane1"); // NOI18N

    jScrollPane1.setBorder(null);
    jScrollPane1.setName("jScrollPane1"); // NOI18N

    queryPane.setName("queryPane"); // NOI18N
    jScrollPane1.setViewportView(queryPane);

    jSplitPane1.setLeftComponent(jScrollPane1);

    jSplitPane2.setBorder(null);
    jSplitPane2.setDividerLocation(300);
    jSplitPane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
    jSplitPane2.setName("jSplitPane2"); // NOI18N

    jScrollPane2.setBorder(null);
    jScrollPane2.setName("jScrollPane2"); // NOI18N

    resultPane.setEditable(false);
    resultPane.setName("resultPane"); // NOI18N
    jScrollPane2.setViewportView(resultPane);

    jSplitPane2.setRightComponent(jScrollPane2);

    jSplitPane3.setBorder(null);
    jSplitPane3.setName("jSplitPane3"); // NOI18N

    jScrollPane4.setBorder(null);
    jScrollPane4.setName("jScrollPane4"); // NOI18N

    resultTable.setModel(new javax.swing.table.DefaultTableModel(
      new Object [][] {

      },
      new String [] {
        "id", "url", "title", "text"
      }
    ) {
      boolean[] canEdit = new boolean [] {
        false, false, false, false
      };

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit [columnIndex];
      }
    });
    resultTable.setName("resultTable"); // NOI18N
    jScrollPane4.setViewportView(resultTable);
    resultTable.getColumnModel().getColumn(0).setResizable(false);
    resultTable.getColumnModel().getColumn(0).setPreferredWidth(20);
    resultTable.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("resultTable.columnModel.title5")); // NOI18N
    resultTable.getColumnModel().getColumn(1).setHeaderValue(resourceMap.getString("resultTable.columnModel.title0")); // NOI18N
    resultTable.getColumnModel().getColumn(2).setHeaderValue(resourceMap.getString("resultTable.columnModel.title2")); // NOI18N
    resultTable.getColumnModel().getColumn(3).setHeaderValue(resourceMap.getString("resultTable.columnModel.title4")); // NOI18N

    jSplitPane3.setBottomComponent(jScrollPane4);

    jScrollPane3.setBorder(null);
    jScrollPane3.setName("jScrollPane3"); // NOI18N

    instTable.setModel(new javax.swing.table.DefaultTableModel(
      new Object [][] {

      },
      new String [] {
        "id", "Instruction", "Op1", "Op2"
      }
    ) {
      boolean[] canEdit = new boolean [] {
        false, false, false, false
      };

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit [columnIndex];
      }
    });
    instTable.setName("instTable"); // NOI18N
    instTable.getTableHeader().setReorderingAllowed(false);
    jScrollPane3.setViewportView(instTable);
    instTable.getColumnModel().getColumn(0).setResizable(false);
    instTable.getColumnModel().getColumn(0).setPreferredWidth(20);
    instTable.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("instTable.columnModel.title0")); // NOI18N
    instTable.getColumnModel().getColumn(1).setHeaderValue(resourceMap.getString("instTable.columnModel.title1")); // NOI18N
    instTable.getColumnModel().getColumn(2).setResizable(false);
    instTable.getColumnModel().getColumn(2).setPreferredWidth(20);
    instTable.getColumnModel().getColumn(2).setHeaderValue(resourceMap.getString("instTable.columnModel.title2")); // NOI18N
    instTable.getColumnModel().getColumn(3).setHeaderValue(resourceMap.getString("instTable.columnModel.title3")); // NOI18N

    jSplitPane3.setLeftComponent(jScrollPane3);

    jSplitPane2.setTopComponent(jSplitPane3);

    jSplitPane1.setBottomComponent(jSplitPane2);

    mainPanel.add(jSplitPane1, java.awt.BorderLayout.CENTER);

    apiPanel.setName("apiPanel"); // NOI18N

    statusPanelSeparator1.setName("statusPanelSeparator1"); // NOI18N

    jButton1.setText(resourceMap.getString("jButton1.text")); // NOI18N
    jButton1.setEnabled(false);
    jButton1.setMaximumSize(new java.awt.Dimension(70, 23));
    jButton1.setMinimumSize(new java.awt.Dimension(70, 23));
    jButton1.setName("jButton1"); // NOI18N

    jComboBox1.setEnabled(false);
    jComboBox1.setName("jComboBox1"); // NOI18N

    titleTextField.setText(resourceMap.getString("titleTextField.text")); // NOI18N
    titleTextField.setName("titleTextField"); // NOI18N

    jButton2.setText(resourceMap.getString("jButton2.text")); // NOI18N
    jButton2.setEnabled(false);
    jButton2.setMaximumSize(new java.awt.Dimension(70, 23));
    jButton2.setMinimumSize(new java.awt.Dimension(70, 23));
    jButton2.setName("jButton2"); // NOI18N

    jLabel1.setText(resourceMap.getString("jLabel1.text")); // NOI18N
    jLabel1.setName("jLabel1"); // NOI18N

    downloadWaitSpinner.setModel(new javax.swing.SpinnerNumberModel(2, 0, 360, 1));
    downloadWaitSpinner.setName("downloadWaitSpinner"); // NOI18N

    jSeparator1.setName("jSeparator1"); // NOI18N

    jLabel2.setText(resourceMap.getString("jLabel2.text")); // NOI18N
    jLabel2.setName("jLabel2"); // NOI18N

    javax.swing.GroupLayout apiPanelLayout = new javax.swing.GroupLayout(apiPanel);
    apiPanel.setLayout(apiPanelLayout);
    apiPanelLayout.setHorizontalGroup(
      apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(apiPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 687, Short.MAX_VALUE)
          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, apiPanelLayout.createSequentialGroup()
            .addComponent(jLabel2)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(titleTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 279, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, 192, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, apiPanelLayout.createSequentialGroup()
            .addComponent(jLabel1)
            .addGap(12, 12, 12)
            .addComponent(downloadWaitSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)))
        .addContainerGap())
      .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(statusPanelSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 711, Short.MAX_VALUE))
    );
    apiPanelLayout.setVerticalGroup(
      apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(apiPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(jLabel1)
          .addComponent(downloadWaitSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(titleTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jLabel2))
        .addContainerGap())
      .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, apiPanelLayout.createSequentialGroup()
          .addComponent(statusPanelSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addContainerGap(73, Short.MAX_VALUE)))
    );

    mainPanel.add(apiPanel, java.awt.BorderLayout.PAGE_END);

    menuBar.setName("menuBar"); // NOI18N

    fileMenu.setText(resourceMap.getString("fileMenu.text")); // NOI18N
    fileMenu.setName("fileMenu"); // NOI18N

    javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(com.blogspot.rubyug.crawlquerypad.CrawlQueryPadApp.class).getContext().getActionMap(CrawlQueryPadView.class, this);
    exitMenuItem.setAction(actionMap.get("quit")); // NOI18N
    exitMenuItem.setName("exitMenuItem"); // NOI18N
    fileMenu.add(exitMenuItem);

    menuBar.add(fileMenu);

    helpMenu.setText(resourceMap.getString("helpMenu.text")); // NOI18N
    helpMenu.setName("helpMenu"); // NOI18N

    aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
    aboutMenuItem.setName("aboutMenuItem"); // NOI18N
    helpMenu.add(aboutMenuItem);

    menuBar.add(helpMenu);

    statusPanel.setName("statusPanel"); // NOI18N

    statusPanelSeparator.setName("statusPanelSeparator"); // NOI18N

    statusMessageLabel.setName("statusMessageLabel"); // NOI18N

    statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
    statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N

    progressBar.setName("progressBar"); // NOI18N

    javax.swing.GroupLayout statusPanelLayout = new javax.swing.GroupLayout(statusPanel);
    statusPanel.setLayout(statusPanelLayout);
    statusPanelLayout.setHorizontalGroup(
      statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(statusPanelSeparator, javax.swing.GroupLayout.DEFAULT_SIZE, 711, Short.MAX_VALUE)
      .addGroup(statusPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addComponent(statusMessageLabel)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 536, Short.MAX_VALUE)
        .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(statusAnimationLabel)
        .addContainerGap())
    );
    statusPanelLayout.setVerticalGroup(
      statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(statusPanelLayout.createSequentialGroup()
        .addComponent(statusPanelSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(statusMessageLabel)
          .addComponent(statusAnimationLabel)
          .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addGap(3, 3, 3))
    );

    setComponent(mainPanel);
    setMenuBar(menuBar);
    setStatusBar(statusPanel);
  }// </editor-fold>//GEN-END:initComponents

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel apiPanel;
  private javax.swing.JButton crawlButton;
  private javax.swing.JButton depth1Button;
  private javax.swing.JButton depth2Button;
  private javax.swing.JSpinner downloadWaitSpinner;
  private javax.swing.JButton filterButton;
  private javax.swing.JTable instTable;
  private javax.swing.JButton jButton1;
  private javax.swing.JButton jButton2;
  private javax.swing.JComboBox jComboBox1;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JScrollPane jScrollPane3;
  private javax.swing.JScrollPane jScrollPane4;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JSplitPane jSplitPane1;
  private javax.swing.JSplitPane jSplitPane2;
  private javax.swing.JSplitPane jSplitPane3;
  private javax.swing.JPanel mainPanel;
  private javax.swing.JMenuBar menuBar;
  private javax.swing.JProgressBar progressBar;
  private javax.swing.JTextPane queryPane;
  private javax.swing.JTextPane resultPane;
  private javax.swing.JTable resultTable;
  private javax.swing.JButton setButton;
  private javax.swing.JButton sortButton;
  private javax.swing.JLabel statusAnimationLabel;
  private javax.swing.JLabel statusMessageLabel;
  private javax.swing.JPanel statusPanel;
  private javax.swing.JTextField titleTextField;
  private javax.swing.JButton urlButton;
  // End of variables declaration//GEN-END:variables

    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;

    private JDialog aboutBox;

    private final UndoManager undomanager;
    private final StyledDocument queryPaneDoc;
    private SimpleNode query = null;
    static JdbcConnectionPool connectionPool = null;
    private Connection conn = null;
    private CrawlExcecuteWorker worker = null;
    private boolean workerStop = false;
    private List<String> extensions = null;
}
