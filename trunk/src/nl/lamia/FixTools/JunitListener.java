/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nl.lamia.FixTools;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
 *
 * @author marcel
 */
public class JunitListener extends RunListener {

    private MainWindow mainApp;
    private ConfigurationManager cm;

    public JunitListener() {
        this.cm=ConfigurationManager.getConfigurationManager();
        this.mainApp=cm.getMainApp();
    }

    @Override
    public void testFailure(Failure f) {
        mainApp.appendTestOutput("FAIL "+f.getMessage());
        //mainApp.appendTestOutput(f.getTrace());
    }

    @Override
    public void testFinished(Description d) {
        mainApp.appendTestOutput(d.getDisplayName());
    }

    @Override
    public void testAssumptionFailure(Failure f) {
        mainApp.appendTestOutput(f.getMessage());
    }

    @Override
    public void testRunFinished(Result r) {
        mainApp.appendTestOutput("\nResults of all tests:\n--------------------");
        int total=r.getRunCount();
        int fail=r.getFailureCount();
        int ignore=r.getIgnoreCount();
        long runtime=r.getRunTime();
        mainApp.appendTestOutput(total+" tests ("+fail+" failed, "+ignore+" ignored).");
        mainApp.appendTestOutput("total runtime "+runtime+" milliseconds.");
        if (r.wasSuccessful()) {
            mainApp.appendTestOutput("All tests were Succesful.");
        } else {
            mainApp.appendTestOutput("Some tests Failed.");
        }
    }

}
