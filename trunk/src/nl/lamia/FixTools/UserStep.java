package nl.lamia.FixTools;

import javax.swing.JOptionPane;
import org.apache.log4j.Logger;

public class UserStep implements TestStep {

	private static Logger logger = Logger.getLogger(UserStep.class);

	private String msg;
        private MainWindow mw;
        private ConfigurationManager cm;

	@Override
	public Boolean run() throws Exception {
		logger.info("Waiting for user.");
		JOptionPane.showMessageDialog(mw,
                    msg, "User Action", JOptionPane.INFORMATION_MESSAGE);
		return true;
	}

	public UserStep (String msg) {
                if (msg.equals("")||msg==null) {
                    this.msg="Press OK to continue.";
                } else {
                    this.msg=msg;
                }
                cm=ConfigurationManager.getConfigurationManager();
                mw=cm.getMainApp();
	}

}
