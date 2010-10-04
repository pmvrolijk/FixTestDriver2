/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nl.lamia.FixTools;

import org.apache.log4j.Logger;

/**
 *
 * @author marcel
 */
public class ResetOrderStep implements TestStep {

	private static Logger logger = Logger.getLogger(ResetOrderStep.class);

	private String testCase;
	private MainWindow mainApp;
        private OrderManager om;

	@Override
	public Boolean run() throws Exception {
                ConfigurationManager cm=ConfigurationManager.getConfigurationManager();
                this.mainApp=cm.getMainApp();

                om=OrderManager.get();
                om.purgeTestCase(testCase);

                if (mainApp!=null) mainApp.appendTestOutput("Reset testCase: "+testCase);
                logger.info("Reset testCase: "+testCase);
		return true;
	}

	public ResetOrderStep(String id) {
		this.testCase=id;
	}

}
