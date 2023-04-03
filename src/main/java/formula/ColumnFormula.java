package formula;

import table_skeleton.TableCell;
import table_skeleton.TableColumn;
import table_skeleton.TableRow;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class to manage columns formulas (%columnId.code/label)
 * @author avonva
 *
 */
public class ColumnFormula implements IFormula {
	
	private static final Logger LOGGER = LogManager.getLogger(ColumnFormula.class);

	private String formula;
	private String columnId;
	private String fieldType;
	
	public ColumnFormula(String formula) throws FormulaException {
		this.formula = formula;
		compile();
	}
	
	public String getColumnId() {
		return columnId;
	}
	public String getFieldType() {
		return fieldType;
	}
	public String getUnsolvedFormula() {
		return formula;
	}
	
	/**
	 * Analyze the formula and extract information
	 * @throws FormulaException 
	 */
	public void compile() throws FormulaException {
		
		// split based on the dot
		String[] split = formula.split("\\.");
		
		// get column id and required field type
		if (split.length != 2) {
			throw new FormulaException("Wrong column formula, found " + formula);
		}
		
		// get the column id of the formula
		this.columnId = split[0].replace("%", "");
		
		// get the field type
		this.fieldType = split[1];  // required field
	}
	
	@Override
	public String solve() throws FormulaException {
		throw new FormulaException("Not supported");
	}

	public String solve(TableRow row) throws FormulaException {
		
		TableColumn colSchema = row.getSchema().getById(columnId);
		TableCell colValue = row.get(columnId);
		
		if (colSchema == null) {
			throw new FormulaException("No column found in the row schema for " + columnId);
		}
		
		// if no value is present in the row, then 
		// the formula of the referenced column is
		// put, in order to solve it later with another
		// pass
		boolean emptyValue = colValue == null;
		
		String solvedFormula = null;
		
		switch (fieldType) {
		case "code":
			try {
				solvedFormula = emptyValue ? colSchema.getCodeFormula() : colValue.getCode();
			}catch (Exception e) {
				LOGGER.error("Error in getting solved formula ", e);
				e.printStackTrace();
			}
			break;
		case "label":
			solvedFormula = emptyValue ? colSchema.getLabelFormula() : colValue.getLabel();			
			break;
		default:
			throw new FormulaException("Field type " + fieldType + " not recognized.");
		}
		
		return solvedFormula;
	}
	
	@Override
	public String toString() {
		return "column=" + columnId + "; formula=" + formula + "; fieldType=" + fieldType;
	}
}
