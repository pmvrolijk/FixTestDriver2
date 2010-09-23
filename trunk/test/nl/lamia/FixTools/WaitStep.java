package nl.lamia.FixTools;

public class WaitStep implements TestStep {

	static int time;
	
	@Override
	public Boolean run() throws Exception {
		System.out.println("Waiting for "+time+" ms.");
		Thread.sleep(time);
		return true;
	}
	
	public WaitStep (int time) {
		this.time=time;
	}

}

