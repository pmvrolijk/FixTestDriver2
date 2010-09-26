package nl.lamia.FixTools;

import org.apache.log4j.Logger;

public class WaitStep implements TestStep {

	private static Logger logger = Logger.getLogger(WaitStep.class);
	
	static int time;
	
	@Override
	public Boolean run() throws Exception {
		logger.info("Waiting for "+time+" ms.");
		Thread.sleep(time);
		return true;
	}
	
	public WaitStep (int time) {
		this.time=time;
	}

}

