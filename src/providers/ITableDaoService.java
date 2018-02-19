package providers;

import table_skeleton.TableRow;
import table_skeleton.TableRowList;
import xlsx_reader.TableSchema;

public interface ITableDaoService {

	/**
	 * Add a row into the database
	 * @param row
	 * @return
	 */
	public int add(TableRow row);
	
	/**
	 * Update a row in the database
	 * @param row
	 * @return
	 */
	public boolean update(TableRow row);
	
	/**
	 * Get all the records of a table
	 * @param schema
	 * @return
	 */
	public TableRowList getAll(TableSchema schema);
	
	/**
	 * Get a row by its id in the chosen table
	 * @param schema
	 * @return
	 */
	public TableRow getById(TableSchema schema, int id);
	
	/**
	 * Get rows by parent
	 * @param schema
	 * @param parentTable
	 * @param parentId
	 * @param solveFormulas
	 * @return
	 */
	public TableRowList getByParentId(TableSchema schema, String parentTable, int parentId, boolean solveFormulas);

}