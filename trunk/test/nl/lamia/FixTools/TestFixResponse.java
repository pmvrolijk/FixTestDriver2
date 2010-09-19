package nl.lamia.FixTools;


import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@RunWith(Parameterized.class)
public class TestFixResponse {

	//Defaults (overridden by -D parameters)
	private static String testCases="testcases";   //Relative location for the def files: -Dtestcase.dir=
	
	//Static variables
	private static Collection<Object[]> data;   //Collection for the testrunner to go through

	//Test class variables 
	private File def_File;  //File containing the individual test scripts
	
	//Constructor for the test case class
	public TestFixResponse(File file) {
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
			System.out.println("Test "+data.size()+":"+file.getName());
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
                System.out.println("directory not found: " + directory.getPath());
            }
        }
    }

    
    //Simple testcase to go through a file with Input/Expect
    @Test
    public void runTest() {
    	System.out.println("--------------\nRunning test for file: "+this.def_File.getName());
    	TestRun test=new TestRun(def_File);
    	assertTrue("Failed:"+this.def_File.getName(),test.run());
    }
    

    //Before any test runs. Here we setup the Fix connections
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	//After all tests have run. Here we drop the connections and clean up
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
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
