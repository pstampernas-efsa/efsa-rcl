package table_database;

import java.io.File;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app_config.AppPaths;
import app_config.PropertiesReader;
import version_manager.VersionComparator;
import xlsx_reader.TableSchema;
import xlsx_reader.TableSchemaList;

/**
 * Start the database if present, otherwise create it.
 * 
 * @author avonva
 *
 */
public class Database {

	private static final Logger LOGGER = LogManager.getLogger(Database.class);

	private static final String DB_URL = "jdbc:derby:" + AppPaths.DB_FOLDER;
	private static final String CLOSE_DB_URL = DB_URL + ";shutdown=true";

	private IDatabaseBuilder dbBuilder;

	public Database(IDatabaseBuilder dbBuilder) {
		this.dbBuilder = dbBuilder;
	}

	// TODO to be removed
	public Database() {
		this.dbBuilder = new DatabaseBuilder();
	}

	/**
	 * Connect to the main catalogues database if present, otherwise create it and
	 * then connect
	 * 
	 * @param DBString
	 * @throws IOException
	 * @throws Exception
	 */
	public void connect() throws IOException {

		LOGGER.info("Connecting to database");

		try {

			// load the jdbc driver
			LOGGER.debug("Starting embedded database...");

			Class.forName("org.apache.derby.jdbc.EmbeddedDriver");

			// check if the database is present or not
			LOGGER.debug("Testing database connection...");

			Connection con = getConnection();
			con.close();

		} catch (ClassNotFoundException e) {
			LOGGER.fatal("Cannot start embedded database: embedded driver missing", e);
			e.printStackTrace();

		} catch (SQLException e1) {

			LOGGER.debug("Creating new database...");
	
			DatabaseBuilder creator = new DatabaseBuilder();
			creator.create(AppPaths.DB_FOLDER);
		}
	}

	/**
	 * Update the database with the last version available
	 * 
	 * @throws IOException
	 * @throws SQLException
	 * @throws DatabaseVersionException
	 */
	public void update() throws IOException, SQLException, DatabaseVersionException {

		// get db version
		String dbVersion = this.getVersion();

		if (dbVersion == null) {
			throw new DatabaseVersionException("No database version found. Cannot check updates.");
		}

		// get min required db version
		String minRequiredVersion = PropertiesReader.getMinRequiredDbVersion();

		if (minRequiredVersion == null) {
			throw new IOException(
					"Cannot retrieve the minimum database version needed to run the tool properly. Check the configuration file.");
		}

		VersionComparator versionComparator = new VersionComparator();

		int compare = versionComparator.compare(minRequiredVersion, dbVersion);

		// if updates are needed, check schemas differences
		// and apply them to the database
		if (compare > 0) {

			LOGGER.info("Database structure needs update");

			File oldSchema = new File(AppPaths.COMPAT_FOLDER + AppPaths.TABLES_SCHEMA_FILENAME + "." + dbVersion
					+ AppPaths.TABLES_SCHEMA_FORMAT);

			File newSchema = new File(AppPaths.TABLES_SCHEMA_FILE);

			// update the database
			DatabaseUpdater upd = new DatabaseUpdater(dbBuilder);
			upd.update(oldSchema, newSchema);

			// update the version with the current
			this.updateVersion(PropertiesReader.getAppVersion());
		} else {
			LOGGER.info("Database structure is up to date");
		}
	}

	/**
	 * Get the version of the database if available
	 * 
	 * @return
	 */
	public String getVersion() {
		return getInfo("DB_VERSION");
	}

	/**
	 * Update the version of the database
	 * 
	 * @param value
	 */
	public void updateVersion(String value) {
		updateInfo("DB_VERSION", value);
	}

	/**
	 * Update a generic info of the database
	 * 
	 * @param key
	 * @param value
	 */
	public void updateInfo(String key, String value) {

		String query = "update " + DatabaseStructureCreator.DB_INFO_TABLE + " set VAR_VALUE = ? where VAR_KEY = ?";

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			stmt.setString(1, value);
			stmt.setString(2, key);
			stmt.executeUpdate();
		} catch (SQLException e) {
			LOGGER.error("Cannot update database properties, key=" + key + " value=" + value, e);
			e.printStackTrace();
		}
	}

	/**
	 * Get a generic database information
	 * 
	 * @return
	 */
	public String getInfo(String key) {

		String version = null;

		String query = "select VAR_VALUE from " + DatabaseStructureCreator.DB_INFO_TABLE + " where VAR_KEY = ?";

		try (Connection con = Database.getConnection(); PreparedStatement stmt = con.prepareStatement(query);) {

			stmt.setString(1, key);

			try (ResultSet rs = stmt.executeQuery();) {
				if (rs.next()) {
					version = rs.getString("VAR_VALUE");
				}
			}
		} catch (SQLException e) {
			LOGGER.error("Cannot get database property key=" + key, e);
			e.printStackTrace();
		}

		return version;
	}

	/**
	 * Compress the database to avoid fragmentation
	 * 
	 * @throws IOException
	 */
	public void compress() throws IOException {

		LOGGER.info("Compressing database");

		String query = "CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, ?)";

		try (Connection con = Database.getConnection(); CallableStatement cs = con.prepareCall(query);) {

			TableSchemaList tables = TableSchemaList.getAll();

			for (TableSchema table : tables) {

				cs.setString(1, "APP");
				cs.setString(2, table.getSheetName().toUpperCase());
				cs.setShort(3, (short) 1);
				cs.addBatch();
			}

			cs.executeBatch();

		} catch (SQLException e) {
			LOGGER.error("Cannot compress database", e);
			e.printStackTrace();
		}
	}

	/**
	 * Delete the database folder
	 * 
	 * @throws IOException
	 */
	public void delete() throws IOException {

		LOGGER.info("Deleting database");

		this.shutdown();
		File dir = new File(AppPaths.DB_FOLDER);
		FileUtils.deleteDirectory(dir);
	}

	/**
	 * Shutdown the database
	 * 
	 * @throws SQLException
	 */
	public void shutdown() {

		LOGGER.info("Shutting down database");

		try {
			DriverManager.getConnection(CLOSE_DB_URL);
		} catch (SQLException e) {
			LOGGER.error("Error in shutting down the database ", e);
			e.printStackTrace();
		}
	}

	/**
	 * Get the connection with the database
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static Connection getConnection() throws SQLException {
		Connection con = DriverManager.getConnection(DB_URL);
		return con;
	}
}
