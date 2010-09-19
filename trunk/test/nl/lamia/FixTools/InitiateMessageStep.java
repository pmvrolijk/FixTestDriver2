package nl.lamia.FixTools;

import junit.framework.Assert;
import quickfix.InvalidMessage;
import quickfix.Message;

public class InitiateMessageStep implements TestStep {

	private FixEngine engine;
	private String session;
	private quickfix.Message message;
	
	@Override
	public Boolean run() throws Exception {
		return engine.sendMessage(message, session);
	}
	
	public InitiateMessageStep(String line, FixEngine engine, String session) {
		this.engine=engine;
		this.session=session;
		try {
			this.message=new Message(line.substring(1, line.length()));
		} catch (InvalidMessage e) {
			Assert.fail("Invalid message format.");
		}
	}

}
