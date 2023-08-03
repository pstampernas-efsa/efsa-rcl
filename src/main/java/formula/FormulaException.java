package formula;

public class FormulaException extends Exception {
	private static final long serialVersionUID = -6384640000599405482L;

	public FormulaException(Exception e) {
		super(e);
	}
	
	public FormulaException(String text) {
		super(text);
	}

	public FormulaException(String text, Exception e) {
		super(text, e);
	}
}
