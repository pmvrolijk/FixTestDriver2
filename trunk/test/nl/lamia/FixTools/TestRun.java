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
import quickfix.field.ClOrdID;
import quickfix.field.HandlInst;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.Text;
import quickfix.field.TransactTime;

/***
 * Contains the test run as defined in the def file 
 * @author marcel
 *
 */
public class TestRun {

	private ArrayList<AnswerMessage> answers;
	private ArrayList<SendMessage> sendings;
	private FixEngine fixEngine;
	
	
	/***
	 * Constructor. Sets up the test case:
	 * Stores the expect messages and messages to be sent
	 * Connects to the correct session (from the iCONNECT <name> line)
	 * @param file
	 */
	public TestRun(File file, FixEngine engine) {
		this.fixEngine=engine;
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
		quickfix.fix42.Message message=this.testOrder();  //These would come from the file
		String sessionName="FIX.4.2:BANZAI->EXEC";  //This would come from the CONNECT line
		return fixEngine.sendMessage(message, sessionName);
	}

	//Quick stub to create a test order:
	private quickfix.fix42.Message testOrder() {
		quickfix.fix42.NewOrderSingle message = new quickfix.fix42.NewOrderSingle(
				new ClOrdID("321"),
				new HandlInst('1'),
				new Symbol("LNUX"),
				new Side(Side.BUY),
				new TransactTime(),
				new OrdType('1'));
		message.set(new Text("Please deliver !"));
		message.set(new OrderQty(100));
		return message;
	}
	
}
