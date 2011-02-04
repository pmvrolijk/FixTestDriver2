/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nl.lamia.FixTools;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import javax.swing.JFrame;
import org.apache.log4j.Logger;

/**
 *
 * @author marcel
 */
public class ConfigurationManager {

    private static Logger logger=Logger.getLogger(ConfigurationManager.class);

    private static ConfigurationManager manager=null;
    private Properties appProperties;
    private String configFile;

    //Entities store for retrieval
    private MainWindow mainGui=null;

    public static ConfigurationManager getConfigurationManager() {
        if (manager==null) manager=new ConfigurationManager();
        return manager;
    }

    private ConfigurationManager() {
        configFile="config/testdriver.properties";
        appProperties=new Properties();
        loadProperties();
    }

    public String getProperty(String key) {
        return appProperties.getProperty(key);
    }

    public String getProperty(String key, String myDefault) {
        String value=appProperties.getProperty(key);
        if (value==null) return myDefault;
        return value;
    }
    
    public void setProperty(String key, String value) {
        appProperties.setProperty(key, value);
    }

    /***
     * Retrieves a windows geometry from the config store, returns default values when unknown.
     * @param jframe    Window concerned
     * @param name      Store name (could be different for the same class)
     * @param initial   Default values. Comma separated list x,y,width,height
     */
    public void getGeometry(JFrame jframe, String name, String initial) {
        String[] propval;
        String prop;
        prop=this.getProperty("Window."+name, initial);
        propval=prop.split(",");
        if (propval.length==4) {
            jframe.setLocation(Integer.parseInt(propval[0]), Integer.parseInt(propval[1]));
            jframe.setSize(Integer.parseInt(propval[2]), Integer.parseInt(propval[3]));
        }
    }

    /***
     * Saves a windows geometry to the store. 
     * @param jframe    Window concerned
     * @param name      Store name (could be different for the same class)
     */
    public void setGeometry(JFrame jframe, String name) {
        String posSze;
        posSze=""+jframe.getX()+","+jframe.getY()+","+jframe.getWidth()+","+jframe.getHeight();
        this.setProperty("Window."+name, posSze);
    }

    public void setMainApp(MainWindow app) {
        this.mainGui=app;
    }

    public MainWindow getMainApp() {
        return this.mainGui;
    }
    
    public void loadDefaults() {
        appProperties.setProperty("ExpectMessageStep.timeout", "10000");
        appProperties.setProperty("Application.testcases", "testcases");
        appProperties.setProperty("FixEngine.configuration", "config/FixEngine.cfg");
        appProperties.setProperty("Dictionary.filename", "config/products.def");
        appProperties.setProperty("TestMessage.ignoreUnexpectedTags" , "false");
        appProperties.setProperty("TestMessage.checkDateTags" , "false");
        appProperties.setProperty("TestMessage.skipTags" , "10,9,45,34,17,37");
        appProperties.setProperty("QueuedMessage.staleTime", "30000");
        appProperties.setProperty("TestMessage.matchClordid" , "false");
        appProperties.setProperty("TestMessage.matchMsgType", "true");
    }

    public void loadProperties() {
        //Try to read the file. If not present create default properties
        FileInputStream file=null;
        try {
            file=new FileInputStream(configFile);
            appProperties.load(file);
        } catch (IOException ex) {
            logger.info("No configuration file. Using defaults.");
            loadDefaults();
        } finally {
            try {if (file!=null) file.close();}
            catch (Exception e) {logger.error("Error closing file -", e);}
        }
    }


    public Boolean saveProperties() {
        FileOutputStream file=null;
        try {
            file=new FileOutputStream(configFile);
            appProperties.store(file,"FixTestDriver");
            file.close();
        } catch (Exception e) {
            logger.error("Can't write configuration file "+configFile, e);
            return false;
        }
        return true;
    }

}
