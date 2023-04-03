package amend_manager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dataset.Dataset;
import dataset.DatasetList;
import dataset.IDataset;
import dataset.NoAttachmentException;
import formula.FormulaException;
import progress_bar.ProgressListener;
import providers.IReportService;
import providers.ITableDaoService;
import soap.DetailedSOAPException;
import table_skeleton.TableRow;
import table_skeleton.TableRowList;
import table_skeleton.TableVersion;

public abstract class ReportImporter {

	private static final Logger LOGGER = LogManager.getLogger(ReportImporter.class);

	private ProgressListener progressListener;

	private TableRowList newVersions;
	private TableRowList oldVersions;
	private DatasetList datasetVersions;
	private String senderDatasetId;
	private String rowIdField;
	private String versionField;

	private int processedDatasets;

	private ITableDaoService daoService;
	private IReportService reportService;

	/**
	 * Download and import a dataset using all its versions to manage the
	 * amendments.
	 * 
	 * @param datasetVersions
	 * @param rowIdField      name of the field which is the identified of the
	 *                        dataset
	 * @param versionField    name of the field which contains the dataset version
	 *                        using the format value.version, as FR1704.01
	 *                        (senderDatasetId.version)
	 */
	public ReportImporter(String rowIdField, String versionField, IReportService reportService,
			ITableDaoService daoService) {

		this.processedDatasets = 1;

		this.rowIdField = rowIdField;
		this.versionField = versionField;
		this.daoService = daoService;
		this.reportService = reportService;
	}

	/**
	 * Listen to the process progresses
	 * 
	 * @param progressListener
	 */
	public void setProgressListener(ProgressListener progressListener) {
		this.progressListener = progressListener;
	}

	/**
	 * Set the progress (call the listener if set)
	 * 
	 * @param progress
	 */
	private void setProgress(double progress) {
		if (this.progressListener != null)
			this.progressListener.progressChanged(progress);
	}

	private void saveOldVersions() {
		this.oldVersions = reportService.getAllVersions(this.senderDatasetId);
	}

	/**
	 * Delete all the old versions of the report
	 */
	public void deleteOldVersions() {
		LOGGER.debug("Deleting the old versions of the report if were present");
		daoService.delete(oldVersions);
	}

	public void abort() {
		if (this.newVersions != null)
			daoService.delete(newVersions);
	}

	private Dataset download(Dataset dataset)
			throws XMLStreamException, IOException, DetailedSOAPException, NoAttachmentException {
		LOGGER.debug("Downloading..");
		String datasetId = dataset.getId();

		File file = reportService.download(datasetId);

		Dataset populatedDataset = reportService.datasetFromFile(file);

		// add the get datasets list metadata to the dataset object
		populatedDataset.setStatus(dataset.getRCLStatus());
		populatedDataset.setSenderId(dataset.getSenderId());
		populatedDataset.setId(populatedDataset.getOperation().getDatasetId());
		populatedDataset.setLastMessageId(dataset.getLastMessageId());
		populatedDataset.setLastModifyingMessageId(dataset.getLastModifyingMessageId());
		populatedDataset.setLastValidationMessageId(dataset.getLastValidationMessageId());

		return populatedDataset;
	}

	public void setDatasetVersions(DatasetList datasetVersions) {
		this.datasetVersions = datasetVersions;

		// get the sender id of the dataset versions
		if (!datasetVersions.isEmpty()) {
			senderDatasetId = datasetVersions.get(0).getDecomposedSenderId();
		} else {
			throw new IllegalArgumentException("Cannot import an empty dataset list");
		}
	}

	/**
	 * Import an entire report (composed of several dataset versions) The amendment
	 * is also managed here
	 * 
	 * @throws DetailedSOAPException
	 * @throws XMLStreamException
	 * @throws IOException
	 * @throws FormulaException
	 * @throws NoAttachmentException
	 * @throws ParseException
	 */
	public void importReport() throws DetailedSOAPException, XMLStreamException, IOException, FormulaException,
			NoAttachmentException, ParseException {

		LOGGER.info("Report downloader started for report=" + senderDatasetId);

		// save old versions of the report if present
		saveOldVersions();

		// clear cache
		clearTable();

		int k = getLastAcceptedVersion(); // version of last accepted dataset
		int n = getLastExistingVersion(); // version of last dataset

		LOGGER.debug("Last version found=" + n + ", while last ACCEPTED_DWH version found=" + k);

		LOGGER.debug("Versions=" + datasetVersions);

		// sort the datasets by version ascendent
		datasetVersions.sortAsc();

		// in order, import the datasets processing the amendments if needed
		for (IDataset data : datasetVersions) {

			Dataset dataset = (Dataset) data;

			setProgress(processedDatasets / datasetVersions.size() * 25);

			LOGGER.debug("importSingleVersion=" + dataset);

			// import the single dataset into db
			importSingleVersion((Dataset) dataset);

			setProgress(processedDatasets / datasetVersions.size() * 100);
			processedDatasets++;

			// get the dataset version
			int currentVersion = TableVersion.getNumVersion(dataset.getVersion());

			LOGGER.debug("The version of the imported dataset is " + currentVersion);

			if (currentVersion == k || currentVersion == n) {

				if (currentVersion == n)
					LOGGER.debug("-> which is the last one");
				else
					LOGGER.debug("-> which is the last accepted one");

				LOGGER.debug("--> therefore process amendments and create the report");

				// populate the dataset with metadata (operation/header)
				Dataset popDataset = download(dataset);

				// process the dataset header/operation
				TableRow newReport = importDatasetMetadata(popDataset);
				if (this.newVersions == null) {
					this.newVersions = new TableRowList(newReport.getSchema());
				}

				newVersions.add(newReport);

				// process the amendments of the current dataset
				LOGGER.debug("Processing amendments");
				processAmendments();

				// generate local report starting from dataset
				LOGGER.debug("Saving the imported report version into the database");
				createLocalReport();

				// if we have reached the last processable dataset stop
				if (currentVersion == n) {
					break;
				}
			}
		}

		// at the end clear the database table
		clearTable();

		// delete all the old versions (we don't need them anymore)
		deleteOldVersions();

		if (this.progressListener != null)
			this.progressListener.progressCompleted();

		LOGGER.info("Report downloader ended for report=" + senderDatasetId);
	}

	/**
	 * Import a dataset from an .xml file. Note that amendments are not processed
	 * with this method, therefore it can be used only with the first version of a
	 * report.
	 * 
	 * @param file
	 * @throws XMLStreamException
	 * @throws IOException
	 * @throws FormulaException
	 * @throws ParseException
	 */
	public void importFirstDatasetVersion(File file)
			throws XMLStreamException, IOException, FormulaException, ParseException {

		this.importDatasetFile(file);
		Dataset d = reportService.datasetFromFile(file);
		importDatasetMetadata(d);
		this.createLocalReport();
	}

	/**
	 * Import a single dataset version into the database
	 * 
	 * @param dataset
	 * @throws DetailedSOAPException
	 * @throws XMLStreamException
	 * @throws IOException
	 * @throws NoAttachmentException
	 */
	private void importSingleVersion(Dataset dataset)
			throws DetailedSOAPException, XMLStreamException, IOException, NoAttachmentException {

		// download the dataset file
		File file = reportService.download(dataset.getId());

		setProgress(processedDatasets / datasetVersions.size() * 75);

		// import the file
		importDatasetFile(file);
	}

	/**
	 * Import a dataset file into the comparison table
	 * 
	 * @param file
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	private void importDatasetFile(File file) throws XMLStreamException, IOException {

		if (file == null || !file.exists()) {
			throw new IOException("Cannot find the dataset attachment in the DCF response.");
		}

		// parse it to extract the relevant information
		try (DatasetComparisonParser parser = new DatasetComparisonParser(file, rowIdField, versionField);) {

			// for each dataset comparison insert into the db
			DatasetComparison comp;
			while ((comp = parser.next()) != null) {
				DatasetComparisonDao dao = new DatasetComparisonDao();
				dao.add(comp);
			}

			parser.close();
		}
	}

	/**
	 * Process the amendments of the current processed dataset
	 */
	private void processAmendments() {
		deleteNullifiedRecords();
		deleteOldVersionsOfRecords();
		deleteRemovedRecords();
	}

	/**
	 * Delete all the records which were amended as deleted
	 */
	private void deleteNullifiedRecords() {

		StringBuilder query = new StringBuilder();
		query.append("delete from APP.DATASET_COMPARISON ").append("where IS_NULLIFIED = '1'");

		DatasetComparisonDao dao = new DatasetComparisonDao();
		dao.executeQuery(query.toString());
	}

	private void deleteOldVersionsOfRecords() {

		StringBuilder query = new StringBuilder();
		query.append("delete from APP.DATASET_COMPARISON ").append("where ROW_ID || VERSION not in (")
				.append("select ROW_ID || MAX(VERSION) from APP.DATASET_COMPARISON group by ROW_ID)");

		DatasetComparisonDao dao = new DatasetComparisonDao();
		dao.executeQuery(query.toString());
	}

	private void deleteRemovedRecords() {

		StringBuilder query = new StringBuilder();
		query.append("delete from APP.DATASET_COMPARISON ").append("where AM_TYPE = 'D'");

		DatasetComparisonDao dao = new DatasetComparisonDao();
		dao.executeQuery(query.toString());
	}

	/**
	 * Create the local report using the data received up to now
	 * 
	 * @throws IOException
	 * @throws XMLStreamException
	 * @throws FormulaException
	 * @throws ParseException
	 */
	private void createLocalReport() throws XMLStreamException, IOException, FormulaException, ParseException {

		DatasetComparisonDao dao = new DatasetComparisonDao();
		List<DatasetComparison> list = dao.getAll();
		List<TableRow> rows = new ArrayList<>();

		for (DatasetComparison comp : list) {
			TableRow row = getRowFromXml(comp.getXmlRecord());
			rows.add(row);
		}

		// import the rows
		importDatasetRows(rows);
	}

	/**
	 * Extract the table row from the xml
	 * 
	 * @param xmlRecord
	 * @return
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	private TableRow getRowFromXml(String xmlRecord) throws XMLStreamException, IOException {

		String encoding = StandardCharsets.UTF_8.name();

		// add root to create a well formed xml
		xmlRecord = "<dummy>" + xmlRecord + "</dummy>";

		InputStream input = new ByteArrayInputStream(xmlRecord.getBytes(encoding));
		RowParser parser = new RowParser(input);

		TableRow row = parser.parse();

		parser.close();

		return row;
	}

	/**
	 * Get the last version of the dataset which is accepted
	 * 
	 * @return
	 */
	private int getLastAcceptedVersion() {
		Dataset lastAccepted = (Dataset) datasetVersions.getLastAcceptedVersion(senderDatasetId);

		if (lastAccepted == null)
			return 0;

		int lastAcceptedVersion = Integer.valueOf(lastAccepted.getVersion());
		LOGGER.debug("Last Accepted Version", lastAcceptedVersion);
		return lastAcceptedVersion;
	}

	/**
	 * Get the last version of the dataset which is not deleted/rejected
	 * 
	 * @return
	 */
	private int getLastExistingVersion() {
		Dataset lastExisting = (Dataset) datasetVersions.getLastExistingVersion(senderDatasetId);

		if (lastExisting == null)
			return 0;
		
		int lastExistingVersion = Integer.valueOf(lastExisting.getVersion());
		LOGGER.debug("Last Existing Version", lastExistingVersion);
		return lastExistingVersion;
	}

	/**
	 * Clear comparisons table
	 */
	private void clearTable() {
		LOGGER.debug("Clearing DatasetComparison table");
		DatasetComparisonDao dao = new DatasetComparisonDao();
		dao.deleteAll();
	}

	/**
	 * Import the dataset header/operation
	 * 
	 * @param dataset
	 */
	public abstract TableRow importDatasetMetadata(Dataset dataset);

	/**
	 * Import into the local database the row (depends on the data collection)
	 * 
	 * @param row
	 */
	public abstract void importDatasetRows(List<TableRow> row) throws FormulaException, ParseException;
}
