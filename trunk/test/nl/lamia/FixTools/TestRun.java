package nl.lamia.FixTools;

import java.io.File;
import java.util.ArrayList;

import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.RejectLogon;
import quickfix.SessionID;
import quickfix.UnsupportedMessageType;

/***
 * Contains the test run as defined in the def file 
 * @author marcel
 *
 */
public class TestRun implements quickfix.Application {

	private ArrayList<AnswerMessage> answers;
	private ArrayList<SendMessage> sendings;
	
	
	/***
	 * Constructor. Sets up the test case:
	 * Stores the expect messages and messages to be sent
	 * Connects to the correct session (from the iCONNECT <name> line)
	 * @param file
	 */
	public TestRun(File file) {
		
	}
	
	/***
	 * Gets a connection by name from the session manager and
	 * joins it. 
	 * @param session
	 */
	private void connect(String session) {
		
	}
	
	/***
	 * Runs the test by sending the messages. Sending is immediate
	 * receiving asynchronous. After the time out or when all 
	 * expected messages have returned it returns true or false to the
	 * caller.
	 * @return
	 */
	public Boolean run() {
		return true;
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
		
	}

	@Override
	public void fromApp(Message message, SessionID sessionId)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue,
			UnsupportedMessageType {
		// TODO Auto-generated method stub
		
	}
	
}
