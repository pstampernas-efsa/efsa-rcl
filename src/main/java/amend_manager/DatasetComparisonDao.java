package amend_manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import table_database.Database;

public class DatasetComparisonDao {

	private static final Logger LOGGER = LogManager.getLogger(DatasetComparisonDao.class);
	
	/**
	 * add an element to the table
	 * @param comp
	 */
	public void add(DatasetComparison comp) {
		LOGGER.info(comp.getRowId()+", "+comp.getVersion()+", "+comp.getXmlRecord()+", "+comp.getAmType()+", "+comp.getIsNullified());
		
		String query = "insert into APP.DATASET_COMPARISON (ROW_ID, VERSION, XML_RECORD, AM_TYPE, IS_NULLIFIED) values (?,?,?,?,?)";

		try (Connection con = Database.getConnection(); 
				PreparedStatement stmt = con.prepareStatement(query, 
						Statement.RETURN_GENERATED_KEYS);) {
			
			stmt.setString(1, comp.getRowId());
			stmt.setString(2, comp.getVersion());
			stmt.setString(3, comp.getXmlRecord());
			
			if (comp.getAmType() == null)
				stmt.setNull(4, Types.VARCHAR);
			else 
				stmt.setString(4, comp.getAmType().getCode());
			
			if (comp.getIsNullified() == null)
				stmt.setNull(5, Types.VARCHAR);
			else 
				stmt.setString(5, comp.getIsNullified());
			
			stmt.executeUpdate();
		}
		catch (SQLException e) {
			LOGGER.error("Cannot add a new dataset comparison=" + comp, e);
			e.printStackTrace();
		}
	}
	
	public DatasetComparison getByResultSet(ResultSet rs) throws SQLException {
		
		String rowId = rs.getString("ROW_ID");
		String version = rs.getString("VERSION");
		String xmlRecord = rs.getString("XML_RECORD");
		AmendType amType = AmendType.fromCode(rs.getString("AM_TYPE"));
		String isNullified = rs.getString("IS_NULLIFIED");
		
		return new DatasetComparison(rowId, version, xmlRecord, amType, isNullified);
	}

	public List<DatasetComparison> getAll() {
		
		List<DatasetComparison> comparisons = new ArrayList<>();
		
		String query = "select * from APP.DATASET_COMPARISON";
		
		try (Connection con = Database.getConnection(); 
				PreparedStatement stmt = con.prepareStatement(query);) {

			try (ResultSet rs = stmt.executeQuery();) {
				while (rs.next()) {
					comparisons.add(getByResultSet(rs));
				}
			}
		}
		catch (SQLException e) {
			LOGGER.error("Cannot get all the dataset comparisons", e);
			e.printStackTrace();
		}
		
		return comparisons;
	}
	
	/**
	 * Clear the table
	 */
	public void deleteAll() {
		
		String query = "delete from APP.DATASET_COMPARISON";

		try (Connection con = Database.getConnection(); 
				PreparedStatement stmt = con.prepareStatement(query);) {

			stmt.executeUpdate();
		}
		catch (SQLException e) {
			LOGGER.error("Cannot delete all the dataset comparisons", e);
			e.printStackTrace();
		}
	}
	
	/**
	 * Execute a query
	 * @param query
	 */
	public void executeQuery(String query) {

		try (Connection con = Database.getConnection(); 
				PreparedStatement stmt = con.prepareStatement(query);) {

			stmt.executeUpdate();
		}
		catch (SQLException e) {
			LOGGER.error("Cannot execute query=" + query, e);
			e.printStackTrace();
		}
	}
}
