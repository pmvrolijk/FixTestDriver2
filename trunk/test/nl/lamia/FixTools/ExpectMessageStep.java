package nl.lamia.FixTools;

import java.util.regex.Pattern;

import junit.framework.Assert;

import quickfix.SessionID;

public class ExpectMessageStep implements TestStep {

	private final static int TIMEOUT=10000;
	
	private FixEngine engine;
	private SessionID session;
	private quickfix.Message answer;
	private String expect;
	private Boolean received;
	
	@Override
	public Boolean run() throws Exception {
		engine.expectMessage(this);
		System.out.println("Waiting for answer...");
		Boolean timeout=false;
		synchronized (this) {
			while (!received && !timeout) {
				try {
					this.notifyAll();
					wait(TIMEOUT);
					System.out.println("Woke up!");
					if (!received) timeout=true;
					//System.out.println("got something ??"+received);
				} catch (Exception e) {
					Assert.fail("Waiting interrupted before "+TIMEOUT/1000+" sec.");
				}
			}
		}
		Assert.assertTrue("Expect: No message returned in "+TIMEOUT/1000+" sec.", received);    //No answer is fail
		//Compare the message to the expected:
		Assert.assertTrue("Answer doesn't match expected message.",TestMessage.compare(answer, expect)); 
		return true;
	}
	
	synchronized public void receive(quickfix.Message answer) {
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
