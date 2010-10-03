package nl.lamia.FixTools;

import org.apache.log4j.Logger;

public class PrintComment implements TestStep {

	private static Logger logger = Logger.getLogger(PrintComment.class);
	
	private String line;
	private MainWindow mainApp;

	@Override
	public Boolean run() throws Exception {
                ConfigurationManager cm=ConfigurationManager.getConfigurationManager();
                this.mainApp=cm.getMainApp();            

                if (mainApp!=null) mainApp.appendTestOutput(line);
                logger.info(line);
		return true;
	}
	
	public PrintComment(String line) {
		this.line=line;
	}

}
