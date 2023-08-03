package table_dialog;

import java.util.Comparator;

import table_skeleton.TableCell;
import table_skeleton.TableColumn;
import table_skeleton.TableRow;

public class TableRowComparator implements Comparator<TableRow> {
	
	private TableColumn column;
	private boolean ascendant;
	
	/**
	 * Order a table by the values of a column
	 * @param column sort the table by this column
	 * @param ascendant true for ascendant order, false for descendant
	 */
	public TableRowComparator(TableColumn column, boolean ascendant) {
		this.column = column;
		this.ascendant = ascendant;
	}
	
	/**
	 * Compare two rows
	 */
	public int compare(TableRow row1, TableRow row2) {
		
		String value1 = null;
		String value2 = null;
		
		TableCell sel1 = row1.get(column.getId());
		TableCell sel2 = row2.get(column.getId());
		
		// get values
		if (sel1 != null)
			value1 = sel1.getLabel();
		
		if (sel2 != null)
			value2 = sel2.getLabel();
		
		int compare = 0;
		
		switch(column.getType()) {
		case U_INTEGER:
		case INTEGER:
			
			int intValue1;
			int intValue2;
			
			// set default values if no value is retrieved
			if (value1 == null || value1.length() == 0)
				intValue1 = ascendant ? Integer.MAX_VALUE : Integer.MIN_VALUE;
			else
				intValue1 = Integer.valueOf(value1);
			
			if (value2 == null || value2.length() == 0)
				intValue2 = ascendant ? Integer.MAX_VALUE : Integer.MIN_VALUE;
			else
				intValue2 = Integer.valueOf(value2);

			// check if equal
			if (intValue1 == intValue2)
				compare = 0;
			else {
				
				// if not equal check greater/less than
				boolean result = ascendant ? intValue1 > intValue2 : intValue2 > intValue1;
				
				// convert boolean to integer
				compare = result ? 1 : -1;
			}
			
			break;
		default:
			
			// check null values
			if (value1 == null)
				value1 = "";
			
			if (value2 == null)
				value2 = "";
			
			compare = ascendant ? value1.compareTo(value2) : value2.compareTo(value1);
			break;
		}
		
		return compare;
	}
}
