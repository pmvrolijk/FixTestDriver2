package nl.lamia.FixTools;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.JFrame;

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
	private static String fileName="config/FixEngine.cfg";
	
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
			socketInitiator.start();
			while (!socketInitiator.isLoggedOn()) {
				Thread.sleep(50);
			}
	    } catch (ConfigError e) {
			logger.error("Configuration error starting Initiator (configuration file "+fileName+").");
			e.printStackTrace();
		} catch (InterruptedException e) {
			logger.error("Ongeduld !");
			e.printStackTrace();
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
		// TODO Auto-generated method stub

	}

	@Override
    	public void onLogon(SessionID sessionId) {
            if (this.parentWindow!=null) parentWindow.setFixStatus("Logged on");
	}

	@Override
	public void onLogout(SessionID sessionId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void toAdmin(Message message, SessionID sessionId) {
		logger.info("Admin Outgoing: "+message.toString().replace('\001', '|'));
                try {
                    if (this.parentWindow!=null) parentWindow.setFixStatus("msg: received");
                } catch (Exception e) {}
	}

	@Override
	public void fromAdmin(Message message, SessionID sessionId)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
			RejectLogon {
		logger.info("Admin Incoming: "+message.toString().replace('\001', '|'));
                try {
                    if (this.parentWindow!=null) parentWindow.setFixStatus("msg: sent");
                } catch (Exception e) {}
	}

	@Override
	public void toApp(Message message, SessionID sessionId) throws DoNotSend {
		OrderManager om=OrderManager.get();
		om.addMessage(message);
		logger.info("Outgoing: "+message.toString().replace('\001', '|'));
                try {
                    if (this.parentWindow!=null) parentWindow.setFixStatus("msg: recvd ");
                } catch (Exception e) {}
	}

	@Override
	public void fromApp(Message message, SessionID sessionId)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
			UnsupportedMessageType {
		OrderManager om=OrderManager.get();
		om.addMessage(message);
		logger.info("Incoming: "+message.toString().replace('\001', '|'));
                try {
                    if (this.parentWindow!=null) parentWindow.setFixStatus("msg: sent");
                } catch (Exception e) {}

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
