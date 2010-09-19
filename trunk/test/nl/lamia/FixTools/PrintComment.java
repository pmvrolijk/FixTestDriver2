package nl.lamia.FixTools;

public class PrintComment implements TestStep {

	private String line;
	
	@Override
	public Boolean run() throws Exception {
		System.out.println(line);
		return true;
	}
	
	public PrintComment(String line) {
		this.line=line;
	}

}
