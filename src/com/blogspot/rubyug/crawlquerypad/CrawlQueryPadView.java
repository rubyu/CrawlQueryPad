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

/**
 * The application's main frame.
 */
public class CrawlQueryPadView extends FrameView {

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

        // connecting action tasks to status bar via TaskMonitor
        TaskMonitor taskMonitor = new TaskMonitor(getApplication().getContext());
        taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();
                if ("started".equals(propertyName)) {
                    if (!busyIconTimer.isRunning()) {
                        statusAnimationLabel.setIcon(busyIcons[0]);
                        busyIconIndex = 0;
                        busyIconTimer.start();
                    }
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);
                } else if ("done".equals(propertyName)) {
                    busyIconTimer.stop();
                    statusAnimationLabel.setIcon(idleIcon);
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                } else if ("message".equals(propertyName)) {
                    String text = (String)(evt.getNewValue());
                    statusMessageLabel.setText((text == null) ? "" : text);
                    messageTimer.restart();
                } else if ("progress".equals(propertyName)) {
                    int value = (Integer)(evt.getNewValue());
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(value);
                }
            }
        });

        queryPane.addCaretListener(new QueryPaneCaretListener());

        queryPaneDoc = (StyledDocument) queryPane.getDocument();
        queryPaneDoc.addDocumentListener(new QueryPaneDocumentListener());

        queryPane.getKeymap().addActionForKeyStroke(
          KeyStroke.getKeyStroke("ENTER"), new TextAction("ENTER") {
            public void actionPerformed(ActionEvent e) {
              try {
                queryPaneDoc.insertString(queryPane.getCaretPosition(), "\n  > ", null);
              } catch (Exception ex) {}
            }
        });

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

    }

    public static String ThrowableToString(Throwable t) {
        Writer writer = new StringWriter();
        PrintWriter pw = null;
        try {
          pw = new PrintWriter(writer);
          t.printStackTrace(pw);
          pw.flush();
        } finally {
          if (pw != null) {
              pw.close();
          }
        }
        return writer.toString();
    }

    void queryParse() {

        System.out.println( "--------------------" );

        //disconnect undomanager
        queryPaneDoc.removeUndoableEditListener(undomanager);
        
        String text = queryPane.getText();
        StyledDocument doc = (StyledDocument) queryPane.getDocument();
        // remove all atteributes
        SimpleAttributeSet plane = new SimpleAttributeSet();
        doc.setCharacterAttributes(0, text.length(), plane, true);

        //
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

        logPane.setText("");
        QueryParser parser = new QueryParser( new StringReader(queryPane.getText()) );
        try {
          SimpleNode query = parser.parse();

          //
          List<Object[]> stack;
          int depth;

          //pass 1
          //ノードのナンバリングのためのリスト
          List<SimpleNode> nodes = new ArrayList<SimpleNode>();
          //命令リスト
          List<String> operations = new ArrayList<String>();
          //エラーリスト
          List<Throwable> throwables = new ArrayList<Throwable>();

          //initialize
          stack = new ArrayList<Object[]>();
          stack.add( new Object[]{query, -1} );
          depth = 0;
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
                operations.add( "[CONDITION] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Condition_AND ) {
                operations.add( "[CONDITION AND] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Condition_Match ) {
                operations.add( "[CONDITION MATCH] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Condition_OR ) {
                operations.add( "[CONDITION OR] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Condition_Unary_NOT ) {
                operations.add( "[CONDITION UNARY NOT] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );

              } else if ( node instanceof Crawl ) {
                operations.add( "[CRAWL] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Crawl_Execute ) {
                operations.add( "[CRAWL EXECUTE] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Crawl_Filter ) {
                operations.add( "[CRAWL FILTER] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Crawl_Operator_Difference ) {
                operations.add( "[CRAWL OP DIFFERENCE] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Crawl_Operator_Intersection ) {
                operations.add( "[CRAWL OP INTERSECTION] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Crawl_Operator_SymmetricDefference ) {
                operations.add( "[CRAWL OP SYMMETRIC DEFFERENCE] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Crawl_Operator_Union ) {
                operations.add( "[CRAWL OP UNION] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Crawl_Set_Condition ) {
                operations.add( "[CRAWL SET CONDITION] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );

              } else if ( node instanceof Depth_Value ) {
                Object[] arr = (Object[])node.jjtGetValue();
                String value = (String)arr[2];
                operations.add( "[DEPTH VALUE] " + nodes.indexOf(node.jjtGetParent()) + ", " + value );

              } else if ( node instanceof Field_BODY ) {
                operations.add( "[FIELD BODY] " + nodes.indexOf(node.jjtGetParent()) + ", FIELD_BODY" );
              } else if ( node instanceof Field_ID ) {
                operations.add( "[FIELD ID] " + nodes.indexOf(node.jjtGetParent()) + ", FIELD_ID" );
              } else if ( node instanceof Field_LINKWORD ) {
                operations.add( "[FIELD LINKWORD] " + nodes.indexOf(node.jjtGetParent()) + ", FIELD_LINKWORD" );
              } else if ( node instanceof Field_TEXT ) {
                operations.add( "[FIELD TEXT] " + nodes.indexOf(node.jjtGetParent()) + ", FIELD_TEXT" );
              } else if ( node instanceof Field_TITLE ) {
                operations.add( "[FIELD TITLE] " + nodes.indexOf(node.jjtGetParent()) + ", FIELD_TITLE" );
              } else if ( node instanceof Field_URL ) {
                operations.add( "[FIELD URL] " + nodes.indexOf(node.jjtGetParent()) + ", FIELD_URL" );

              } else if ( node instanceof Index_Order ) {
                operations.add( "[INDEX ORDER] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );

              } else if ( node instanceof Order_ASC ) {
                operations.add( "[ORDER ASC] " + nodes.indexOf(node.jjtGetParent()) + ", ORDER_ASC" );
              } else if ( node instanceof Order_DESC ) {
                operations.add( "[ORDER DESC] " + nodes.indexOf(node.jjtGetParent()) + ", ORDER_DESC" );

              } else if ( node instanceof Query ) {
                operations.add( "[QUERY] " + "- , " + nodes.indexOf(node) );

              } else if ( node instanceof Query_Option_Index ) {
                operations.add( "[QUERY OPTION INDEX] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );

              } else if ( node instanceof Regex ) {
                operations.add( "[REGEX] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              } else if ( node instanceof Regex_Option_Value ) {
                Object[] arr = (Object[])node.jjtGetValue();
                String value = (String)arr[2];
                operations.add( "[REGEX OPTION VALUE] " + nodes.indexOf(node.jjtGetParent()) + ", " + value );
              }  else if ( node instanceof Regex_Pattern_Value ) {
                Object[] arr = (Object[])node.jjtGetValue();
                String value = (String)arr[2];
                operations.add( "[REGEX PATTERN VALUE] " + nodes.indexOf(node.jjtGetParent()) + ", " + value );

              } else if ( node instanceof Url_Value ) {
                Object[] arr = (Object[])node.jjtGetValue();
                String value = (String)arr[2];
                operations.add( "[URL] " + nodes.indexOf(node.jjtGetParent()) + ", " + value );

              } else if ( node instanceof Urls ) {
                operations.add( "[URLS] " + nodes.indexOf(node.jjtGetParent()) + ", " + nodes.indexOf(node) );
              }

              stack.remove(depth);
              depth--;
            } else {
              //depth-first
              stack.add( new Object[]{ node.jjtGetChild(cursor), -1 } );
              depth++;
            }
          }

          for (String s: operations) {
            System.out.println( s );
          }

          //pass 2
          StringBuffer sb = new StringBuffer();
          //initialize
          stack = new ArrayList<Object[]>();
          stack.add( new Object[]{query, -1} );
          depth = 0;
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
              
              //write node name
              for ( int i=0; i < depth; i++ ) {
                sb.append(" ");
              }
              sb.append(node.toString());

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
                              token.kind == QueryParserConstants.FIELD_LINKWORD ||
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
                //write node value
                if ( null != value ) {
                  sb.append(" : ");
                  sb.append(value);
                }
              }
              sb.append("\n");
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

          logPane.setText( sb.toString() );
        } catch (Throwable t) {
          logPane.setText( ThrowableToString(t) );
        }
        //reconnect undomanager
        queryPaneDoc.addUndoableEditListener(undomanager);

        System.out.println( "--------------------" );
    }
    void invokeQueryParse() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                queryParse();
            }
        });
    }
    class QueryPaneDocumentListener implements DocumentListener {
      public void insertUpdate(DocumentEvent e) {
        invokeQueryParse();
      }
      public void removeUpdate(DocumentEvent e) {
        invokeQueryParse();
      }
      public void changedUpdate(DocumentEvent e) {
      }
    }
    class QueryPaneCaretListener implements CaretListener {
      public void caretUpdate(CaretEvent e){
        //
        invokeQueryParse();
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
    jSplitPane1 = new javax.swing.JSplitPane();
    jScrollPane1 = new javax.swing.JScrollPane();
    queryPane = new javax.swing.JTextPane();
    jSplitPane2 = new javax.swing.JSplitPane();
    jScrollPane2 = new javax.swing.JScrollPane();
    logPane = new javax.swing.JTextPane();
    jTabbedPane1 = new javax.swing.JTabbedPane();
    jScrollPane3 = new javax.swing.JScrollPane();
    queryTable = new javax.swing.JTable();
    jScrollPane4 = new javax.swing.JScrollPane();
    crawlTable = new javax.swing.JTable();
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
    mainPanel.setLayout(new javax.swing.BoxLayout(mainPanel, javax.swing.BoxLayout.LINE_AXIS));

    jSplitPane1.setBorder(null);
    jSplitPane1.setDividerLocation(120);
    jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
    jSplitPane1.setName("jSplitPane1"); // NOI18N

    jScrollPane1.setBorder(null);
    jScrollPane1.setName("jScrollPane1"); // NOI18N

    queryPane.setName("queryPane"); // NOI18N
    jScrollPane1.setViewportView(queryPane);

    jSplitPane1.setTopComponent(jScrollPane1);

    jSplitPane2.setDividerLocation(300);
    jSplitPane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
    jSplitPane2.setName("jSplitPane2"); // NOI18N

    jScrollPane2.setBorder(null);
    jScrollPane2.setName("jScrollPane2"); // NOI18N

    logPane.setName("logPane"); // NOI18N
    jScrollPane2.setViewportView(logPane);

    jSplitPane2.setRightComponent(jScrollPane2);

    jTabbedPane1.setName("jTabbedPane1"); // NOI18N

    jScrollPane3.setName("jScrollPane3"); // NOI18N

    queryTable.setModel(new javax.swing.table.DefaultTableModel(
      new Object [][] {

      },
      new String [] {
        "id", "name", "summary", "progress"
      }
    ) {
      boolean[] canEdit = new boolean [] {
        false, false, false, false
      };

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit [columnIndex];
      }
    });
    queryTable.setName("queryTable"); // NOI18N
    jScrollPane3.setViewportView(queryTable);
    org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(com.blogspot.rubyug.crawlquerypad.CrawlQueryPadApp.class).getContext().getResourceMap(CrawlQueryPadView.class);
    queryTable.getColumnModel().getColumn(0).setPreferredWidth(20);
    queryTable.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("queryTable.columnModel.title0")); // NOI18N
    queryTable.getColumnModel().getColumn(1).setHeaderValue(resourceMap.getString("queryTable.columnModel.title1")); // NOI18N
    queryTable.getColumnModel().getColumn(2).setHeaderValue(resourceMap.getString("queryTable.columnModel.title2")); // NOI18N
    queryTable.getColumnModel().getColumn(3).setPreferredWidth(20);
    queryTable.getColumnModel().getColumn(3).setHeaderValue(resourceMap.getString("queryTable.columnModel.title3")); // NOI18N

    jTabbedPane1.addTab(resourceMap.getString("jScrollPane3.TabConstraints.tabTitle"), jScrollPane3); // NOI18N

    jScrollPane4.setName("jScrollPane4"); // NOI18N

    crawlTable.setModel(new javax.swing.table.DefaultTableModel(
      new Object [][] {

      },
      new String [] {
        "url", "linkword", "title", "body", "text"
      }
    ) {
      boolean[] canEdit = new boolean [] {
        false, false, false, false, false
      };

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit [columnIndex];
      }
    });
    crawlTable.setName("crawlTable"); // NOI18N
    jScrollPane4.setViewportView(crawlTable);
    crawlTable.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("crawlTable.columnModel.title0")); // NOI18N
    crawlTable.getColumnModel().getColumn(1).setHeaderValue(resourceMap.getString("crawlTable.columnModel.title1")); // NOI18N
    crawlTable.getColumnModel().getColumn(2).setHeaderValue(resourceMap.getString("crawlTable.columnModel.title2")); // NOI18N
    crawlTable.getColumnModel().getColumn(3).setHeaderValue(resourceMap.getString("crawlTable.columnModel.title3")); // NOI18N
    crawlTable.getColumnModel().getColumn(4).setHeaderValue(resourceMap.getString("crawlTable.columnModel.title4")); // NOI18N

    jTabbedPane1.addTab(resourceMap.getString("jScrollPane4.TabConstraints.tabTitle"), jScrollPane4); // NOI18N

    jSplitPane2.setTopComponent(jTabbedPane1);

    jSplitPane1.setRightComponent(jSplitPane2);

    mainPanel.add(jSplitPane1);

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
      .addComponent(statusPanelSeparator, javax.swing.GroupLayout.DEFAULT_SIZE, 707, Short.MAX_VALUE)
      .addGroup(statusPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addComponent(statusMessageLabel)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 528, Short.MAX_VALUE)
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
  private javax.swing.JTable crawlTable;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JScrollPane jScrollPane3;
  private javax.swing.JScrollPane jScrollPane4;
  private javax.swing.JSplitPane jSplitPane1;
  private javax.swing.JSplitPane jSplitPane2;
  private javax.swing.JTabbedPane jTabbedPane1;
  private javax.swing.JTextPane logPane;
  private javax.swing.JPanel mainPanel;
  private javax.swing.JMenuBar menuBar;
  private javax.swing.JProgressBar progressBar;
  private javax.swing.JTextPane queryPane;
  private javax.swing.JTable queryTable;
  private javax.swing.JLabel statusAnimationLabel;
  private javax.swing.JLabel statusMessageLabel;
  private javax.swing.JPanel statusPanel;
  // End of variables declaration//GEN-END:variables

    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;

    private JDialog aboutBox;

    private final UndoManager undomanager;
    private final StyledDocument queryPaneDoc;
}
