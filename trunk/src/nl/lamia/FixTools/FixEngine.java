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
import javax.swing.Timer;
import java.awt.event.*;
import javax.swing.table.DefaultTableModel;

import org.apache.log4j.Logger;

import junit.framework.Assert;

import quickfix.Application;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.DoNotSend;
import quickfix.FieldConvertError;
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

	//private Set<ExpectMessageStep> expectations;
	
        private static FixEngine fixengine=null;

        //TODO
        //move calls to the new Queuemanager
        private QueueManager qm;


        //private ArrayList<QueuedMessage> msgQueue;

        public static FixEngine getFixEngine() {
            if (fixengine==null) fixengine=new FixEngine();
            return fixengine;
        }

        /***
         * Reload the configuration settings and recreate the engine
         */
        public void reloadFixEngine() {
            fixengine.close();
            fixengine.load();
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
	    this.load();  //read config and initialize

            //The new QueueManager:
            qm=QueueManager.get();
	    //Store for Expect messages:
	    //expectations=new HashSet<ExpectMessageStep>();
            //msgQueue=new ArrayList<QueuedMessage>();

            //Timer for queue
            //Timer t=new Timer(300, new ActionListener() {
            //    public void actionPerformed(ActionEvent e) {
            //        deliver();
            //    }
            //});
            //t.start();
	}

        public void load() {
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
	    } catch (ConfigError e) {
			logger.error("Configuration error starting Initiator (configuration file "+fileName+").");
			e.printStackTrace();
            }

            //Print created sessions
            sessionList=socketInitiator.getSessions();
	    for (int i=0;i<sessionList.size();i++) {
	    	logger.info("Session "+i+" "+sessionList.get(i).toString());
	    }
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

	//public Boolean expectMessage(ExpectMessageStep caller) {
	//	expectations.add(caller);
	//	return true;
	//}

	
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
                try {
                    if (message.getHeader().getField(new MsgType()).getValue().equals(MsgType.LOGON)) {
                        logger.debug("Logon message, check for user/pass");
                        String username=settings.get(sessionId).getString("Username");
                        String password=settings.get(sessionId).getString("Password");
                        if (username!=null) {
                            message.setField(new Username(username));
                        }
                        if (password!=null) {
                            message.setField(new Password(password));
                        }
                    }
                } catch (FieldNotFound ex) {
                    logger.debug("Msg has no type!");
                } catch (ConfigError ex) {
                    logger.debug("No user/password specified.");
                } catch (FieldConvertError ex) {
                    logger.debug("Field convert error getting user/password!");
                }

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
                //DEBUG
                try {logger.debug(TestMessage.print(message));}
                catch (Exception e) {logger.warn("Problem printing message.",e);}
		logger.info("Outgoing: "+message.toString().replace('\001', '|'));
                pushStatus();
	}

	@Override
	public void fromApp(Message message, SessionID sessionId)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
			UnsupportedMessageType {
		//OrderManager om=OrderManager.get();
		//om.addMessage(message);
		logger.info("Incoming: "+message.toString().replace('\001', '|'));
                pushStatus();

                //String msgType=message.getHeader().getString(35);
                //TODO: get rid of the other calls to queues
                qm.addMessage(message, sessionId);
                //msgQueue.add(new QueuedMessage(sessionId, msgType, message));
                //deliver();

		//See whether somebody is interested:
		/*for (ExpectMessageStep caller: expectations) {
			//Check for session id only. Heartbeats come in on the admin callback
			//so the next message is prob an incoming answer. 
			//If this turns out not to be the case, then we may need to expand this
			//to expect a certain msgtype as well. 
			synchronized (caller) {
				caller.receive(message);     //wake up sleepy head
				caller.notifyAll();
			}
			expectations.remove(caller); //only one message per expect
		}*/
	
	}

        //public void cleanQueue() {
        //    logger.info("Clearing message queue");
        //    msgQueue.clear();
        //}

        /*private void deliver() {
            logger.debug("Attempting to send queued messages");
            logger.debug("Number of waiting msgs: "+msgQueue.size());
            QueuedMessage msg;
            String msgtype,clordid;
            for (int i=0;i<msgQueue.size();i++) {
                msg=msgQueue.get(i);
                msgtype=msg.getMsgtype();
                clordid=msg.getClordid();
                for (ExpectMessageStep caller: expectations) {
                    logger.debug("Queued msg: "+msg.getMsgtype()+" for "+msg.getSession().toString());
                    logger.debug("Caller msg: "+caller.getMsgType()+" for "+caller.getSession().toString());
                    if (caller.wants(msgtype,clordid) &&
                        caller.getSession().equals(msg.getSession()) ) {
                            //try to send
                            synchronized (caller) {
				caller.receive(msg.getMessage());     //wake up sleepy head
				caller.notifyAll();
                            }
                            expectations.remove(caller); //only one message per expect
                            msgQueue.remove(i);
                    }
		}
                if (msg.isStale()) msgQueue.remove(i);
            }
        }*/

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
