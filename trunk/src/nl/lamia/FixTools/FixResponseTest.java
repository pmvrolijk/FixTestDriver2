package nl.lamia.FixTools;


import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;


import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;



@RunWith(Parameterized.class)
public class FixResponseTest {

	//Defaults (overridden by -D parameters)
	private static String testCases="testcases";   //Relative location for the def files: -Dtestcase.dir=
	
	//Static variables
	private static Collection<Object[]> data;   //Collection for the testrunner to go through
	private static FixEngine fixEngine;
        private static MainWindow mainApp;
	private static Logger logger = Logger.getLogger(FixResponseTest.class);
	static {
		PropertyConfigurator.configure("config/log4j.properties");
	}
	
	//Test class variables 
	private File def_File;  //File containing the individual test scripts
	
	//Constructor for the test case class
	public FixResponseTest(File file) {
		this.def_File=file;
	}

	//Generator for test files, i.e. parameters for the test class
	@Parameters
	public static Collection<Object[]> data() {
		String param=System.getProperty("testcase.dir");
		if (param!=null) testCases=param;
		data=new ArrayList<Object[]>();
		addTests(new File(testCases));
		return data;
	}
	
	private static void addFile(File file) {
		if (file.getName().endsWith(".def")) {
			data.add(new Object[] { file });
			logger.info("Test "+data.size()+":"+file.getName());
		}
	}
	
    private static void addTests(File directory) {   //Iterate through the folders adding files
        if (!directory.isDirectory()) {
            addFile(new File(directory.getPath()));
        } else {
            if (directory.exists()) {
                File[] files = directory.listFiles();
                for (int i = 0; i < files.length; i++) {
                    if (!files[i].isDirectory() ) {
                        addFile(files[i]);
                    }
                }
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        addTests(files[i]);
                    }
                }
            } else {
                logger.error("directory not found: " + directory.getPath());
            }
        }
    }

    
    //Simple testcase to go through a file with Input/Expect on an existing fix engine
    @Test
    public void runTest() {
    	logger.info("Running test for file: "+this.def_File.getName());
    	TestRun test=new TestRun(def_File, fixEngine);
    	assertTrue("Failed:"+this.def_File.getName(),test.run());
    }
    

    //Before any test runs. Here we setup the Fix connections
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		fixEngine=FixEngine.getFixEngine();

                ConfigurationManager cm=ConfigurationManager.getConfigurationManager();
                mainApp=cm.getMainApp();
                if (mainApp!=null) mainApp.cleanTestOutput();
	}

	//After all tests have run. Here we drop the connections and clean up
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		//fixEngine.close();
	}

	//Before each test. 
	@Before
	public void setUp() throws Exception {
	}

	//After each test.
	@After
	public void tearDown() throws Exception {
	}


	
}
