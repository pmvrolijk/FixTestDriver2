package nl.lamia.FixTools;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.table.DefaultTableModel;

import org.apache.log4j.Logger;

import junit.framework.Assert;

import quickfix.Application;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.FileLogFactory;
import quickfix.FileStoreFactory;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.InvalidMessage;
import quickfix.LogFactory;
import quickfix.Message;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;
import quickfix.UnsupportedMessageType;
import quickfix.field.*;

/***
 * FIXENGINE: supporting multiple sessions.
 * Connect a session
 * Send messages to a session
 * Wait for message (expect) with timeout
 * callback for incoming messages (for async testing later)
 */
public class FixEngine implements Application {

	private static Logger logger = Logger.getLogger(FixEngine.class);
	
	//Defaults
	private static String fileName;
	
	private SessionSettings settings;
	private MessageStoreFactory storeFactory;
	private LogFactory logFactory;
	private MessageFactory messageFactory;
	private SocketInitiator socketInitiator;
	private ArrayList<SessionID> sessionList;
	private MainWindow parentWindow;

	private Set<ExpectMessageStep> expectations;
	
        private static FixEngine fixengine=null;

        public static FixEngine getFixEngine() {
            if (fixengine==null) fixengine=new FixEngine();
            return fixengine;
        }

        public void setParentWindow(MainWindow window) {
            this.parentWindow=window;
        }

	/***
	 * Constructor; reads the configuration file and initializes all sessions. 
	 * Connection only after connect(Session) call. To avoid unused 
	 * connections for untested sessions.
	 */
	public FixEngine() {
            ConfigurationManager cm=ConfigurationManager.getConfigurationManager();
            fileName=cm.getProperty("FixEngine.configuration", "config/FixEngine.cfg");
            this.parentWindow=cm.getMainApp();
	    try {
			settings = new SessionSettings(new FileInputStream(fileName));
		} catch (FileNotFoundException e) {
			logger.error("Configuration file not found: "+fileName);
			e.printStackTrace();
		} catch (ConfigError e) {
			logger.error("Configuration error in config file: "+fileName);
			e.printStackTrace();
		}
	    storeFactory = new FileStoreFactory(settings);
	    logFactory = new FileLogFactory(settings);
	    messageFactory = new DefaultMessageFactory();

	    try {
			socketInitiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
			//socketInitiator.start();
			//while (!socketInitiator.isLoggedOn()) {
			//	Thread.sleep(50);
			//}
	    } catch (ConfigError e) {
			logger.error("Configuration error starting Initiator (configuration file "+fileName+").");
			e.printStackTrace();
            //} catch (InterruptedException e) {
            //		logger.error("Ongeduld !");
            //		e.printStackTrace();
            }

            //Print created sessions
            sessionList=socketInitiator.getSessions();
	    for (int i=0;i<sessionList.size();i++) {
	    	logger.debug("Session "+i+" "+sessionList.get(i).toString());
	    }

	    
	    //Store for Expect messages:
	    expectations=new HashSet<ExpectMessageStep>();
	}
	
	public void close() {
		socketInitiator.stop();
	}

	public void start() {
                try {
                    socketInitiator.start();
            } catch (ConfigError ex) {
                logger.error("Configuration error in Fixengine cfg", ex);
            }
	}
	
	public Boolean sendMessage(Message message, String sessionName) {
		try {
			return Session.sendToTarget(message, new SessionID(sessionName)); 
		} catch (SessionNotFound e) {
			logger.warn("Session not found: "+sessionName);
			return false;
		}
	}

	public Boolean expectMessage(ExpectMessageStep caller) {
		expectations.add(caller);
		return true;
	}

	
	@Override
	public void onCreate(SessionID sessionId) {
	}

	@Override
    	public void onLogon(SessionID sessionId) {
            //if (this.parentWindow!=null) parentWindow.setFixStatus("Logged on");
            sessionList=socketInitiator.getSessions();
            pushStatus();
	}

	@Override
	public void onLogout(SessionID sessionId) {
            sessionList=socketInitiator.getSessions();
            pushStatus();
	}

	@Override
	public void toAdmin(Message message, SessionID sessionId) {
		logger.info("Admin Outgoing: "+message.toString().replace('\001', '|'));
                pushStatus();
	}

	@Override
	public void fromAdmin(Message message, SessionID sessionId)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
			RejectLogon {
		logger.info("Admin Incoming: "+message.toString().replace('\001', '|'));
                pushStatus();
	}

	@Override
	public void toApp(Message message, SessionID sessionId) throws DoNotSend {
		OrderManager om=OrderManager.get();
		om.addMessage(message);
		logger.info("Outgoing: "+message.toString().replace('\001', '|'));
                pushStatus();
	}

	@Override
	public void fromApp(Message message, SessionID sessionId)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
			UnsupportedMessageType {
		OrderManager om=OrderManager.get();
		om.addMessage(message);
		logger.info("Incoming: "+message.toString().replace('\001', '|'));
                pushStatus();

		//See whether somebody is interested:
		for (ExpectMessageStep caller: expectations) {
			//Check for session id only. Heartbeats come in on the admin callback
			//so the next message is prob an incoming answer. 
			//If this turns out not to be the case, then we may need to expand this
			//to expect a certain msgtype as well. 
			synchronized (caller) {
				caller.receive(message);     //wake up sleepy head
				caller.notifyAll();
			}
			expectations.remove(caller); //only one message per expect
		}
	
	}

        private void pushStatus() {
            if (this.parentWindow==null || this.sessionList==null) return;
            Session ssn=null;
            Vector<String> headers=new Vector<String>();
            Vector<Vector<String>> rows=new Vector<Vector<String>>();
            headers.add("up");
            headers.add("Name");
            headers.add("out");
            headers.add("in");
            for (int i=0;i<sessionList.size();i++) {
                try {
                    ssn=Session.lookupSession(sessionList.get(i));
                } catch (Exception ex) {
                    System.out.println("can't get session");
                }
                if (ssn!=null) {
                    Vector<String> row=new Vector<String>();
                    row.add(Boolean.toString(ssn.isLoggedOn()));
                    row.add(sessionList.get(i).toString());
                    row.add(Integer.toString(ssn.getExpectedSenderNum()));
                    row.add(Integer.toString(ssn.getExpectedTargetNum()));
                    rows.add(row);
                }
	    }
            this.parentWindow.setFixStatus(new DefaultTableModel(rows, headers));
        }

        public void logout(String sessionName) {
            Session ssn;
            try {
                ssn=Session.lookupSession(new SessionID(sessionName));
            } catch (Exception ex) {
                logger.warn("can't get session");
                return;
            }
            ssn.logout("Requested logoff");
            pushStatus();
        }

        public void logon(String sessionName) {
            Session ssn;
            try {
                ssn=Session.lookupSession(new SessionID(sessionName));
            } catch (Exception ex) {
                logger.warn("can't get session");
                return;
            }
            ssn.logon();
            pushStatus();
        }

        public void reset(String sessionName) {
            Session ssn;
            try {
                ssn=Session.lookupSession(new SessionID(sessionName));
            } catch (Exception ex) {
                logger.warn("can't get session");
                return;
            }
            try {
                ssn.reset();
            } catch (IOException ex) {
                logger.error("Error resetting "+sessionName, ex);
            }
            pushStatus();
        }

	/**
	 * @param args
	 * @throws InvalidMessage 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InvalidMessage, InterruptedException {
		//Test connection
		FixEngine engine=new FixEngine();
		quickfix.fix42.NewOrderSingle message = new quickfix.fix42.NewOrderSingle(
						new ClOrdID("321"),
						new HandlInst('1'),
						new Symbol("LNUX"),
						new Side(Side.BUY),
						new TransactTime(),
						new OrdType('1'));
		message.set(new Text("Please deliver !"));
		message.set(new OrderQty(100));
		Boolean succes=engine.sendMessage(message, "FIX.4.2:BANZAI->EXEC");
		logger.debug("Result :"+succes.toString());
		Thread.sleep(10000);
		engine.close();
	}

}
