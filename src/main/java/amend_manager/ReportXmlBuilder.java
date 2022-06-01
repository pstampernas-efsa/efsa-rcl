package amend_manager;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import message.MessageConfigBuilder;
import message_creator.MessageXmlBuilder;
import progress_bar.ProgressListener;
import providers.IFormulaService;
import providers.ITableDaoService;
import report.EFSAReport;
import report.ReportException;
import table_skeleton.TableRow;

public class ReportXmlBuilder implements AutoCloseable {
	
	private static final Logger LOGGER = LogManager.getLogger(ReportXmlBuilder.class);
	
	private EFSAReport report;
	private MessageConfigBuilder messageConfig;
	private String rowIdField;
	private ProgressListener progressListener;
	
	private ITableDaoService daoService;
	private IFormulaService formulaService;
	
	/**
	 * Send a report to the DCF
	 * @param report report which will be exported
	 * @param messageConfig configuration used to create the export file
	 * @param rowIdField name of the field which contains the row id of the record
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	public ReportXmlBuilder(EFSAReport report, MessageConfigBuilder messageConfig, 
			String rowIdField, ITableDaoService daoService, IFormulaService formulaService) {

		this.report = report;
		this.messageConfig = messageConfig;
		this.rowIdField = rowIdField;
		
		this.daoService = daoService;
		this.formulaService = formulaService;
	}
	
	public void setProgressListener(ProgressListener progressListener) {
		this.progressListener = progressListener;
	}
	
	/**
	 * Set the progress (call the listener if set)
	 * @param progress
	 */
	private void setProgress(double progress) {
		if (this.progressListener != null)
			this.progressListener.progressChanged(progress);
	}
	
	/**
	 * Create an empty report with the desired header/operation
	 * @param messageConfig
	 * @return
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	public static File createEmptyReport(MessageConfigBuilder messageConfig) 
			throws ParserConfigurationException, SAXException, IOException {
		
		try(MessageXmlBuilder creator = new MessageXmlBuilder(
				messageConfig.getOut(), messageConfig);) {
			
			return creator.exportEmpty();
		}
	}
	
	private void clearTable() {
		DatasetComparisonDao dao = new DatasetComparisonDao();
		dao.deleteAll();
	}
	
	/**
	 * 
	 * @return
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws ReportException
	 * @throws AmendException 
	 */
	public File exportReport() throws IOException, ParserConfigurationException, 
		SAXException, ReportException, AmendException {
		
		LOGGER.info("Exporting report " + report);
		
		clearTable();
		
		// extract the report into the comparisons table
		extractSingleVersion(report);
		
		String latestVersion = report.getVersion();
		
		LOGGER.debug("The version of the report is " + latestVersion);
		
		setProgress(40);
		
		// if baseline, just extract it and export it
		if (report.isBaselineVersion()) {
			LOGGER.info("Export finished since the report does not have amended versions");
			setProgress(100);
			File file = createXmlFile();
			clearTable();
			return file;
		}
		
		// otherwise extract also the previous version
		EFSAReport previousReport = report.getPreviousVersion(daoService);
		
		if (previousReport == null) {
			throw new ReportException("Cannot export report " 
					+ report.getVersion() 
					+ " since its previous version cannot be found.");
		}
		
		// extract also the previous report
		extractSingleVersion(previousReport);
		
		String oldVersion = previousReport.getVersion();
		
		setProgress(60);
		
		// solve the amendments of the two versions
		solveDuplications(latestVersion, oldVersion);
		
		setProgress(80);
		
		// export the final xml file with the merged dataset
		File xml = createXmlFile();
		
		setProgress(100);
		
		clearTable();
		
		return xml;
	}
	
	/**
	 * Extract a single version and put it into the database
	 * @param record
	 */
	private void extractSingleVersion(EFSAReport report) {
		
		// for each row
		for (TableRow record : report.getRecords(daoService)) {
		
			LOGGER.debug("Adding to the DATASET_COMPARISON table the record " + record);
			
			// update all the record formulas
			formulaService.updateFormulas(record);

			// get the row id from the record
			String rowId = record.getLabel(rowIdField);
			
			// get the version
			String version = report.getVersion();
			
			// create the dataset comparison object
			DatasetComparison comp = new DatasetComparison(rowId, version, record.toXml(false));

			// save it into the comparison table
			DatasetComparisonDao dao = new DatasetComparisonDao();
			dao.add(comp);
		}
	}
	
	/**
	 * Solve the duplications and set the amendments
	 * @throws AmendException 
	 */
	private void solveDuplications(String latestVersion, String oldVersion) throws AmendException {
		
		DatasetComparisonDao dao = new DatasetComparisonDao();
		
		removeOldRecordVersions();
		setUpdateAmendment(latestVersion, oldVersion);
		setDeleteAmendment(latestVersion, oldVersion);
		
		if (dao.getAll().isEmpty())
			throw new AmendException("Cannot create .xml file with no data");
	}
	
	/**
	 * Create the xml file of the dataset
	 * @return
	 * @throws IOException
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	private File createXmlFile() throws IOException, ParserConfigurationException, SAXException {
		
		DatasetComparisonDao dao = new DatasetComparisonDao();
		Collection<DatasetComparison> comps = dao.getAll();
		
		// export the xml file
		try(MessageXmlBuilder creator = new MessageXmlBuilder(
				messageConfig.getOut(), this.messageConfig);) {
			
			LOGGER.debug("Creating the xml file of the dataset");
			return creator.export(comps);
		}
	}
	
	/**
	 * Remove all the records which belong to an old version
	 * and they were replaced by a newer version
	 */
	private void removeOldRecordVersions() {

		StringBuilder query = new StringBuilder();
		query.append("delete from APP.DATASET_COMPARISON ")
			.append("where ROW_ID in ( ")
				.append("select ROW_ID ")
				.append("from APP.DATASET_COMPARISON ")
				.append("group by ROW_ID, XML_RECORD ")
				.append("having COUNT(VERSION) = 2 ")
			.append(") ");
		
		DatasetComparisonDao dao = new DatasetComparisonDao();
		dao.executeQuery(query.toString());
		
		LOGGER.debug("Removing all the records which belonged to an old version");
	}
	
	/**
	 * Set the update amendment to the updated row
	 */
	private void setUpdateAmendment(String latestVersion, String oldVersion) {
		
		StringBuilder query = new StringBuilder();
		
		// set update amendment
		query.append("update APP.DATASET_COMPARISON ")
			.append("set XML_RECORD = XML_RECORD || '<amType>U</amType>',")
			.append("AM_TYPE = 'U' ")
			.append("where VERSION = ")
			.append("'").append(latestVersion).append("'")
			.append(" and ")
			.append("ROW_ID in (")
				.append("select ROW_ID ")
				.append("from APP.DATASET_COMPARISON ")
				.append("where VERSION = ")
				.append("'").append(oldVersion).append("'")
				.append(" and ROW_ID in (")  // avoid to set amType = U for new records
					.append("select ROW_ID ")
					.append("from APP.DATASET_COMPARISON ")
					.append("group by ROW_ID ")
					.append("having COUNT(VERSION) = 2")
				.append(")")
			.append(")");
		
		DatasetComparisonDao dao = new DatasetComparisonDao();
		dao.executeQuery(query.toString());
		
		// then delete the old record versions related
		// to the just changed records
		StringBuilder query2 = new StringBuilder();
		query2.append("delete from APP.DATASET_COMPARISON ")
			.append("where AM_TYPE is null ")
			.append("and ")
			.append("ROW_ID in (")
				.append("select ROW_ID ")
				.append("from APP.DATASET_COMPARISON ")
				.append("where AM_TYPE = 'U' ")
			.append(")");
		
		dao.executeQuery(query2.toString());
	}
	
	/**
	 * Set the delete amendment for records that are present
	 * just in the older version (i.e. they were deleted)
	 */
	private void setDeleteAmendment(String latestVersion, String oldVersion) {

		StringBuilder query = new StringBuilder();
		
		// set delete amendment
		query.append("update APP.DATASET_COMPARISON ")
			.append("set XML_RECORD = XML_RECORD || '<amType>D</amType>',")
			.append("AM_TYPE = 'D' ")
			.append("where VERSION = ")
			.append("'").append(oldVersion).append("'")
			.append(" and ")
			.append("ROW_ID not in (")
				.append("select ROW_ID ")
				.append("from APP.DATASET_COMPARISON ")
				.append("where VERSION = ")
				.append("'").append(latestVersion).append("'")
			.append(")");
		
		DatasetComparisonDao dao = new DatasetComparisonDao();
		dao.executeQuery(query.toString());
	}

	@Override
	public void close() {
		LOGGER.debug("Clearing temp table");
		// clear the temporary table
		clearTable();
	}
}
