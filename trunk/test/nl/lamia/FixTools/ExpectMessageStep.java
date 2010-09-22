package nl.lamia.FixTools;

import java.util.regex.Pattern;

import quickfix.SessionID;

public class ExpectMessageStep implements TestStep {

	private final static int TIMEOUT=10000;
	
	private FixEngine engine;
	private SessionID session;
	private quickfix.Message answer;
	private Pattern expect;
	private Boolean received;
	
	@Override
	public Boolean run() throws Exception {
		engine.expectMessage(this);
		while (!received) {
			System.out.println("Waiting...");
			Thread.sleep(TIMEOUT);
			}
		return received;
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
		this.expect = Pattern.compile(expectStr.substring(1, expectStr.length()));
	}

	
}
