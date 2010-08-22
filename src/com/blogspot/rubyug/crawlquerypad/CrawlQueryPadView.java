/*
 * CrawlQueryPadView.java
 */

package com.blogspot.rubyug.crawlquerypad;

import com.blogspot.rubyug.crawlquerypad.condition.*;
import com.blogspot.rubyug.crawlquerypad.comparators.*;

import com.blogspot.rubyug.crawlquery.*;
import com.blogspot.rubyug.crawlquery.utils.*;
import com.blogspot.rubyug.crawlquery.utils.CQCompiler.CQCompileResult;

import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.text.*;
import javax.swing.event.*;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.undo.*;
import javax.swing.table.DefaultTableModel;

import java.sql.*;
import java.net.*;

import org.python.core.*;
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
      logger.trace("top: " + top);
      if(top.startsWith("file:/")){
        top = top.substring(5, top.toLowerCase().indexOf(".jar!/") + 4);
        logger.trace(" -> " + top);
        if(top.matches("^/[A-Z]:/.*")){
          top = top.substring(1);
          logger.trace(" -> " + top);
        }
        top = top.substring(0, top.lastIndexOf("/"));
        logger.trace(" -> " + top);
        if(top.matches("^[A-Z]:")){
          top += "/";
          logger.trace(" -> " + top);
        }
        top = top.replace("\\", "/");
        logger.trace(" -> " + top);
      }else{
        top = ".";
        logger.trace(" -> " + top);
      }
      logger.debug("getCurrent(): " + top);
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

      logger.info("getting database connection");
      try {
        conn = DB.getConnection();
      } catch(Exception e) {
        logger.error(Utils.ThrowableToString(e));
        app.exit();
      }
      
      //exts
      logger.info("extension initialize");
      extensions_of_save   = new ArrayList<String>();
      extensions_of_render = new ArrayList<String>();
      String extensionDirPath = getCurrent() + "/extension";
      if (new File(extensionDirPath).exists()) {
        PythonInterpreter pyi = new PythonInterpreter();
        pyi.exec("import sys");
        String extensionLibDirPath = extensionDirPath + "/Lib";
        if (new File(extensionLibDirPath).exists()) {
          logger.info("sys.path.append('" + extensionLibDirPath + "')");
          pyi.exec("sys.path.append('" + extensionLibDirPath + "')");
        }
        String extensionPluginDirPath = extensionDirPath + "/plugin";
        if (new File(extensionPluginDirPath).exists()) {
          logger.info("sys.path.append('" + extensionPluginDirPath + "')");
          pyi.exec("sys.path.append('" + extensionPluginDirPath + "')");
        }

        String pluginDirPath = extensionPluginDirPath + "/save";
        File pluginDir = new File(pluginDirPath);
        if (pluginDir.exists() && pluginDir.isDirectory()) {
          for (File plugin: pluginDir.listFiles()) {
            if (!plugin.exists() ||
                !plugin.isDirectory()) {
                continue;
            }
            logger.info("try to register a save plugin: " + plugin.getAbsolutePath());
            try {
              String pluginPath = pluginDir.getName() + "." + plugin.getName() + ".plugin";
              logger.debug("path: " + pluginPath);
              pyi.exec("import " + pluginPath);
              PyObject name = pyi.eval(pluginPath + ".ext_name()");
              PyObject desc = pyi.eval(pluginPath + ".ext_description()");
              logger.info("plugin is registered; name: " + name + " description: " + desc);
              extensions_of_save.add(pluginPath);
              DefaultComboBoxModel model = (DefaultComboBoxModel)saveComboBox.getModel();
              model.addElement(name + ": " + desc);
            } catch(Exception e) {
              logger.info("plugin not registered: " + Utils.ThrowableToString(e));
            }
          }
        }
        pluginDirPath = extensionPluginDirPath + "/render";
        pluginDir = new File(pluginDirPath);
        if (pluginDir.exists() && pluginDir.isDirectory()) {
          for (File plugin: pluginDir.listFiles()) {
            if (!plugin.exists() ||
                !plugin.isDirectory()) {
                continue;
            }
            logger.info("try to register a render plugin: " + plugin.getAbsolutePath());
            try {
              String pluginPath = pluginDir.getName() + "." + plugin.getName() + ".plugin";
              logger.debug("path: " + pluginPath);
              pyi.exec("import " + pluginPath);
              PyObject name = pyi.eval(pluginPath + ".ext_name()");
              PyObject desc = pyi.eval(pluginPath + ".ext_description()");
              logger.info("plugin is registered; name: " + name + " description: " + desc);
              extensions_of_render.add(pluginPath);
              DefaultComboBoxModel model = (DefaultComboBoxModel)renderComboBox.getModel();
              model.addElement(name + ": " + desc);
            } catch(Exception e) {
              logger.info("plugin not registered: " + Utils.ThrowableToString(e));
            }
          }
        }
      }

      saveSubmitButton.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            try {
              logger.info("call: " + saveComboBox.getSelectedItem());
              int index = saveComboBox.getSelectedIndex();
              String pluginPath = extensions_of_save.get(index);
              PythonInterpreter pyi = new PythonInterpreter();
              pyi.exec("import " + pluginPath);
              pyi.exec("reload(" + pluginPath + ")");
              PyObject ext_name = pyi.eval(pluginPath + ".ext_name()");
              ExtensionAPI api = new ExtensionAPI(ext_name.toString());
              Map data = api.getData();
              logger.debug("title: " + resultTitle);
              logger.debug("text: " + resultTextFile.getAbsolutePath());
              logger.debug("query: " + worker.getQueryString());
              data.put("title", titleTextField.getText());
              data.put("text", resultTextFile);
              data.put("query", worker.getQueryString());
              pyi.set("____API____", api);
              String result = pyi.eval(pluginPath + ".call(____API____)").toString();
              logger.info("result: " + result);
              //
              statusMessageLabel.setText("save plugin(" + ext_name + ") returned: " + result);
            } catch (Exception ex) {
              //textareaに書き出さないほうがいい
              statusMessageLabel.setText("PluginError: error occurred when saving");
              logger.error("PluginError: error occurred when saving: " + Utils.ThrowableToString(ex));
            }
          }
        });

      saveResetButton.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            try {
              logger.info("clear state: " + saveComboBox.getSelectedItem());
              int index = saveComboBox.getSelectedIndex();
              String pluginPath = extensions_of_save.get(index);
              PythonInterpreter pyi = new PythonInterpreter();
              pyi.exec("import " + pluginPath);
              PyObject ext_name = pyi.eval(pluginPath + ".ext_name()");
              ExtensionAPI api = new ExtensionAPI(ext_name.toString());
              api.setState(new State());
              logger.info("success");
              //
              statusMessageLabel.setText("State is cleared");
            } catch (Exception ex) {
              logger.error("PluginError: error occurred when clearing a save plugin state: " + Utils.ThrowableToString(ex));
            }
          }
        });

      renderResetButton.addActionListener( new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            try {
              logger.info("clear state: " + renderComboBox.getSelectedItem());
              int index = renderComboBox.getSelectedIndex();
              String pluginPath = extensions_of_render.get(index);
              PythonInterpreter pyi = new PythonInterpreter();
              pyi.exec("import " + pluginPath);
              PyObject ext_name = pyi.eval(pluginPath + ".ext_name()");
              ExtensionAPI api = new ExtensionAPI(ext_name.toString());
              api.setState(new State());
              logger.info("success");
              //
              statusMessageLabel.setText("State is cleared");
            } catch (Exception ex) {
              logger.error("PluginError: error occurred when clearing a render plugin state: " + Utils.ThrowableToString(ex));
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
      int maxRetry = (Integer)maxRetrySpinner.getValue();
      LazyLoader.setMaxRetry(maxRetry);
      maxRetrySpinner.addChangeListener( new ChangeListener() {
          public void stateChanged(ChangeEvent e) {
            JSpinner spinner = (JSpinner)e.getSource();
            int maxRetry = (Integer)spinner.getValue();
            LazyLoader.setMaxRetry(maxRetry);
          }
        });


      StringBuilder sb = new StringBuilder();
      int c;
      try {
        int a = System.in.available();
        if (a <= 0) {
          logger.info("no standard input");
        } else {
          InputStreamReader isr = new InputStreamReader(System.in);
          for (int i = 0; i < a; i++) {
            c = System.in.read();
            sb.append((char)c);
          }
        }
      } catch(Exception e) {}
      if (0 < sb.length()) {
        logger.info("standard input: " + sb.toString());
        setStringToJTextPane(queryPane, sb.toString());
        invokeQueryParse(true);
      }

      //restore state
      logger.info("restore state");
      try {
        State global = DB.Global.getState(conn, "window-state");
        //download wait
        int value;
        value = (int)global.getFirstOr("download-wait", -1L);
        logger.debug("download-wait: " + value);
        if (-1 < value) {
          downloadWaitSpinner.setValue(value);
        }
        //max retry
        value = (int)global.getFirstOr("max-retry", -1L);
        logger.debug("max-retry: " + value);
        if (-1 < value) {
          maxRetrySpinner.setValue(value);
        }
        //plugin
        int index;
        String pluginPath;
        pluginPath = global.getFirstOr("plugin-render-selected", "");
        index      = extensions_of_render.indexOf(pluginPath);
        logger.debug("plugin-render-selected: " + pluginPath);
        if (-1 < index) {
          renderComboBox.setSelectedIndex(index);
        }
        pluginPath = global.getFirstOr("plugin-save-selected", "");
        index      = extensions_of_save.indexOf(pluginPath);
        logger.debug("plugin-save-selected: " + pluginPath);
        if (-1 < index) {
          saveComboBox.setSelectedIndex(index);
        }
      } catch (Exception e) {
        logger.error("error occurred when restore states: " + Utils.ThrowableToString(e));
      }

      //save state
      getFrame().addWindowListener(new WindowAdapter(){
        @Override
        public void windowClosing(WindowEvent event) {
          try {
            State global = DB.Global.getState(conn, "window-state");
            //download wait
            Integer value;
            value = (Integer)downloadWaitSpinner.getValue();
            logger.debug("download-wait: " + value);
            global.set("download-wait", value.toString());
            //max retry
            value = (Integer)maxRetrySpinner.getValue();
            logger.debug("max-retry: " + value);
            global.set("max-retry", value.toString());
            //plugin render
            int index;
            String pluginPath;
            index      = renderComboBox.getSelectedIndex();
            pluginPath = extensions_of_render.get(index);
            logger.debug("plugin-render-selected: " + pluginPath);
            global.set("plugin-render-selected", pluginPath);
            //plugin save
            index      = saveComboBox.getSelectedIndex();
            pluginPath = extensions_of_save.get(index);
            logger.debug("plugin-save-selected: " + pluginPath);
            global.set("plugin-save-selected", pluginPath);
            
            DB.Global.setState(conn, "window-state", global);
            conn.commit();
            conn.close();
          } catch(Exception e) {
            logger.error("error occurred when save states: " + Utils.ThrowableToString(e));
          }

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
    void setStringToJTextArea(JTextArea textArea, String text) {
      textArea.setText(text);
      textArea.setCaretPosition(0);
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
      //apiPanel
      saveSubmitButton.setEnabled(false);
      //clear
      javax.swing.table.DefaultTableModel model;
      model = (DefaultTableModel)instTable.getModel();
      model.setRowCount(0);
      model = (DefaultTableModel)resultTable.getModel();
      model.setRowCount(0);
      //
      resultTextFile = null;
      resultTitle    = null;
      //
      resultTextArea.setText("");
      titleTextField.setText("");

      String queryString = queryPane.getText();
      QueryParser parser = new QueryParser( new StringReader(queryString) );
      logger.debug("Compiling ...");
      CQCompileResult result;
      List<Object[]> instructions;
      List<Throwable> throwables;
      try {
        query = parser.parse();
        query.dump("");
        result = CQCompiler.compile(query);
        instructions = result.getInstructions();
        throwables   = result.getThrowables();
        if (0 < throwables.size()) {
          StringBuilder sb = new StringBuilder();
          for (Throwable t: throwables) {
            String error = Utils.ThrowableToString(t);
            sb.append("[" + throwables.indexOf(t) + "/" + throwables.size() + "]\n");
            sb.append(error);
          }
          logger.error("compiler returned " + throwables.size() + " throwables: " + sb.toString());
          throw new Exception("compiler returned " + throwables.size() + " throwables: " + sb.toString());
        }
      } catch(Exception e) {
        String error = Utils.ThrowableToString(e);
        statusMessageLabel.setText("Query syntax error has occurred!");
        logger.error("Query syntax error has occurred!; " + error);
        setStringToJTextArea(resultTextArea, error);
        return;
      }
      statusMessageLabel.setText("Compile finished");
      logger.debug("Compile finished");

      model = (DefaultTableModel)instTable.getModel();
      model.setRowCount(0);
      for (Object[] arr: instructions) {
        model.addRow(arr);
      }

      if ( instructions.size() < 2 ||  //empty or
           0 != throwables.size()      //has error
         ) {
        crawlButton.setEnabled(false);
      } else {
        crawlButton.setEnabled(true);
      }

      if ( 0 == throwables.size() && //no error
           doCrawl
         ) {
        logger.debug("start crawling");
        worker = new CrawlExcecuteWorker(queryString, result);
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
          worker.cancel(true);
        }
        invokeQueryParse(false);
      }
      public void removeUpdate(DocumentEvent e) {
        if (null != worker && !worker.isDone() && !worker.isCancelled()) {
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
    public class PluginErrorException extends Exception {
      public PluginErrorException(Throwable t) {
        super(t);
      }
    }
    public class CrawlExcecuteWorker extends SwingWorker<List<Object[]>, String> {
      protected Logger logger = LoggerFactory.getLogger(CrawlExcecuteWorker.class);
      String queryString = null;
      CQCompileResult compileResult = null;
      List<Object[]> instructions = null;
      public CrawlExcecuteWorker(String queryString, CQCompileResult result) {
        this.queryString  = queryString;
        this.compileResult = result;
        this.instructions  = result.getInstructions();
      }
      public String getQueryString() {
        return this.queryString;
      }
      public void publish(String s){
        super.publish(s);
      }
      @Override
      protected List<Object[]> doInBackground() throws Exception {
        publish("initializing...");
        setProgress(0);

        List<List> pool = compileResult.getPool();
        //Conds
        List<Cond> filters = new ArrayList<Cond>();
        //Sort
        List sorts = new ArrayList();
        //default sort
        List defaultSort = new ArrayList();
        defaultSort.add( Fields.Field.ID );
        defaultSort.add( Orders.Order.ASC );
        sorts.add( 0, defaultSort );

        if (null == conn || conn.isClosed()) {
          logger.warn("Missing connection!");
          logger.info("tring to get a new connection ...");
          conn = DB.getConnection();
          logger.info("success");
        }

        LazyLoaderManager manager = new LazyLoaderManager(conn);

        for (Object[] instruction : instructions) {
          Integer id           = (Integer)         instruction[0];
          CQCompiler.Inst inst = (CQCompiler.Inst) instruction[1];
          Integer op1          = (Integer)         instruction[2];
          Object  op2          =                   instruction[3];

          if (worker.isCancelled()) {
            logger.info("worker is cancelled");
            publish("Cancelled");
            throw new java.util.concurrent.CancellationException();
          }

          publish( "(" + id + "/" + instructions.size() + ")" + " [" + inst + "] " + op1 + ", " + op2 );
          setProgress(100 * id / instructions.size());

          logger.debug("--DUMP--");
          logger.debug("id: " + id);
          logger.debug("inst: " + inst);
          logger.debug("op1: " + op1);
          if (op2 instanceof Integer) { //adress
            logger.debug("op2: " + op2);
            List fArr = pool.get((Integer)op2);
            for (Object o : fArr) {
              logger.debug(o.toString());
            }
          } else if (op2 instanceof String) { //value
            logger.debug("op2(value): '" + op2 + "'");
          } else {
            logger.debug("op2(null): -");
          }
          logger.debug("--------");

          if ( inst == CQCompiler.Inst.CONDITION ) {
            List fromArr = pool.get((Integer)op2);
            pool.get(op1).add( new Cond((ICondition)fromArr.get(0)) );

          } else if ( inst == CQCompiler.Inst.CONDITION_AND ) {
            List fromArr = pool.get((Integer)op2);
            pool.get(op1).add( new CondAND((ICondition)fromArr.get(0), (ICondition)fromArr.get(1)) );

          } else if ( inst == CQCompiler.Inst.CONDITION_MATCH ) {
            List fromArr = pool.get((Integer)op2);
            Fields.Field field = null;
            String pattern     = null;
            String option      = null;
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
            pool.get(op1).add( new CondMatch(field, pattern, option) );

          } else if ( inst == CQCompiler.Inst.CONDITION_OR ) {
            List fromArr = pool.get((Integer)op2);
            pool.get(op1).add( new CondOR((ICondition)fromArr.get(0), (ICondition)fromArr.get(1)) );

          } else if ( inst == CQCompiler.Inst.CONDITION_UNARY_NOT ) {
            List fromArr = pool.get((Integer)op2);
            pool.get(op1).add( new CondNOT((ICondition)fromArr.get(0)) );


          } else if ( inst == CQCompiler.Inst.CRAWL ) {
            List fromArr = pool.get((Integer)op2);
            pool.get(op1).add( fromArr.get(0) ); //copy one
            //reset filters
            filters.clear();

          } else if ( inst == CQCompiler.Inst.CRAWL_EXECUTE ) {
            List fromArr = pool.get((Integer)op2);
            List toArr   = pool.get(op1);
            int depth = (Integer)fromArr.get(0);
            LogicalOperationSet set = (LogicalOperationSet)toArr.get(0);
            set = set.getCrawled(depth, filters);
            toArr.clear();
            toArr.add(set);

          } else if ( inst == CQCompiler.Inst.CRAWL_FILTER ) {
            List fromArr = pool.get((Integer)op2);
            List toArr   = pool.get(op1);
            LogicalOperationSet set = (LogicalOperationSet)toArr.get(0);
            LogicalOperationSet newSet = set.getCondsFiltered(fromArr);
            toArr.clear();
            toArr.add(newSet);

          } else if ( inst == CQCompiler.Inst.CRAWL_OP_DIFFERENCE ) {
            List fromArr = pool.get((Integer)op2);
            List toArr   = pool.get(op1);
            LogicalOperationSet set1 = (LogicalOperationSet)fromArr.get(0);
            LogicalOperationSet set2 = (LogicalOperationSet)fromArr.get(1);
            toArr.add(set1.getDifference(set2));

          } else if ( inst == CQCompiler.Inst.CRAWL_OP_INTERSECTION ) {
            List fromArr = pool.get((Integer)op2);
            List toArr   = pool.get(op1);
            LogicalOperationSet set1 = (LogicalOperationSet)fromArr.get(0);
            LogicalOperationSet set2 = (LogicalOperationSet)fromArr.get(1);
            toArr.add(set1.getIntersection(set2));

          } else if ( inst == CQCompiler.Inst.CRAWL_OP_SYMMETRIC_DIFFERENCE ) {
            List fromArr = pool.get((Integer)op2);
            List toArr   = pool.get(op1);
            LogicalOperationSet set1 = (LogicalOperationSet)fromArr.get(0);
            LogicalOperationSet set2 = (LogicalOperationSet)fromArr.get(1);
            toArr.add(set1.getSymmetricDifference(set2));

          } else if ( inst == CQCompiler.Inst.CRAWL_OP_UNION ) {
            List fromArr = pool.get((Integer)op2);
            List toArr   = pool.get(op1);
            LogicalOperationSet set1 = (LogicalOperationSet)fromArr.get(0);
            LogicalOperationSet set2 = (LogicalOperationSet)fromArr.get(1);
            toArr.add(set1.getUnion(set2));

          } else if ( inst == CQCompiler.Inst.CRAWL_SET_CONDITION ) {
            List fromArr = pool.get((Integer)op2);
            filters.add((Cond)fromArr.get(0));


          } else if ( inst == CQCompiler.Inst.DEPTH_VALUE ) {
            int depth = Integer.parseInt( (String)op2 );
            pool.get(op1).add( depth );

          } else if ( inst == CQCompiler.Inst.FIELD_BODY ) {
            pool.get(op1).add( Fields.Field.BODY );

          } else if ( inst == CQCompiler.Inst.FIELD_ID ) {
            pool.get(op1).add( Fields.Field.ID );

          } else if ( inst == CQCompiler.Inst.FIELD_TEXT ) {
            pool.get(op1).add( Fields.Field.TEXT );

          } else if ( inst == CQCompiler.Inst.FIELD_TITLE ) {
            pool.get(op1).add( Fields.Field.TITLE );

          } else if ( inst == CQCompiler.Inst.FIELD_URL ) {
            pool.get(op1).add( Fields.Field.URL );


          } else if ( inst == CQCompiler.Inst.INDEX_ORDER ) {
            List fromArr = pool.get((Integer)op2);
            if (fromArr.size() == 1) {
              fromArr.add( 0, Fields.Field.URL ); //default
              pool.get(op1).add( fromArr );
            } else if (fromArr.size() == 2) {
              pool.get(op1).add( fromArr );
            }

          } else if ( inst == CQCompiler.Inst.ORDER_ASC ) {
            pool.get(op1).add( Orders.Order.ASC );

          } else if ( inst == CQCompiler.Inst.ORDER_DESC ) {
            pool.get(op1).add( Orders.Order.DESC );


          } else if ( inst == CQCompiler.Inst.QUERY ) {
            List fromArr = pool.get((Integer)op2);
            if (0 == fromArr.size()) {
              pool.get(0).add( new HashSet() );
            } else {
              pool.get(0).add( fromArr.get(0) ); //copy one
            }


          } else if ( inst == CQCompiler.Inst.QUERY_OPTION_INDEX ) {
            List fromArr = pool.get((Integer)op2);
            sorts.addAll(fromArr);


          } else if ( inst == CQCompiler.Inst.REGEX ) {
            List fromArr = pool.get((Integer)op2);
            pool.get(op1).addAll( fromArr ); //copy all

          } else if ( inst == CQCompiler.Inst.REGEX_OPTION_VALUE ) {
            String s = (String)op2;
            pool.get(op1).add( s );

          } else if ( inst == CQCompiler.Inst.REGEX_PATTERN_VALUE ) {
            String s = (String)op2;
            pool.get(op1).add( s );


          } else if ( inst == CQCompiler.Inst.URL ) {
            String s = (String)op2;
            pool.get(op1).add( s );

          } else if ( inst == CQCompiler.Inst.URLS ) {
            List fromArr = pool.get((Integer)op2);
            LogicalOperationSet urls = new LogicalOperationSet(conn, manager, this);
            for (Object o : fromArr) {
              String url = (String)DomUtils.getSplitedByAnchor((String)o)[0];
              if (!DomUtils.isValidURL(url)) {
                logger.debug("invalid url: " + url);
                continue;
              }
              int urlId = manager.register(url);
              urls.add( urlId );
              logger.debug("urls.add id: " + urlId + " url: " + url);
            }
            //condによるフィルタリングは行わない・行えない
            urls = urls.getResponseCodeFiltered();
            urls = urls.getContentTypeFiltered();
            pool.get(op1).add( urls );

          }
        }
        conn.commit();
        logger.debug("cache is committed");

        publish("Creating result ...");
        logger.debug("Creating result ...");
        Set<LazyLoader> result = new HashSet<LazyLoader>();
        Object rootObj = pool.get(0).get(0);
        if (rootObj instanceof LogicalOperationSet) {
          if (worker.isCancelled()) {
            logger.info("worker is cancelled");
            publish("Cancelled");
            throw new java.util.concurrent.CancellationException();
          }
          LogicalOperationSet set = (LogicalOperationSet)rootObj;
          for (Integer id : set) {
            LazyLoader loader = manager.getLazyLoader(conn, id);
            result.add(loader);
          }
        }

        LazyLoader[] resultArr = result.toArray(new LazyLoader[]{});
        //sort
        publish("Sorting ...");
        logger.debug("Sorting ...");
        for (Object o: sorts) {
          if (worker.isCancelled()) {
            logger.info("worker is cancelled");
            publish("Cancelled");
            throw new java.util.concurrent.CancellationException();
          }
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
        //
        publish("Rendering ...");
        logger.debug("Rendering ...");
        try {
          logger.info("call: " + renderComboBox.getSelectedItem());
          int index = renderComboBox.getSelectedIndex();
          String pluginPath = extensions_of_render.get(index);
          PythonInterpreter pyi = new PythonInterpreter();
          pyi.exec("import " + pluginPath);
          pyi.exec("reload(" + pluginPath + ")");
          PyObject ext_name = pyi.eval(pluginPath + ".ext_name()");
          ExtensionAPI api = new ExtensionAPI(ext_name.toString());
          Map data = api.getData();
          data.put("results", resultArr);
          data.put("query",   worker.getQueryString());
          data.put("worker",  this);
          pyi.set("____API____", api);
          Object ret = pyi.eval(pluginPath + ".call(____API____)");
          if (ret instanceof PyTuple) {
            PyTuple retTuple = (PyTuple)ret;
            if (2 == retTuple.__len__()) {
              resultTitle    = (String)retTuple.get(0);
              resultTextFile = (File)retTuple.get(1);
            } else {
              throw new Exception("invalid result");
            }
          } else {
            if (ret instanceof PyString) {
              String message = ret.toString();
              logger.debug("Plugin returned a message: " + message);
              if (message.equals("CANCELLED")) {
                //exceptionがマトモに取れないので、plugin内でキャンセルを検出したら文字列を返す
                logger.info("worker is cancelled");
                publish("Cancelled");
                worker.cancel(true); //プラグインがisCancelled()を検出せず、このメッセージを返したときのため
                throw new java.util.concurrent.CancellationException();
              } else {
                logger.debug("Unknown message!");
              }
            } else {
              throw new Exception("Plugin not returned PyTuple Object");
            }
          }
          logger.info("render plugin(" + ext_name + ") returned valid result");
        } catch (Exception ex) {
          logger.error("Error occurred when rendering");
          publish("Error occurred when rendering");
          throw new PluginErrorException(ex);
        }
        //
        publish("Building GUI data ...");
        logger.debug("Building GUI data ...");
        List<Object[]> rows = new ArrayList<Object[]>();
        for (int i=0; i < resultArr.length; i++) {
          if (worker.isCancelled()) {
            logger.info("worker is cancelled");
            publish("Cancelled");
            throw new java.util.concurrent.CancellationException();
          }
          LazyLoader loader = resultArr[i];
          String title = loader.getTitle();
          if (null != title && 100 < title.length()) {
            title = title.substring(0, 100); //切り捨て
          }
          String text  = loader.getText();
          if (null != text && 100 < text.length()) {
            text = text.substring(0, 100); //切り捨て
          }
          rows.add(
            new Object[]{
              loader.getId(),
              loader.getFullUrl(),
              title,
              text
            }
          );
        }
        return rows;
      }
      @Override
      protected void process(List<String> chunks) {
        String text = chunks.get(chunks.size() - 1);
        statusMessageLabel.setText((text == null) ? "" : text);
      }
      @Override
      protected void done() {
        publish("Done");
        logger.debug("Done");
        try {
          List<Object[]> rows = get();
          
          if (0 < rows.size()) {
            //結果が返っているので、resultTitle, resultTextFileも正常に保存されている。
            javax.swing.table.DefaultTableModel model = (DefaultTableModel)resultTable.getModel();
            model.setRowCount(0);
            for (Object[] row: rows) {
              model.addRow(row);
            }
            //result
            titleTextField.setText(resultTitle);
            String text = "";
            InputStream in = null;
            try {
              in = new FileInputStream(resultTextFile);
              text = Utils.InputStreamToString(in, "utf-8");
            } catch (Exception e) {
              logger.error(Utils.ThrowableToString(e));
            } finally {
              if (null != in) {
                try {
                  in.close();
                } catch (Exception e) {}
              }
            }
            setStringToJTextArea(resultTextArea, text);
            publish("Ready");
            logger.debug("Ready");
            //enable apiPanel
            saveSubmitButton.setEnabled(true);
          }

        } catch (java.util.concurrent.CancellationException e) {
          //thread的に、ここよりdoInBackground()内での処理の方が後になることがある
          publish("Cancelled");
          logger.debug("Cancelled");
        } catch (Exception e) {
          Throwable t = e.getCause();
          String error;
          if (null != t && t instanceof PluginErrorException) {
            error = Utils.ThrowableToString(t);
            publish("PluginError: error occurred when rendering");
            logger.debug("PluginError: error occurred when Rendering: " + error);
          } else {
            error = Utils.ThrowableToString(e);
            publish("Error");
            logger.error("Error: " + error);
          }
          setStringToJTextArea(resultTextArea, error);
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
    jSeparator5 = new javax.swing.JSeparator();
    jSplitPane1 = new javax.swing.JSplitPane();
    jScrollPane1 = new javax.swing.JScrollPane();
    queryPane = new javax.swing.JTextPane();
    jSplitPane4 = new javax.swing.JSplitPane();
    jSplitPane3 = new javax.swing.JSplitPane();
    jScrollPane4 = new javax.swing.JScrollPane();
    resultTable = new javax.swing.JTable();
    jScrollPane3 = new javax.swing.JScrollPane();
    instTable = new javax.swing.JTable();
    jScrollPane2 = new javax.swing.JScrollPane();
    resultTextArea = new javax.swing.JTextArea();
    apiPanel = new javax.swing.JPanel();
    javax.swing.JSeparator statusPanelSeparator1 = new javax.swing.JSeparator();
    saveSubmitButton = new javax.swing.JButton();
    saveComboBox = new javax.swing.JComboBox();
    titleTextField = new javax.swing.JTextField();
    saveResetButton = new javax.swing.JButton();
    jLabel1 = new javax.swing.JLabel();
    downloadWaitSpinner = new javax.swing.JSpinner();
    jLabel2 = new javax.swing.JLabel();
    jSeparator2 = new javax.swing.JSeparator();
    maxRetrySpinner = new javax.swing.JSpinner();
    jLabel3 = new javax.swing.JLabel();
    jLabel4 = new javax.swing.JLabel();
    renderComboBox = new javax.swing.JComboBox();
    jSeparator1 = new javax.swing.JSeparator();
    jSeparator3 = new javax.swing.JSeparator();
    jSeparator4 = new javax.swing.JSeparator();
    renderResetButton = new javax.swing.JButton();
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

    jSeparator5.setName("jSeparator5"); // NOI18N

    javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
    jPanel1.setLayout(jPanel1Layout);
    jPanel1Layout.setHorizontalGroup(
      jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jSeparator5, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 776, Short.MAX_VALUE)
      .addGroup(jPanel1Layout.createSequentialGroup()
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
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 362, Short.MAX_VALUE)
        .addComponent(crawlButton)
        .addContainerGap())
    );
    jPanel1Layout.setVerticalGroup(
      jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
        .addContainerGap()
        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(urlButton)
          .addComponent(setButton)
          .addComponent(filterButton)
          .addComponent(depth1Button)
          .addComponent(depth2Button)
          .addComponent(sortButton)
          .addComponent(crawlButton))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(jSeparator5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

    jSplitPane1.setTopComponent(jScrollPane1);

    jSplitPane4.setBorder(null);
    jSplitPane4.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
    jSplitPane4.setName("jSplitPane4"); // NOI18N

    jSplitPane3.setBorder(null);
    jSplitPane3.setMinimumSize(new java.awt.Dimension(97, 100));
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

    jSplitPane3.setRightComponent(jScrollPane4);

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

    jSplitPane4.setTopComponent(jSplitPane3);

    jScrollPane2.setBorder(null);
    jScrollPane2.setName("jScrollPane2"); // NOI18N

    resultTextArea.setColumns(20);
    resultTextArea.setEditable(false);
    resultTextArea.setFont(resourceMap.getFont("resultTextArea.font")); // NOI18N
    resultTextArea.setLineWrap(true);
    resultTextArea.setRows(5);
    resultTextArea.setWrapStyleWord(true);
    resultTextArea.setName("resultTextArea"); // NOI18N
    jScrollPane2.setViewportView(resultTextArea);

    jSplitPane4.setBottomComponent(jScrollPane2);

    jSplitPane1.setRightComponent(jSplitPane4);

    mainPanel.add(jSplitPane1, java.awt.BorderLayout.CENTER);

    apiPanel.setName("apiPanel"); // NOI18N

    statusPanelSeparator1.setName("statusPanelSeparator1"); // NOI18N

    saveSubmitButton.setText(resourceMap.getString("saveSubmitButton.text")); // NOI18N
    saveSubmitButton.setEnabled(false);
    saveSubmitButton.setMaximumSize(new java.awt.Dimension(70, 23));
    saveSubmitButton.setMinimumSize(new java.awt.Dimension(70, 23));
    saveSubmitButton.setName("saveSubmitButton"); // NOI18N

    saveComboBox.setName("saveComboBox"); // NOI18N

    titleTextField.setText(resourceMap.getString("titleTextField.text")); // NOI18N
    titleTextField.setName("titleTextField"); // NOI18N

    saveResetButton.setText(resourceMap.getString("saveResetButton.text")); // NOI18N
    saveResetButton.setMaximumSize(new java.awt.Dimension(70, 23));
    saveResetButton.setMinimumSize(new java.awt.Dimension(70, 23));
    saveResetButton.setName("saveResetButton"); // NOI18N

    jLabel1.setText(resourceMap.getString("jLabel1.text")); // NOI18N
    jLabel1.setName("jLabel1"); // NOI18N

    downloadWaitSpinner.setModel(new javax.swing.SpinnerNumberModel(2, 0, 360, 1));
    downloadWaitSpinner.setName("downloadWaitSpinner"); // NOI18N

    jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    jLabel2.setText(resourceMap.getString("jLabel2.text")); // NOI18N
    jLabel2.setName("jLabel2"); // NOI18N

    jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);
    jSeparator2.setName("jSeparator2"); // NOI18N

    maxRetrySpinner.setModel(new javax.swing.SpinnerNumberModel(0, 0, 100, 1));
    maxRetrySpinner.setName("maxRetrySpinner"); // NOI18N

    jLabel3.setText(resourceMap.getString("jLabel3.text")); // NOI18N
    jLabel3.setName("jLabel3"); // NOI18N

    jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    jLabel4.setText(resourceMap.getString("jLabel4.text")); // NOI18N
    jLabel4.setName("jLabel4"); // NOI18N

    renderComboBox.setName("renderComboBox"); // NOI18N

    jSeparator1.setName("jSeparator1"); // NOI18N

    jSeparator3.setName("jSeparator3"); // NOI18N

    jSeparator4.setOrientation(javax.swing.SwingConstants.VERTICAL);
    jSeparator4.setName("jSeparator4"); // NOI18N

    renderResetButton.setText(resourceMap.getString("renderResetButton.text")); // NOI18N
    renderResetButton.setMaximumSize(new java.awt.Dimension(70, 23));
    renderResetButton.setMinimumSize(new java.awt.Dimension(70, 23));
    renderResetButton.setName("renderResetButton"); // NOI18N

    javax.swing.GroupLayout apiPanelLayout = new javax.swing.GroupLayout(apiPanel);
    apiPanel.setLayout(apiPanelLayout);
    apiPanelLayout.setHorizontalGroup(
      apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(apiPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, apiPanelLayout.createSequentialGroup()
            .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(saveComboBox, 0, 506, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(saveSubmitButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(saveResetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
          .addGroup(apiPanelLayout.createSequentialGroup()
            .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(renderComboBox, 0, 580, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(renderResetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, apiPanelLayout.createSequentialGroup()
            .addComponent(titleTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 409, Short.MAX_VALUE)
            .addGap(9, 9, 9)
            .addComponent(jSeparator4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jLabel3)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(maxRetrySpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(6, 6, 6)
            .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jLabel1)
            .addGap(3, 3, 3)
            .addComponent(downloadWaitSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
          .addComponent(jSeparator3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 752, Short.MAX_VALUE)
          .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 752, Short.MAX_VALUE))
        .addContainerGap())
      .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(statusPanelSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 776, Short.MAX_VALUE))
    );
    apiPanelLayout.setVerticalGroup(
      apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(apiPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
            .addComponent(maxRetrySpinner, javax.swing.GroupLayout.DEFAULT_SIZE, 20, Short.MAX_VALUE)
            .addComponent(jLabel3))
          .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
            .addComponent(jSeparator2, javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
              .addComponent(downloadWaitSpinner)
              .addComponent(jLabel1)))
          .addGroup(apiPanelLayout.createSequentialGroup()
            .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
              .addComponent(jSeparator4, javax.swing.GroupLayout.Alignment.LEADING)
              .addComponent(titleTextField, javax.swing.GroupLayout.Alignment.LEADING))
            .addGap(11, 11, 11)))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(renderComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jLabel4)
          .addComponent(renderResetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(saveResetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(saveSubmitButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(saveComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jLabel2))
        .addContainerGap())
      .addGroup(apiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(apiPanelLayout.createSequentialGroup()
          .addComponent(statusPanelSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addContainerGap(112, Short.MAX_VALUE)))
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
      .addComponent(statusPanelSeparator, javax.swing.GroupLayout.DEFAULT_SIZE, 776, Short.MAX_VALUE)
      .addGroup(statusPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addComponent(statusMessageLabel)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 601, Short.MAX_VALUE)
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
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel jLabel4;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JScrollPane jScrollPane3;
  private javax.swing.JScrollPane jScrollPane4;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JSeparator jSeparator2;
  private javax.swing.JSeparator jSeparator3;
  private javax.swing.JSeparator jSeparator4;
  private javax.swing.JSeparator jSeparator5;
  private javax.swing.JSplitPane jSplitPane1;
  private javax.swing.JSplitPane jSplitPane3;
  private javax.swing.JSplitPane jSplitPane4;
  private javax.swing.JPanel mainPanel;
  private javax.swing.JSpinner maxRetrySpinner;
  private javax.swing.JMenuBar menuBar;
  private javax.swing.JProgressBar progressBar;
  private javax.swing.JTextPane queryPane;
  private javax.swing.JComboBox renderComboBox;
  private javax.swing.JButton renderResetButton;
  private javax.swing.JTable resultTable;
  private javax.swing.JTextArea resultTextArea;
  private javax.swing.JComboBox saveComboBox;
  private javax.swing.JButton saveResetButton;
  private javax.swing.JButton saveSubmitButton;
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
    private Connection conn = null;
    private CrawlExcecuteWorker worker = null;
    private List<String> extensions_of_save   = null;
    private List<String> extensions_of_render = null;
    private String resultTitle = null;
    private File resultTextFile = null;
}
