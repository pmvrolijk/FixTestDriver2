package nl.lamia.FixTools;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;

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

	//Defaults
	private static String fileName="config/FixEngine.cfg";
	
	private SessionSettings settings;
	private MessageStoreFactory storeFactory;
	private LogFactory logFactory;
	private MessageFactory messageFactory;
	private SocketInitiator socketInitiator;
	private ArrayList<SessionID> sessionList;
	
	/***
	 * Constructor; reads the configuration file and initializes all sessions. 
	 * Connection only after connect(Session) call. To avoid unused 
	 * connections for untested sessions.
	 */
	public FixEngine() {
	    try {
			settings = new SessionSettings(new FileInputStream(fileName));
		} catch (FileNotFoundException e) {
			System.out.println("Configuration file not found: "+fileName);
			e.printStackTrace();
		} catch (ConfigError e) {
			System.out.println("Configuration error in config file: "+fileName);
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
			System.out.println("Configuration error starting Initiator (configuration file "+fileName+").");
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.out.println("Ongeduld !");
			e.printStackTrace();
		}
	    //Print created sessions
	    sessionList=socketInitiator.getSessions();
	    for (int i=0;i<sessionList.size();i++) {
	    	System.out.println("Session "+i+" "+sessionList.get(i).toString());
	    }
	}
	
	public void close() {
		socketInitiator.stop();
	}
	
	public Boolean sendMessage(Message message, String sessionName) {
		try {
			return Session.sendToTarget(message, new SessionID(sessionName)); 
		} catch (SessionNotFound e) {
			System.out.println("Session not found: "+sessionName);
			return false;
		}
	}
	
	
	@Override
	public void onCreate(SessionID sessionId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLogon(SessionID sessionId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLogout(SessionID sessionId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void toAdmin(Message message, SessionID sessionId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fromAdmin(Message message, SessionID sessionId)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
			RejectLogon {
		// TODO Auto-generated method stub

	}

	@Override
	public void toApp(Message message, SessionID sessionId) throws DoNotSend {
		// TODO Auto-generated method stub
		System.out.println("Outgoing: "+message.toString());

	}

	@Override
	public void fromApp(Message message, SessionID sessionId)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
			UnsupportedMessageType {
		// TODO Auto-generated method stub
		System.out.println("Incoming: "+message.toString());
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
		System.out.println("Result :"+succes.toString());
		Thread.sleep(10000);
		engine.close();
	}

}
