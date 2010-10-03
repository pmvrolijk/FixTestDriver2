package nl.lamia.FixTools;

import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import junit.framework.Assert;

import quickfix.SessionID;

public class ExpectMessageStep implements TestStep {

	private static Logger logger = Logger.getLogger(ExpectMessageStep.class);
	
	private final static int TIMEOUT=10000;
	
	private FixEngine engine;
	private SessionID session;
	private quickfix.Message answer;
	private String expect;
	private Boolean received;

	@Override
	public Boolean run() throws Exception {
		engine.expectMessage(this);
		logger.info("Waiting for answer...");
		Boolean timeout=false;
		synchronized (this) {
			while (!received && !timeout) {
				try {
					this.notifyAll();
					wait(TIMEOUT);
					logger.info("Woke up!");
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
		logger.info("We were Called ! "+answer);
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
