package table_database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app_config.AppPaths;
import app_config.PropertiesReader;
import sql.SQLExecutor;
import table_skeleton.TableColumn;
import xlsx_reader.TableSchema;
import xlsx_reader.TableSchemaList;

/**
 * Class which creates the application database. It uses an .xlsx file to define
 * the structure (tables/columns) of the database.
 * 
 * @author avonva
 * @author shahaal
 *
 */
public class DatabaseBuilder implements IDatabaseBuilder {

	private static final Logger LOGGER = LogManager.getLogger(DatabaseBuilder.class);

	private static final String DB_URL = "jdbc:derby:" + AppPaths.DB_FOLDER + ";create=true";

	/**
	 * Create the application database in the defined path
	 * 
	 * @param path
	 * @throws IOException
	 */
	public void create(String path) throws IOException {

		DatabaseStructureCreator queryCreator = new DatabaseStructureCreator();

		String query = queryCreator.getCreateDatabaseQuery(TableSchemaList.getAll());

		// create the database
		try (Connection con = DriverManager.getConnection(DB_URL); SQLExecutor executor = new SQLExecutor(con);) {
			executor.exec(query);

			addDbInfo();

		} catch (IOException | SQLException e) {
			LOGGER.error("Cannot create database in folder=" + path, e);
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Create a new table
	 * 
	 * @param table
	 * @throws SQLException
	 * @throws IOException
	 */
	public void createTable(TableSchema table) throws SQLException, IOException {

		DatabaseStructureCreator creator = new DatabaseStructureCreator();
		String query = creator.getNewTableQuery(table);

		try (Connection con = DriverManager.getConnection(DB_URL); SQLExecutor executor = new SQLExecutor(con);) {
			executor.exec(query);
		}
		LOGGER.debug("Creating table " + table + " completed");
	}

	/**
	 * Add a column to a table
	 * 
	 * @param schema
	 * @param column
	 * @throws IOException
	 * @throws SQLException
	 */
	public void addColumnToTable(TableSchema schema, TableColumn column) throws IOException, SQLException {

		DatabaseStructureCreator creator = new DatabaseStructureCreator();

		String query = creator.getAddNewColumnQuery(schema.getSheetName(), column);

		try (Connection con = DriverManager.getConnection(DB_URL); SQLExecutor executor = new SQLExecutor(con);) {
			executor.exec(query);
		}
		LOGGER.debug("Adding column to table " + column + " completed");
	}

	/**
	 * Add a foreign key to the database
	 * 
	 * @param schema
	 * @param foreignKey
	 * @throws IOException
	 * @throws SQLException
	 */
	public void addForeignKey(TableSchema schema, TableColumn foreignKey) throws IOException, SQLException {

		DatabaseStructureCreator creator = new DatabaseStructureCreator();

		String query = creator.getAddForeignKeyQuery(schema.getSheetName(), foreignKey);

		try (Connection con = DriverManager.getConnection(DB_URL); SQLExecutor executor = new SQLExecutor(con);) {
			executor.exec(query);
		}
		LOGGER.debug("Adding foreiqn key " + foreignKey + " completed");
	}

	/**
	 * Remove the foreign key from the table in the database
	 * 
	 * @param schema
	 * @param foreignKey
	 * @throws IOException
	 * @throws SQLException
	 */
	public void removeForeignKey(TableSchema schema, TableColumn foreignKey) throws IOException, SQLException {

		DatabaseStructureCreator creator = new DatabaseStructureCreator();

		String query = creator.getRemoveForeignKeyQuery(schema.getSheetName(), foreignKey.getId());

		try (Connection con = DriverManager.getConnection(DB_URL); SQLExecutor executor = new SQLExecutor(con);) {
			executor.exec(query);
		}
		LOGGER.debug("Removing foreiqn key " + foreignKey + " completed");
	}

	/**
	 * Get a foreign key property using its column name
	 * 
	 * @param foreignKeyColName
	 * @return
	 * @throws SQLException
	 */
	public ForeignKey getForeignKeyByColumnName(String fkTableName, String foreignKeyColName) throws SQLException {

		ForeignKey key = null;
		try (ResultSet rs = DriverManager.getConnection(DB_URL).getMetaData().getImportedKeys("", "APP",
				fkTableName.toUpperCase())) {

			while (rs.next()) {

				// get column name of fk
				String fkColumnName = rs.getString("FKCOLUMN_NAME");

				// consider only the wanted foreign key
				if (fkColumnName.equalsIgnoreCase(foreignKeyColName)) {
					String fkName = rs.getString("FK_NAME");
					String tableName = rs.getString("FKCOLUMN_NAME");
					key = new ForeignKey(tableName, fkColumnName, fkName);
					break;
				}
			}
		}
		LOGGER.info("Foreiqn key: " + key);
		return key;
	}

	/**
	 * Add basic information to the database
	 * 
	 * @throws IOException
	 * @throws SQLException
	 */
	private void addDbInfo() throws IOException, SQLException {

		// sql script to create the database
		String query = "insert into " + DatabaseStructureCreator.DB_INFO_TABLE + " (VAR_KEY, VAR_VALUE) values (?, ?)";

		try (Connection con = DriverManager.getConnection(DB_URL);
				PreparedStatement stmt = con.prepareStatement(query);) {

			// add the database version
			stmt.setString(1, "DB_VERSION");
			stmt.setString(2, PropertiesReader.getAppVersion());

			stmt.addBatch();

			// add the date of creation
			stmt.setString(1, "DB_CREATED");
			String today = new Date(System.currentTimeMillis()).toString();
			stmt.setString(2, today);

			stmt.addBatch();

			stmt.executeBatch();
		}
	}
}
