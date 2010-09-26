package nl.lamia.FixTools;

import org.apache.log4j.Logger;

public class PrintComment implements TestStep {

	private static Logger logger = Logger.getLogger(PrintComment.class);
	
	private String line;
	
	@Override
	public Boolean run() throws Exception {
		logger.info(line);
		return true;
	}
	
	public PrintComment(String line) {
		this.line=line;
	}

}
