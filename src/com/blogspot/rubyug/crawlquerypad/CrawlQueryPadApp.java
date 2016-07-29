/*
 * CrawlQueryPadApp.java
 */

package com.blogspot.rubyug.crawlquerypad;

import java.io.File;
import org.jdesktop.application.Application;
import org.jdesktop.application.SingleFrameApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.cli.*;

/**
 * The main class of the application.
 */
public class CrawlQueryPadApp extends SingleFrameApplication {
    protected static Logger logger = LoggerFactory.getLogger(CrawlQueryPadApp.class);

    /*
    private static String[] argument = null;
    public static State getArgState() {
      State state = new State();
      Options options = new Options();
      options.addOption("p", false, "profile name");
      CommandLineParser parser = new BasicParser();
      CommandLine commandLine;
      try {
          commandLine = parser.parse(options, argument);
      } catch (ParseException e) {
          logger.error("error occurred when parsing commandline arguments:" + Utils.ThrowableToString(e));
          return state;
      }
      if (commandLine.hasOption("p") &&
          null != commandLine.getOptionValue("p")) {
        state.add("profileName", commandLine.getOptionValue("p"));
      } else {
        state.add("profileName", "default"); //apply default profile name
      }
      return state;
    }
     */

    /**
     * At startup create and show the main frame of the application.
     */
    @Override protected void startup() {
        show(new CrawlQueryPadView(this));
    }

    /**
     * This method is to initialize the specified window by injecting resources.
     * Windows shown in our application come fully initialized from the GUI
     * builder, so this additional configuration is not needed.
     */
    @Override protected void configureWindow(java.awt.Window root) {
    }

    /**
     * A convenient static getter for the application instance.
     * @return the instance of CrawlQueryPadApp
     */
    public static CrawlQueryPadApp getApplication() {
        return Application.getInstance(CrawlQueryPadApp.class);
    }

    /**
     * Main method launching the application.
     */
    public static void main(String[] args) {
      if (!appHome.exists()) {
        appHome.mkdir();
      }
      
      logger.info("database initialize");
      try {
        DB.initialize();
      } catch(Exception e) {
        logger.error(Utils.ThrowableToString(e));
        return;
      }
      launch(CrawlQueryPadApp.class, args);
    }
    
    public static File appHome = new File(System.getProperty("user.home"), ".cqpad");
}
