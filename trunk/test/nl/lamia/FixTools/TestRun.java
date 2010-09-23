package nl.lamia.FixTools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

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

	private ArrayList<TestStep> steps;
	private FixEngine fixEngine;
	private String sessionName;
	
	/***
	 * Constructor. Sets up the test case:
	 * Stores the expect messages and messages to be sent
	 * Connects to the correct session (from the iCONNECT <name> line)
	 * @param file
	 */
	public TestRun(File file, FixEngine engine) {
		this.fixEngine=engine;
		try {
			this.load(file);
		} catch (IOException e) {
			Assert.fail("Error loading def file");
		}
		
	}

	
    private void load(File file) throws IOException {
        steps = new ArrayList<TestStep>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            String line = in.readLine();
            while (line != null) {
                if (line.matches("^[ \t]*#.*")) {
                    steps.add(new PrintComment(line));
                } else if (line.startsWith("I")) {
                    steps.add(new InitiateMessageStep(line, fixEngine, sessionName));
                } else if (line.startsWith("WAIT")) {
                	String[] temp=line.split(" ");
                	if (temp.length<2) Assert.fail("No wait time specified, give time in ms.");
                	int time=Integer.parseInt(temp[1]);
                	steps.add(new WaitStep(time));
                } else if (line.startsWith("E")) {
                    steps.add(new ExpectMessageStep(line, fixEngine, sessionName));
                } else if (line.matches("^i\\d*,?CONNECT.*")) {
                	String[] temp=line.split(" ");
                	if (temp.length<2) Assert.fail("No session specified");
                	this.sessionName=temp[1];
                } else if (line.matches("^e\\d*,?DISCONNECT")) {
                    //steps.add(new ExpectDisconnectStep(line));
                }
                line = in.readLine();
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }
	
	
	/***
	 * Runs the test by sending the messages. Sending is immediate
	 * receiving asynchronous. After the time out or when all 
	 * expected messages have returned it returns true or false to the
	 * caller.
	 * @return
	 */
	public Boolean run() {
		Boolean result=false;
		for (int i=0;i<steps.size();i++) {
			try {
				result=steps.get(i).run();
			} catch (Exception e) {
				e.printStackTrace();
				Assert.fail("Error executing step "+i+".");
			}
			if (!result) Assert.fail("Failure: step "+i+".");
		}
		return true;
	}

	public Boolean quickTest() {
		quickfix.fix42.Message message=this.testOrder();  //These would come from the file
		this.sessionName="FIX.4.2:BANZAI->EXEC";  //This would come from the CONNECT line
		return fixEngine.sendMessage(message, this.sessionName);
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
