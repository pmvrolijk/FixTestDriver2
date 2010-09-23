package nl.lamia.FixTools;

import java.util.regex.Pattern;

import junit.framework.Assert;

import quickfix.SessionID;

public class ExpectMessageStep implements TestStep {

	private final static int TIMEOUT=50;
	private final static int TIMES=200;
	
	private FixEngine engine;
	private SessionID session;
	private quickfix.Message answer;
	private String expect;
	private Boolean received;
	
	@Override
	public Boolean run() throws Exception {
		engine.expectMessage(this);
		System.out.println("Waiting for answer...");
		int i=0;
		while (!received && i<TIMES) {
			Thread.sleep(TIMEOUT);
			i++;
			}
		Assert.assertTrue("Expect: No message returned in "+TIMES*TIMEOUT/1000+" sec.", received);    //No answer is fail
		//Compare the message to the expected:
		Assert.assertTrue("Answer doesn't match expected message.",TestMessage.compare(answer, expect)); 
		return true;
	}
	
	public void receive(quickfix.Message answer) {
		System.out.println("We were Called ! "+answer);
		this.answer=answer;
		this.received=true;
	}

	public ExpectMessageStep(String expectStr, FixEngine engine, String sessionStr) {
		this.engine=engine;
		this.session=new SessionID(sessionStr);
		this.received=false;
		this.expect = expectStr.substring(1, expectStr.length()); //cut off the E
	}

	
}
