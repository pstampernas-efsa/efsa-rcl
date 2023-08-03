package providers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.xml.sax.SAXException;

import ack.DcfAck;
import ack.DcfAckDetailedResId;
import ack.IDcfAckLog;
import ack.MessageValResCode;
import amend_manager.AmendException;
import amend_manager.ReportXmlBuilder;
import app_config.AppPaths;
import app_config.BooleanValue;
import app_config.PropertiesReader;
import config.Config;
import dataset.Dataset;
import dataset.DatasetList;
import dataset.DatasetMetaDataParser;
import dataset.IDataset;
import dataset.NoAttachmentException;
import dataset.RCLDatasetStatus;
import formula.FormulaException;
import global_utils.Message;
import global_utils.Warnings;
import i18n_messages.Messages;
import message.MessageConfigBuilder;
import message.MessageResponse;
import message.SendMessageException;
import message_creator.OperationType;
import progress_bar.ProgressListener;
import report.DisplayAckResult;
import report.EFSAReport;
import report.Report;
import report.ReportException;
import report.ReportSendOperation;
import soap.DetailedSOAPException;
import soap.GetFile;
import soap_interface.IGetAck;
import soap_interface.IGetDataset;
import soap_interface.IGetDatasetsList;
import soap_interface.ISendMessage;
import table_relations.Relation;
import table_skeleton.TableCell;
import table_skeleton.TableColumn;
import table_skeleton.TableRow;
import table_skeleton.TableRowList;
import table_skeleton.TableVersion;
import user.User;
import xlsx_reader.TableHeaders.XlsxHeader;
import xlsx_reader.TableSchemaList;

import net.sf.joost.trax.TransformerFactoryImpl;

/**
 * Create the class which get the ack from the dcf write it into a target output
 * file (in temp folder) display it using the dft browser
 * 
 * @author shahaal
 * @author avonva
 *
 */
public class ReportService implements IReportService {

	private static final Logger LOGGER = LogManager.getLogger(ReportService.class);

	private IGetAck getAck;
	private IGetDatasetsList<IDataset> getDatasetsList;
	protected ITableDaoService daoService;
	private ISendMessage sendMessage;
	private IGetDataset getDataset;

	protected IFormulaService formulaService;

	public ReportService(IGetAck getAck, IGetDatasetsList<IDataset> getDatasetsList, ISendMessage sendMessage,
			IGetDataset getDataset, ITableDaoService daoService, IFormulaService formulaService) {

		this.getAck = getAck;
		this.getDatasetsList = getDatasetsList;
		this.sendMessage = sendMessage;
		this.getDataset = getDataset;
		this.daoService = daoService;
		this.formulaService = formulaService;
	}

	public ITableDaoService getDaoService() {
		return daoService;
	}

	/**
	 * Get all the mandatory fields that are not filled
	 * 
	 * @return
	 * @throws FormulaException
	 */
	public Collection<TableColumn> getMandatoryFieldNotFilled(TableRow row) throws FormulaException {

		Collection<TableColumn> notFilled = new ArrayList<>();

		for (TableColumn column : row.getSchema()) {

			String mandatory = formulaService.solve(row, column, XlsxHeader.MANDATORY);

			if (BooleanValue.isTrue(mandatory)) {

				TableCell value = row.get(column.getId());

				if (value == null || value.isEmpty())
					notFilled.add(column);
			}
		}

		return notFilled;
	}

	/**
	 * Populate the dataset with the header and operation information (from DCF)
	 * 
	 * @return
	 * @throws XMLStreamException
	 * @throws DetailedSOAPException
	 * @throws IOException
	 * @throws NoAttachmentException
	 */
	public Dataset datasetFromFile(File file) throws XMLStreamException, IOException {

		Dataset dataset = null;

		try (DatasetMetaDataParser parser = new DatasetMetaDataParser(file);) {
			dataset = parser.parse();
			parser.close();
		}

		return dataset;
	}

	@Override
	public File download(String datasetId) throws DetailedSOAPException, NoAttachmentException {

		File file = getDataset.getDatasetFile(Config.getEnvironment(), User.getInstance(), datasetId);

		if (file == null)
			throw new NoAttachmentException("Cannot find the attachment of the dataset with id=" + datasetId);

		return file;
	}

	public File export(Report report, MessageConfigBuilder messageConfig)
			throws IOException, ParserConfigurationException, SAXException, ReportException, AmendException {
		return this.export(report, messageConfig, null);
	}

	public File export(Report report, MessageConfigBuilder messageConfig, ProgressListener progressListener)
			throws ParserConfigurationException, SAXException, IOException, ReportException, AmendException {

		if (messageConfig.needEmptyDataset()) {
			return ReportXmlBuilder.createEmptyReport(messageConfig);
		} else {

			Relation.emptyCache();

			// get the previous report version to process amendments
			try (ReportXmlBuilder creator = new ReportXmlBuilder(report, messageConfig, report.getRowIdFieldName(),
					daoService, formulaService);) {

				creator.setProgressListener(progressListener);

				return creator.exportReport();
			}
		}
	}

	/**
	 * @throws IOException Send the report contained in the file and update the
	 *                     report status accordingly. NOTE only for expert users.
	 *                     Otherwise use {@link #exportAndSend()} to send the report
	 *                     with an atomic operation.
	 * @param file
	 * @throws SOAPException @throws SendMessageException @throws
	 */
	private MessageResponse send(File file, OperationType opType) throws DetailedSOAPException, IOException {

		// send the report and get the response to the message
		MessageResponse response = sendMessage.send(Config.getEnvironment(), User.getInstance(), file);

		return response;
	}

	/**
	 * Update a report with a send message response
	 * 
	 * @param report
	 * @param requiredSendOp
	 * @param response
	 * @return
	 */
	private RCLDatasetStatus updateReportWithSendResponse(Report report, OperationType requiredSendOp,
			MessageResponse response) {

		RCLDatasetStatus newStatus;

		// save the message id
		report.setMessageId(response.getMessageId());
		report.setLastMessageId(response.getMessageId());

		if (requiredSendOp == OperationType.INSERT || requiredSendOp == OperationType.REPLACE)
			report.setLastModifyingMessageId(response.getMessageId());

		// if correct response then save the message id
		// into the report
		if (response.isCorrect()) {

			// update report status based on the request operation type
			switch (requiredSendOp) {
			case INSERT:
			case REPLACE:
				newStatus = RCLDatasetStatus.UPLOADED;
				break;
			case REJECT:
				newStatus = RCLDatasetStatus.REJECTION_SENT;
				break;
			case SUBMIT:
				newStatus = RCLDatasetStatus.SUBMISSION_SENT;
				break;
			default:
				newStatus = null;
				break;
			}
		} else {
			// set upload failed status if message is not valid
			newStatus = RCLDatasetStatus.UPLOAD_FAILED;
		}

		if (newStatus != null) {
			report.setStatus(newStatus);
			daoService.update(report);
		}

		return newStatus;
	}

	public void send(Report report, Dataset dcfDataset, MessageConfigBuilder messageConfig,
			ProgressListener progressListener) throws DetailedSOAPException, IOException, ParserConfigurationException,
			SAXException, SendMessageException, ReportException, AmendException {

		// Update the report dataset id if it was found in the DCF
		// (Required if we are overwriting an existing report)
		if (dcfDataset != null) {

			switch (dcfDataset.getRCLStatus()) {
			case PROCESSING: // cannot send with these status
			case ACCEPTED_DWH:
			case OTHER:
			case SUBMITTED:
				return;

			case VALID: // dataset id needs to be copied to replace
			case VALID_WITH_WARNING:
			case REJECTED:
			case REJECTED_EDITABLE:

				LOGGER.info("Overwriting dataset id: " + report.getId() + " with " + dcfDataset.getId());

				report.setId(dcfDataset.getId());
				daoService.update(report);
				break;

			default: // for deleted no action required
				break;
			}
		}

		// get the send operation
		ReportSendOperation op = getSendOperation(report, dcfDataset);
		messageConfig.setOpType(op.getOpType());

		this.exportAndSend(report, messageConfig, progressListener);
	}

	/**
	 * Export the report and send it to the DCF
	 * 
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws SendMessageException
	 * @throws ReportException
	 * @throws AmendException
	 * @throws SOAPException
	 */
	public MessageResponse exportAndSend(Report report, MessageConfigBuilder messageConfig,
			ProgressListener progressListener) throws IOException, ParserConfigurationException, SAXException,
			SendMessageException, DetailedSOAPException, ReportException, AmendException {

		// export the report and get an handle to the exported file
		File file;
		try {
			file = this.export(report, messageConfig, progressListener);
			
		} catch (AmendException e1) {
			LOGGER.error("Error during export", e1);
			// upload failed if errors in the amendments
			report.setStatus(RCLDatasetStatus.UPLOAD_FAILED);
			this.daoService.update(report);

			throw e1;
		}

		MessageResponse response;
		try {

			response = this.send(file, messageConfig.getOpType());

			// Update the report
			updateReportWithSendResponse(report, messageConfig.getOpType(), response);

			// if wrong response
			if (!response.isCorrect()) {
				throw new SendMessageException(response);
			}

			// delete file also if exception occurs
			file.delete();
		} catch (DetailedSOAPException e) {
			LOGGER.error("Error during delete of file ", e);
			// delete file also if exception occurs
			file.delete();

			// then rethrow the exception
			throw e;
		}

		if (progressListener != null)
			progressListener.progressCompleted();

		return response;
	}

	/**
	 * Export and send without tracking progresses
	 * 
	 * @param opType
	 * @throws DetailedSOAPException
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws SendMessageException
	 * @throws ReportException
	 * @throws AmendException
	 */
	public MessageResponse exportAndSend(Report report, MessageConfigBuilder messageConfig)
			throws DetailedSOAPException, IOException, ParserConfigurationException, SAXException, SendMessageException,
			ReportException, AmendException {
		return this.exportAndSend(report, messageConfig, null);
	}

	@Override
	public TableRowList getAllVersions(String senderId) {
		return daoService.getByStringField(TableSchemaList.getByName(AppPaths.REPORT_SHEET), AppPaths.REPORT_SENDER_ID,
				senderId);
	}

	/**
	 * Get the previous version of a report
	 * 
	 * @param report
	 * @return
	 */
	public TableRow getPreviousVersion(EFSAReport report) {

		String currentVersion = report.getVersion();

		// compute the previous version
		String previousVersion = TableVersion.getPreviousVersion(currentVersion);

		// no previous version
		if (previousVersion == null)
			return null;

		// get all the report versions
		TableRowList allVersions = getAllVersions(report.getSenderId());

		// search the previous
		TableRow prev = null;
		for (TableRow current : allVersions) {

			String version = current.getCode(AppPaths.REPORT_VERSION);

			if (version.equals(previousVersion)) {
				prev = current;
				break;
			}
		}

		return prev;
	}

	@Override
	public DcfAck getAckOf(String messageId) throws DetailedSOAPException {

		// if no message id => the report was never sent
		if (messageId.isEmpty()) {
			return null;
		}

		// get state
		DcfAck ack = getAck.getAck(Config.getEnvironment(), User.getInstance(), messageId);

		return ack;
	}

	/*
	 * shahaal: get the detailed res id of the ack
	 */
	@Override
	public DcfAckDetailedResId getAckDetailedResIdOf(String detailedResId) throws DetailedSOAPException {

		// if no message id => the report was never sent
		if (detailedResId.isEmpty())
			return null;

		// get state
		DcfAckDetailedResId ack = getAck.getAckDetailedResId(Config.getEnvironment(), User.getInstance(),
				detailedResId);

		return ack;
	}

	@Override
	public DatasetList getDatasetsOf(String senderDatasetId, String dcYear) throws DetailedSOAPException {

		DatasetList output = new DatasetList();

		getDatasetsList.getList(Config.getEnvironment(), User.getInstance(),
				PropertiesReader.getDataCollectionCode(dcYear), output);

		return output.filterBySenderId(senderDatasetId);
	}

	@Override
	public Dataset getDatasetById(String senderDatasetId, String dcYear, String datasetId)
			throws DetailedSOAPException {

		DatasetList datasets = getDatasetsOf(senderDatasetId, dcYear);
		datasets = datasets.filterByDatasetId(datasetId);

		IDataset mostRecent = datasets.getMostRecentDataset();

		if (mostRecent == null)
			return null;

		return (Dataset) mostRecent;
	}

	@Override
	public Dataset getDataset(String senderDatasetId, String dcYear) throws DetailedSOAPException {

		DatasetList datasets = getDatasetsOf(senderDatasetId, dcYear);
		IDataset mostRecent = datasets.getMostRecentDataset();

		if (mostRecent == null)
			return null;

		return (Dataset) mostRecent;
	}

	@Override
	public Dataset getDataset(EFSAReport report) throws DetailedSOAPException {

		// the report does not have sender id and version together, therefore
		// we need to merge them
		String senderDatasetId = TableVersion.mergeNameAndVersion(report.getSenderId(), report.getVersion());

		// use the dataset id if we have it
		if (report.getId() != null && !report.getId().isEmpty())
			return getDatasetById(senderDatasetId, report.getYear(), report.getId());
		else {
			return getDataset(senderDatasetId, report.getYear());
		}
	}

	@Override
	public boolean isLocallyPresent(String senderDatasetId) {
		for (TableRow row : daoService.getAll(TableSchemaList.getByName(AppPaths.REPORT_SHEET))) {

			String otherSenderId = row.getLabel(AppPaths.REPORT_SENDER_ID);

			// if same sender dataset id then return true
			if (otherSenderId != null && otherSenderId.equals(senderDatasetId))
				return true;
		}

		return false;
	}

	public RCLError create(Report report) throws DetailedSOAPException {

		// if the report is already present
		// show error message
		if (isLocallyPresent(report.getSenderId())) {
			return new RCLError("WARN304");
		}

		Dataset oldReport = getDataset(report);

		// if the report already exists
		// with the selected sender dataset id
		if (oldReport != null) {

			// check if there are errors
			RCLError error = getCreateReportError(report, oldReport);

			if (error != null)
				return error;

			// if no errors, then we are able to create the report

			switch (oldReport.getRCLStatus()) {
			case REJECTED:
				// we mantain the same dataset id
				// of the rejected dataset, but actually
				// we create a new report with that
				report.setId(oldReport.getId());
				break;

			default:
				break;
			}
		}

		// if no conflicts create the new report
		daoService.add(report);

		return null;
	}

	/**
	 * get the error message that needs to be displayed if an old report already
	 * exists
	 * 
	 * @param oldReport
	 * @return
	 */
	private RCLError getCreateReportError(IDataset report, IDataset oldReport) {

		String code = null;

		switch (oldReport.getRCLStatus()) {
		case ACCEPTED_DWH:
			code = "WARN301";
			break;
		case SUBMITTED:
			code = "WARN302";
			break;
		case VALID:
		case VALID_WITH_WARNING:
		case REJECTED_EDITABLE:
			code = "WARN303";
			break;
		case PROCESSING:
			code = "WARN300";
			break;
		case DELETED:
		case REJECTED:
			break;
		default:
			code = "ERR300";
			break;
		}

		if (code == null)
			return null;

		return new RCLError(code, oldReport);
	}

	/**
	 * Given a report and its state, get the operation that is correct for sending
	 * it to the dcf. For example, if the report was never sent then the operation
	 * will be {@link OperationType#INSERT}.
	 * 
	 * @param report
	 * @return
	 * @throws ReportException
	 * @throws DetailedSOAPException
	 */
	public ReportSendOperation getSendOperation(Report report, Dataset dcfDataset) throws DetailedSOAPException {

		OperationType opType = OperationType.NOT_SUPPORTED;

		// Dataset dataset = this.getLatestDataset(report);

		String senderId = TableVersion.mergeNameAndVersion(report.getSenderId(), report.getVersion());

		LOGGER.debug("Searching report with sender dataset id=" + senderId);

		// if no dataset is present => we do an insert
		if (dcfDataset == null || !dcfDataset.getSenderId().equals(senderId)) {
			LOGGER.debug("No valid dataset found in DCF, using INSERT as operation");
			return new ReportSendOperation(null, OperationType.INSERT);
		}

		// otherwise we check the dataset status
		RCLDatasetStatus status = dcfDataset.getRCLStatus();

		LOGGER.debug("Found dataset in DCF in status " + status);

		switch (status) {
		case REJECTED_EDITABLE:
		case VALID:
		case VALID_WITH_WARNING:
		case REJECTED:
			opType = OperationType.REPLACE;
			break;
		case DELETED:
			opType = OperationType.INSERT;
			break;
		default:
			opType = OperationType.NOT_SUPPORTED;
			// throw new ReportException("No send operation for status "
			// + status + " is supported");
		}

		ReportSendOperation operation = new ReportSendOperation(dcfDataset, opType);

		return operation;
	}

	/**
	 * Refresh the status of a report
	 * 
	 * @param shell
	 * @param report
	 * @param listener
	 */
	public Message refreshStatus(Report report) {

		DcfAck ack = null;
		try {
			ack = this.getAckOf(report.getMessageId());
		} catch (DetailedSOAPException e) {
			LOGGER.error("Cannot get the ack for the report=" + report.getSenderId(), e);
			e.printStackTrace();
			return Warnings.createSOAPWarning(e);
		}

		// if no ack return
		if (ack == null) {
			Message m = Warnings.create(Messages.get("error.title"), Messages.get("ack.not.available", PropertiesReader.getSupportEmail()), SWT.ICON_ERROR);
			m.setCode("ERR803");
			return m;
		}

		// failed ack
		if (ack.hasFault() || ack.isDenied()) {
			// mark status as failed
			RCLDatasetStatus failedStatus = RCLDatasetStatus.getFailedVersionOf(report.getRCLStatus());

			if (failedStatus != null) {
				report.setStatus(failedStatus);

				// permanently save data
				daoService.update(report);
			}
		}

		// If the ack has a fault error
		if (ack.hasFault()) {
			String message = Messages.get("ack.fault", PropertiesReader.getSupportEmail());

			int style = SWT.ICON_ERROR;
			Message m = Warnings.createFatal(message, style, report);
			m.setCode("ERR809");
			return m;
		}

		// If access denied
		if (ack.isDenied()) {
			String message = Messages.get("ack.denied", PropertiesReader.getSupportEmail());

			int style = SWT.ICON_ERROR;
			Message m = Warnings.createFatal(message, style, report);
			m.setCode("ERR810");
			return m;
		}

		// if processing
		if (!ack.isReady()) {
			// warn the user, the ack cannot be retrieved yet
			String title = Messages.get("warning.title");
			String message = Messages.get("ack.processing");
			int style = SWT.ICON_INFORMATION;
			Message m = Warnings.create(title, message, style);
			m.setCode("WARN500");
			return m;
		}

		IDcfAckLog log = ack.getLog();

		boolean discarded = ack.getLog().getMessageValResCode() == MessageValResCode.DISCARDED;

		// if discarded or KO
		if (discarded || !log.isOk()) {

			// mark status as failed
			RCLDatasetStatus failedStatus = RCLDatasetStatus.getFailedVersionOf(report.getRCLStatus());

			if (failedStatus != null) {
				report.setStatus(failedStatus);

				// permanently save data
				daoService.update(report);
			}

			if (discarded) {

				String title = Messages.get("error.title");
				String message = Messages.get("ack.discarded", ack.getLog().getMessageValResText());

				int style = SWT.ICON_ERROR;
				Message m = Warnings.create(title, message, style);
				m.setCode("ERR807");

				return m;
			} else if (!log.isOk()) {

				// errors
				if (log.hasErrors()) {

					LOGGER.warn("Error found in ack=" + log.getOpResError());
					LOGGER.warn("Error description found in ack=" + log.getOpResLog());

					return Warnings.getAckOperationWarning(report, log);
				} else {
					LOGGER.error("Wrong ack structure. The log is TRXKO but no errors found!");
					Message m = Warnings
							.createFatal(Messages.get("ack.ko.no.errors", PropertiesReader.getSupportEmail()), report);
					m.setCode("ERR806");
					return m;
				}
			}
		}

		// here log can only be OK
		Dataset dcfDataset;
		try {
			dcfDataset = this.getDataset(report);
		} catch (DetailedSOAPException e) {
			LOGGER.error("Cannot get the dataset of the report=" + report.getSenderId(), e);
			e.printStackTrace();
			return Warnings.createSOAPWarning(e);
		}

		// if no dataset return error
		if (dcfDataset == null) {
			String message = Messages.get("dataset.not.available");
			Message m = Warnings.create(Messages.get("error.title"), message, SWT.ICON_ERROR);
			m.setCode("ERR808");
			return m;
		}

		// if deleted in DCF, auto draft and remove message information
		if (dcfDataset.getRCLStatus() == RCLDatasetStatus.DELETED) {

			report.makeEditable();
			report.remove(AppPaths.REPORT_MESSAGE_ID);
			report.remove(AppPaths.REPORT_LAST_MESSAGE_ID);
			report.remove(AppPaths.REPORT_LAST_MODIFYING_MESSAGE_ID);
			report.remove(AppPaths.REPORT_LAST_VALIDATION_MESSAGE_ID);
			this.daoService.update(report);

			Message mb = Warnings.create(Messages.get("warning.title"), Messages.get("refresh.auto.draft",
					dcfDataset.getRCLStatus().getLabel(), RCLDatasetStatus.DRAFT.getLabel()), SWT.ICON_WARNING);
			mb.setCode("WARN502");

			return mb;
		}

		// if the data in DCF are actually consistent with the ones locally stored
		// update status with the one in DCF
		if (dcfDataset.getLastModifyingMessageId().equals(report.getLastModifyingMessageId())) {

			// update status with dataset status
			// update dataset id
			report.setStatus(dcfDataset.getRCLStatus());
			report.setId(dcfDataset.getId());
			this.daoService.update(report);

			// show different status message when refreshing the status
			Message mb = Warnings.create(Messages.get("success.title"),
					Messages.get("refresh.status.success", dcfDataset.getRCLStatus().getLabel()), SWT.ICON_INFORMATION);
			mb.setCode("OK501");

			return mb;
		}

		// otherwise, inconsistency

		Message mb;
		switch (dcfDataset.getStatus()) {
		case REJECTED:
		case REJECTED_EDITABLE:
		case VALID:
		case VALID_WITH_WARNING:
			// auto draft
			report.makeEditable();
			mb = Warnings
					.create(Messages.get("error.title"),
							Messages.get("refresh.inconsistent.auto.draft", dcfDataset.getLastModifyingMessageId(),
									dcfDataset.getRCLStatus().getLabel(), RCLDatasetStatus.DRAFT.getLabel()),
							SWT.ICON_ERROR);
			mb.setCode("ERR504");
			break;
		default:
			// inconsistency error
			mb = Warnings.createFatal(Messages.get("refresh.inconsistent.unmodifiable",
					dcfDataset.getLastModifyingMessageId(), dcfDataset.getRCLStatus().getLabel()), report, dcfDataset);
			mb.setCode("ERR505");
			break;
		}

		return mb;
	}

	/**
	 * Download the ack file related to the message id
	 * 
	 * @param messageId
	 * @return
	 * @throws DetailedSOAPException
	 */
	private DisplayAckResult downloadAckFile(String messageId) throws DetailedSOAPException, TransformerException {

		DcfAck ack = getAckOf(messageId);

		// get the detailed Ack Res Id
		// DcfAckDetailedResId ackDetailedResId = getAckDetailedResIdOf(messageId);

		// if no ack return
		if (ack == null || !ack.isReady() || ack.getLog() == null) {

			if (ack != null && !ack.isReady()) {

				// warn the user, the ack cannot be retrieved yet
				String title = Messages.get("warning.title");
				String message = Messages.get("ack.processing");
				Message m = Warnings.create(title, message, SWT.ICON_INFORMATION);
				m.setCode("WARN500");

				return new DisplayAckResult(messageId, m);
			}

			String message = Messages.get("ack.not.available");
			Message m = Warnings.create(Messages.get("error.title"), message, SWT.ICON_ERROR);
			m.setCode("ERR803");
			return new DisplayAckResult(messageId, m);
		}

		// get the file using the detailed ack res id
		File fileLog = null;
		try {
			fileLog = new GetFile().getFile(Config.getEnvironment(), User.getInstance(),
					ack.getLog().getDetailedAckResId());
		} catch (SOAPException | IOException e1) {
			LOGGER.error("Error in accessing file: ", e1);
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// write it into a file in the temporary folder
		// in order to be able to open it in the browser
		String filename = AppPaths.TEMP_FOLDER + "ack_" + System.currentTimeMillis() + ".xml";
		File targetFile = new File(filename);

		// get the stx file which will process the xml one
		File stxFile = new File(AppPaths.TSE_ERROR_DETAILS);

		// process the xml file and write it in output
		processXmlInStx(fileLog, stxFile, targetFile);

		// correct execution
		return new DisplayAckResult(messageId, targetFile);
	}

	/**
	 * Display an ack in the browser
	 * 
	 * @param shell
	 * @param report
	 */
	public DisplayAckResult displayAck(EFSAReport report) {

		String localMessageId = report.getMessageId();
		String datasetId = report.getId();

		// if no dataset id
		if (datasetId == null || datasetId.isEmpty()) {

			// if no message id found
			if (localMessageId == null || localMessageId.isEmpty()) {

				Message m = Warnings.create(Messages.get("error.title"), Messages.get("ack.no.message.id"),
						SWT.ICON_ERROR);
				m.setCode("ERR800");

				return new DisplayAckResult(m);
			}

			// use local message id to display ack
			try {
				return downloadAckFile(localMessageId);
			} catch (DetailedSOAPException e) {
				LOGGER.error("Cannot get ack for messageId=" + localMessageId, e);
				e.printStackTrace();
				return new DisplayAckResult(Warnings.createSOAPWarning(e));
			} catch (TransformerException e) {
				LOGGER.error("Cannot get ack for messageId=" + localMessageId, e);
				e.printStackTrace();
			}
		}

		// if dataset id is present

		// do get dataset list
		Dataset dataset = null;
		try {
			dataset = this.getDataset(report);
		} catch (DetailedSOAPException e) {
			LOGGER.error("Cannot get dataset in GetDatasetList for datasetId=" + datasetId, e);
			e.printStackTrace();
			return new DisplayAckResult(Warnings.createSOAPWarning(e));
		}

		// if no dataset return error
		if (dataset == null) {
			String message = Messages.get("dataset.not.available");
			Message m = Warnings.create(Messages.get("error.title"), message, SWT.ICON_ERROR);
			m.setCode("ERR808");
			return new DisplayAckResult(m);
		}

		Message warning = null;
		String targetMessageId = null;

		int dcfLastModifying = Integer.valueOf(dataset.getLastModifyingMessageId());
		int localLastModifying = Integer.valueOf(report.getLastModifyingMessageId());
		int localMessageIdInteger = Integer.valueOf(localMessageId);

		// if dcf has a greater modifying message id => data inconsistency
		if (dcfLastModifying > localLastModifying) {
			targetMessageId = localMessageId;

			warning = Warnings.createFatal(
					Messages.get("ack.modification.outdated", dataset.getLastModifyingMessageId()), SWT.ICON_WARNING,
					report, dataset);
			warning.setCode("WARN800");
		} else {

			if (localLastModifying == localMessageIdInteger) {

				// use last validation message id in this case
				targetMessageId = dataset.getLastValidationMessageId();

				// if greater, warning!
				int dcfLastValidation = Integer.valueOf(dataset.getLastValidationMessageId());

				if (dcfLastValidation > localLastModifying) {

					warning = Warnings.create(Messages.get("warning.title"), Messages.get("ack.validation.outdated"),
							SWT.ICON_WARNING);
					warning.setCode("WARN801");
				}
			} else {
				targetMessageId = localMessageId; // use the local if not equal
			}
		}

		// if no target id found
		if (targetMessageId == null || targetMessageId.isEmpty()) {
			Message m = Warnings.create(Messages.get("error.title"), Messages.get("ack.no.message.id"), SWT.ICON_ERROR);
			m.setCode("ERR800");
			return new DisplayAckResult(m);
		}

		// get the ack
		DisplayAckResult result = null;
		try {
			result = downloadAckFile(targetMessageId);
		} catch (DetailedSOAPException e) {
			LOGGER.error("Cannot get ack for messageId=" + targetMessageId, e);
			e.printStackTrace();
			return new DisplayAckResult(Warnings.createSOAPWarning(e));
		} catch (TransformerException e) {
			LOGGER.error("Cannot get ack for messageId=" + targetMessageId, e);
			e.printStackTrace();
		}

		// add warning if present
		if (warning != null) {
			String ackMsgId = result.getDcfMessageId();
			File file = result.getDownloadedAck();

			// respect the time order of messages in the list
			List<Message> ackMessages = result.getMessages();
			List<Message> messages = new ArrayList<>();
			messages.add(warning);
			messages.addAll(ackMessages);

			result = new DisplayAckResult(ackMsgId, messages, file);
		}

		return result;
	}

	/**
	 * shahaal get the xml file and pre process it using stx and xlst
	 *
	 * NB: Transforms it into html on the fly.
	 *
	 * @param file Where to put it.
	 * @param xml  - What to put in it.
	 * @throws TransformerException
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	protected void processXmlInStx(File inputXml, File stxFile, File targetOutput) {

		// get the xml and stx source
		Source xmlSource = new StreamSource(inputXml);
		Source stxSource = new StreamSource(stxFile);
		try {
			// Initialise the transform class of the joost lib
			TransformerFactory transFact = new TransformerFactoryImpl();
			Transformer trans = transFact.newTransformer(stxSource);

			// Transform it straight into the output file in temp
			FileOutputStream stream = new FileOutputStream(targetOutput);
			StreamResult streamRes = new StreamResult(stream);
			trans.transform(xmlSource, streamRes);
		} catch (IOException | TransformerException e) {
			// TODO Auto-generated catch block
			LOGGER.error("Error during transform: ", e);
			e.printStackTrace();
		}

	}

}
