package table_database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app_config.AppPaths;
import table_relations.Relation;
import table_skeleton.TableCell;
import table_skeleton.TableColumn;
import table_skeleton.TableRow;
import table_skeleton.TableRowList;
import xlsx_reader.TableSchema;
import xml_catalog_reader.XmlContents;
import xml_catalog_reader.XmlLoader;

/**
 * Dao which communicates with the database and all the tables that follow a
 * {@link TableSchema}. These tables are automatically generated starting from
 * the excel file, therefore the dao adapts the query using their structure.
 * 
 * @author avonva
 * @author shahaal
 *
 */
public class TableDao implements ITableDao {

	private static final Logger LOGGER = LogManager.getLogger(TableDao.class);

	private String getTable(TableSchema schema) {
		return "APP." + schema.getSheetName();
	}
	
	/*
	private String getTable(String schemaName) {
		return "APP." + schemaName;
	}*/

	/**
	 * Get the query needed to add a row to the table
	 * 
	 * @return
	 */
	private String getAddQuery(TableSchema schema) {

		StringBuilder query = new StringBuilder();
		query.append("insert into " + getTable(schema) + " (");

		// set the columns names
		Iterator<TableColumn> iterator = schema.iterator();
		while (iterator.hasNext()) {

			TableColumn col = iterator.next();

			query.append(col.getId());

			// append the comma if there is another field
			if (iterator.hasNext())
				query.append(",");
			else
				query.append(")"); // else, close statement
		}

		// values statement
		query.append(" values (");

		// add the ?
		iterator = schema.iterator();
		while (iterator.hasNext()) {

			// go to the next
			iterator.next();

			query.append("?");

			// append the comma if there is another field
			if (iterator.hasNext())
				query.append(",");
			else
				query.append(")"); // else, close statement
		}

		return query.toString();
	}

	/**
	 * Get the query needed to update a row to the table
	 * 
	 * @return
	 */
	private String getUpdateQuery(TableSchema schema) {

		StringBuilder query = new StringBuilder();
		query.append("update " + getTable(schema) + " set ");

		// set the columns names
		Iterator<TableColumn> iterator = schema.iterator();
		while (iterator.hasNext()) {

			TableColumn col = iterator.next();

			query.append(col.getId());
			query.append(" = ?");

			// append the comma if there is another field
			if (iterator.hasNext())
				query.append(",");
		}

		query.append(" where ").append(schema.getTableIdField()).append(" = ?");

		return query.toString();
	}

	/**
	 * Check if the id is a foreignKey for a parent table of the current table
	 * 
	 * @param id
	 * @return
	 * @throws IOException
	 */
	private boolean isRelationId(TableSchema schema, String id) throws IOException {

		if (schema.getRelations() == null)
			return false;

		for (Relation r : schema.getRelations()) {
			if (r.getForeignKey().equals(id))
				return true;
		}

		return false;
	}

	/**
	 * Set the parameter of the statement using the row values and the table name
	 * 
	 * @param row
	 * @param stmt
	 * @throws SQLException
	 */
	private void setParameters(TableRow row, PreparedStatement stmt, boolean setWhereId) throws SQLException {

		int currentIndex = 1;

		for (int i = 0; i < row.getSchema().size(); ++i) {

			TableColumn col = row.getSchema().get(i);

			TableCell colValue = row.get(col.getId());

			// TODO here it is possible to flag the columns which are null
			/*
			 * if (colValue == null) {
			 * 
			 * if(col.getId()==CustomStrings.AN_METH_TYPE_COL) { //set its default value
			 * colValue= }
			 * 
			 * LOGGER.info("No value found for " + col.getId() + " in table " +
			 * row.getSchema().getSheetName() + ". Putting an empty value.");
			 * 
			 * colValue = new TableCell(); }
			 */

			if (colValue == null) {

				LOGGER.info("No value found for " + col.getId() + " in table " + row.getSchema().getSheetName()
						+ ". Putting an empty value.");

				colValue = new TableCell();
			}

			// save always the code
			String value = colValue.getCode();

			// if no code is found, use the label
			if (value == null || value.isEmpty())
				value = colValue.getLabel();

			// If we have a relation ID => then convert into integer
			try {

				if (isRelationId(row.getSchema(), col.getId()))
					stmt.setInt(currentIndex, Integer.valueOf(value));
				else {
					stmt.setString(currentIndex, value);
				}

			} catch (NumberFormatException | IOException e) {
				LOGGER.error("Wrong integer field " + col.getId() + " with value " + value, e);
				e.printStackTrace();
			}

			// increase current index
			currentIndex++;
		}

		// set also the id of the row
		if (setWhereId) {
			stmt.setInt(currentIndex, row.getDatabaseId());
		}
	}

	/**
	 * Add a new row to the table
	 * 
	 * @param row
	 * @return
	 */
	public int add(TableRow row) {

		int id = -1;

		try (Connection con = Database.getConnection();
				PreparedStatement stmt = con.prepareStatement(getAddQuery(row.getSchema()),
						Statement.RETURN_GENERATED_KEYS);) {

			// set the row values in the parameters
			setParameters(row, stmt, false);

			// insert the element
			stmt.executeUpdate();

			// get the newly generated id
			try (ResultSet rs = stmt.getGeneratedKeys();) {
				if (rs.next()) {
					id = rs.getInt(1);
				}
			}

		} catch (SQLException e) {
			LOGGER.error("Cannot add row to the table", e);
			e.printStackTrace();
		}

		if (id != -1) {
			LOGGER.debug("Row " + id + " successfully added in " + getTable(row.getSchema()));
		} else {
			LOGGER.error("Errors in adding " + row + " to " + getTable(row.getSchema()));
		}

		return id;
	}

	/**
	 * Add a new row to the table
	 * 
	 * @param row
	 * @return
	 */
	public boolean update(TableRow row) {

		boolean ok = true;

		try (Connection con = Database.getConnection();
				PreparedStatement stmt = con.prepareStatement(getUpdateQuery(row.getSchema()));) {

			// set the row values in the parameters
			// with the where id included
			setParameters(row, stmt, true);

			// insert the element
			stmt.executeUpdate();

		} catch (SQLException e) {
			LOGGER.error("Cannot update row", e);
			e.printStackTrace();
		
			ok = false;
		}

		if (ok) {
			LOGGER.debug("Row " + row.getDatabaseId() + " successfully updated in " + getTable(row.getSchema()));
		} else {
			LOGGER.error("Errors in updating " + row + " for " + getTable(row.getSchema()));
		}

		return ok;
	}

	/**
	 * Delete all the rows from the table
	 * 
	 * @param row
	 * @return
	 */
	public boolean deleteAll(TableSchema schema) {

		boolean ok = true;

		String query = "delete from " + getTable(schema);

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			stmt.executeUpdate();

		} catch (SQLException e) {
			LOGGER.error("Cannot delete rows", e);
			e.printStackTrace();
			ok = false;
		}

		if (ok) {
			LOGGER.debug("All rows successfully deleted from " + getTable(schema));
		} else {
			LOGGER.error("Cannot delete all rows from " + getTable(schema));
		}

		return ok;
	}

	/**
	 * Remove all the rows where the parent id is equal to {@code parentId} in the
	 * parent table {@code parentTable}
	 * 
	 * @param row
	 * @return
	 */
	public boolean deleteByParentId(TableSchema schema, String parentTable, int parentId) {

		boolean ok = true;

		Relation r = schema.getRelationByParentTable(parentTable);

		String query = "delete from " + getTable(schema) + " where " + r.getForeignKey() + " = ?";

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			// set the id of the parent
			stmt.setInt(1, parentId);

			stmt.executeUpdate();

		} catch (SQLException e) {
			LOGGER.error("Cannot delete rows by parent id=" + parentId, e);
			e.printStackTrace();
			ok = false;
		}

		return ok;
	}

	/**
	 * Get a row from the result set
	 * 
	 * @param rs
	 * @return
	 * @throws SQLException
	 */
	public TableRow getByResultSet(TableSchema schema, ResultSet rs, boolean solveFormulas) throws SQLException {

		// here we need all the columns because we also
		// compute composite fields
		TableRow row = new TableRow(schema);

		// put the id
		int id = rs.getInt(schema.getTableIdField());

		TableCell sel = new TableCell();
		sel.setCode(String.valueOf(id));
		sel.setLabel(String.valueOf(id));
		row.put(schema.getTableIdField(), sel);

		for (TableColumn column : schema) {

			TableCell selection = null;

			// create foreign key if necessary
			if (column.isForeignKey()) {

				// the foreign key is an integer id
				int value = rs.getInt(column.getId());

				selection = new TableCell();

				// we don't need the description for foreign id
				selection.setCode(String.valueOf(value));

				row.put(column.getId(), selection);
			} else {

				String value = null;

				try {
					value = rs.getString(column.getId());
				} catch (SQLException e) {
					LOGGER.error("Error in accessing column", e);
				}

				// if no value go to the next field
				if (value == null)
					continue;

				// if we have a picklist, we need both code and description
				if (column.isPicklist()) {

					String code = String.valueOf(value);

					// get the description from the .xml using the code
					if (code != null && !code.isEmpty()) {

						XmlContents contents = XmlLoader.getByPicklistKey(column.getPicklistKey());

						if (contents == null) {

							LOGGER.error("IMPORTANT: Check that the picklist " + column.getPicklistKey()
									+ " is in your " + AppPaths.XML_FOLDER
									+ " folder. Note that also the root node of the xml should have " + "the name "
									+ column.getPicklistKey() + ". Putting an empty value.");

							selection = new TableCell();
						} else if (contents.getElementByCode(code) == null) {

							LOGGER.error("IMPORTANT: The element " + code + " is missing in the picklist "
									+ column.getPicklistKey() + " in the " + AppPaths.XML_FOLDER
									+ " folder. Putting an empty value.");

							selection = new TableCell();
						} else {
							selection = new TableCell(contents.getElementByCode(code));
						}
					} else
						selection = new TableCell();
				} else {

					// if simple element, then it is sufficient the
					// description (which is the label)
					selection = new TableCell();
					selection.setCode(String.valueOf(value));
					selection.setLabel(String.valueOf(value));
				}
			}

			// set also the id of the row
			row.setId(rs.getInt(schema.getTableIdField()));

			if (selection.getLabel().isEmpty())
				selection.setLabel(selection.getCode());

			// insert the element into the row
			row.put(column.getId(), selection);
		}

		// solve automatic fields
		if (solveFormulas)
			row.updateFormulas();

		return row;
	}

	public TableRowList getByParentId(TableSchema schema, String parentTable, int parentId) {
		return getByParentId(schema, parentTable, parentId, true, "asc");
	}

	public TableRowList getByParentId(TableSchema schema, String parentTable, int parentId, boolean solveFormulas) {
		return getByParentId(schema, parentTable, parentId, solveFormulas, "asc");
	}

	/**
	 * Get all the rows that has as parent the {@code parentId} in the parent table
	 * {@code parentTable}
	 * 
	 * @param row
	 * @return
	 */
	public TableRowList getByParentId(TableSchema schema, String parentTable, int parentId, boolean solveFormulas,
			String order) {

		TableRowList rows = new TableRowList(schema);

		Relation r = schema.getRelationByParentTable(parentTable);

		String query = "select * from " + getTable(schema) + " where " + r.getForeignKey() + " = ? order by "
				+ schema.getTableIdField() + " " + order;

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			// set the id of the parent
			stmt.setInt(1, parentId);

			try (ResultSet rs = stmt.executeQuery();) {

				while (rs.next()) {

					TableRow row = getByResultSet(schema, rs, solveFormulas);

					if (row != null)
						rows.add(row);
				}
			} catch (SQLException e) {
				LOGGER.error("Cannot get rows by parentId=" + parentId, e);
				e.printStackTrace();
			}

		} catch (SQLException e) {
			LOGGER.error("Cannot get rows by parentId=" + parentId, e);
			e.printStackTrace();
		}

		return rows;
	}

	/**
	 * Get all the rows from the table
	 * 
	 * @param row
	 * @return
	 */
	public TableRowList getAll(TableSchema schema) {

		TableRowList rows = new TableRowList(schema);

		String query = "select * from " + getTable(schema) + " order by " + schema.getTableIdField() + " asc";

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			try (ResultSet rs = stmt.executeQuery();) {
				while (rs.next()) {

					TableRow row = getByResultSet(schema, rs, true);
					if (row != null)
						rows.add(row);
				}
			} catch (SQLException e) {
				LOGGER.error("Cannot get all rows", e);
				e.printStackTrace();
			}

		} catch (SQLException e) {
			LOGGER.error("Cannot get all rows", e);
			e.printStackTrace();
		}

		return rows;
	}

	/**
	 * Remove a row by its id
	 * 
	 * @param rowId
	 * @return
	 */
	public boolean delete(TableSchema schema, int rowId) {

		boolean ok = true;

		String query = "delete from " + getTable(schema) + " where " + schema.getTableIdField() + " = ?";

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			stmt.setInt(1, rowId);

			stmt.executeUpdate();

		} catch (SQLException e) {
			LOGGER.error("Cannot delete row with database id=" + rowId, e);
			e.printStackTrace();
			ok = false;
		}

		if (ok) {
			LOGGER.info("Row " + rowId + " successfully deleted from " + getTable(schema));
		} else {
			LOGGER.error("Row " + rowId + " cannot be deleted from " + getTable(schema));
		}

		return ok;
	}

	
	public boolean delete(TableRowList list) {

		boolean ok = true;

		if (list.isEmpty())
			return true;

		TableSchema schema = list.get(0).getSchema();

		String query = "delete from " + getTable(schema) + " where " + schema.getTableIdField() + " = ?";

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			for (TableRow row : list) {
				stmt.setInt(1, row.getDatabaseId());

				stmt.addBatch();
			}

			stmt.executeBatch();

		} catch (SQLException e) {
			LOGGER.error("Cannot delete rows", e);
			e.printStackTrace();
			ok = false;
		}

		return ok;
	}

	/**
	 * delete row and references in child tables
	 * 
	 * @author shahaal
	 
	public boolean delete(TableRowList list) {

		boolean ok = true;

		if (list.isEmpty())
			return true;

		// get the schema of the row
		TableSchema schema = list.get(0).getSchema();
		
		// array which will contain the current schema and the one of the child records
		List<String> schemas = new ArrayList<>();
		
		// add the schema name of the children
		try {
			for(Relation rel:schema.getChildrenTables())
				schemas.add(getTable(rel.getChild()));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// add the current schema name
		schemas.add(getTable(schema));
		
		// iterate each schema
		for (String schemaName : schemas) {
			
			String query = "delete from "+schemaName+" where " + schema.getTableIdField() + " = ?";
			System.out.println("shahaal "+query);
			
			try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

				for (TableRow row : list) {
					stmt.setInt(1, row.getDatabaseId());

					stmt.addBatch();
				}

				stmt.executeBatch();

			} catch (SQLException e) {
				e.printStackTrace();
				LOGGER.error("Cannot delete rows", e);
				ok = false;
			}
		}

		return ok;
	}
	*/

	/**
	 * shahaal add the cloned rows in db
	 * 
	 * @param list
	 * @return
	 */
	public boolean addAll(TableRowList list) {

		boolean ok = true;

		if (list.isEmpty())
			return ok;

		TableSchema schema = list.get(0).getSchema();

		try (Connection con = Database.getConnection();
				PreparedStatement stmt = con.prepareStatement(getAddQuery(schema), Statement.RETURN_GENERATED_KEYS);) {

			int count = 0;

			for (TableRow row : list) {
				// set the row values in the parameters
				setParameters(row, stmt, false);

				// add the batch and increment the counter
				// stmt.addBatch();
				count++;

				// execute the batch
				if (count == list.size()) {
					// insert the elements
					stmt.executeUpdate();
				}
			}

		} catch (SQLException e) {
			LOGGER.error("Cannot add list of records", e);
			e.printStackTrace();
			ok = false;
		}

		return ok;
	}

	/**
	 * Delete all the records by a database field
	 * 
	 * @param fieldName
	 * @param value
	 * @return
	 */
	public boolean deleteByStringField(TableSchema schema, String fieldName, String value) {

		boolean ok = true;

		String query = "delete from " + getTable(schema) + " where " + fieldName + " = ?";

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			stmt.setString(1, value);
			stmt.executeUpdate();

		} catch (SQLException e) {
			LOGGER.error("Cannot delete rows", e);
			e.printStackTrace();
			ok = false;
		}

		if (ok) {
			LOGGER.info("Rows with " + fieldName + " = " + value + " successfully deleted from " + getTable(schema));
		} else {
			LOGGER.error("Rows with " + fieldName + " = " + value + " cannot be deleted from " + getTable(schema));
		}

		return ok;
	}

	/**
	 * Get the row by its id
	 * 
	 * @param id
	 * @return
	 */
	public TableRow getById(TableSchema schema, int id) {

		TableRow row = null;

		String query = "select * from " + getTable(schema) + " where " + schema.getTableIdField() + " = ?";

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			stmt.setInt(1, id);

			try (ResultSet rs = stmt.executeQuery();) {
				if (rs.next()) {
					row = getByResultSet(schema, rs, true);
				}
			} catch (SQLException e) {
				LOGGER.error("Error in getting all the rows by id " + id, e);
				e.printStackTrace();
			}

		} catch (SQLException e) {
			LOGGER.error("Error in getting all the rows by id " + id, e);
			e.printStackTrace();
		}

		return row;
	}

	/**
	 * Get the all the rows that matches the fieldName with value
	 * 
	 * @param id
	 * @return
	 */
	public TableRowList getByStringField(TableSchema schema, String fieldName, String value) {

		TableRowList rows = new TableRowList(schema);

		String query = "select * from " + getTable(schema) + " where " + fieldName + " = ? order by "
				+ schema.getTableIdField() + " asc";

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			stmt.setString(1, value);

			try (ResultSet rs = stmt.executeQuery();) {
				while (rs.next()) {
					TableRow row = getByResultSet(schema, rs, true);
					rows.add(row);
				}
			} catch (SQLException e) {
				LOGGER.error("Error in getting all the rows that matches the fieldName with value " + value, e);
				e.printStackTrace();
			}

		} catch (SQLException e) {
			LOGGER.error("Error in getting all the rows that matches the fieldName with value " + value, e);
			e.printStackTrace();
		}

		return rows;
	}
}
