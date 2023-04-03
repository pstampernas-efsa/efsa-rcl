package report;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.xml.sax.SAXException;

import dataset.Dataset;
import i18n_messages.Messages;
import message.MessageConfigBuilder;
import message.SendMessageException;
import message_creator.OperationType;
import progress_bar.FormProgressBar;
import progress_bar.ProgressListener;
import providers.IReportService;
import soap.DetailedSOAPException;

/**
 * Bridge between the user interface and the programmatic part. It follows the
 * documentation that can be found in the ToolTSE.vsd file. For downloading
 * reports please use instead {@link ReportDownloaderDialog}. For ack use
 * {@link ReportAckManager}.
 * 
 * @author avonva
 * @author shahaal
 *
 */
public abstract class ReportActions {

	private static final Logger LOGGER = LogManager.getLogger(ReportActions.class);

	public enum ReportAction {
		SEND, AMEND, REJECT, SUBMIT
		// ACCEPTED_DWH_BETA
	}

	private Shell shell;
	private Report report;
	private IReportService reportService;

	public ReportActions(Shell shell, Report report, IReportService reportService) {
		this.shell = shell;
		this.report = report;
		this.reportService = reportService;
	}

	public IReportService getReportService() {
		return reportService;
	}

	public Report getReport() {
		return report;
	}

	/**
	 * Perform a report action that involves the dcf
	 * 
	 * @param action
	 * @param listener
	 */
	public void perform(MessageConfigBuilder messageConfig, Listener listener) {

		Exception exceptionOccurred = null;

		ReportAction action;

		if (messageConfig.getOpType() == OperationType.SUBMIT)
			action = ReportAction.SUBMIT;
		/*
		 * shahaal, uncomment for beta testers else
		 * if(messageConfig.getOpType()==OperationType.ACCEPTED_DWH_BETA) action =
		 * ReportAction.ACCEPTED_DWH_BETA;
		 */
		else if (messageConfig.getOpType() == OperationType.REJECT)
			action = ReportAction.REJECT;
		else
			return;

		boolean confirm = askConfirmation(action);

		if (!confirm)
			return;

		try {

			shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));

			reportService.exportAndSend(report, messageConfig);

			listener.handleEvent(null);

		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.error("Cannot perform operation=" + action + " for report=" + report.getSenderId(), e);
			exceptionOccurred = e;
		} finally {
			shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
		}

		// if an exception occurred call manage exception
		if (exceptionOccurred == null) {
			end(action);
		} else {
			manageException(exceptionOccurred, action);
		}
	}

	/**
	 * Export the report and send it to the dcf.
	 * 
	 * @param report
	 * @throws ReportException
	 * @throws DetailedSOAPException
	 * @throws IOException
	 * @throws SOAPException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws SendMessageException
	 */
	public void send(MessageConfigBuilder messageConfig, Listener listener) {

		shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));

		Dataset dataset;
		try {
			dataset = reportService.getDataset(report);
		} catch (DetailedSOAPException e) {
			LOGGER.error("Cannot send report=" + report.getSenderId(), e);
			e.printStackTrace();
			shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
			manageException(e, ReportAction.SEND);
			return;
		}

		shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));

		if (dataset != null) {
			try {
				boolean goOn = showSendWarning(shell, dataset);

				if (!goOn) {
					return;
				}

			} catch (NotOverwritableDcfDatasetException e) {
				LOGGER.error("Cannot send report=" + report.getSenderId(), e);
				e.printStackTrace();
				manageException(e, ReportAction.SEND);
				return;
			}
		}

		FormProgressBar progressBarDialog = new FormProgressBar(shell, Messages.get("send.progress.title"));
		progressBarDialog.open();

		// start the sender thread
		ReportExportAndSendThread sender = new ReportExportAndSendThread(report, dataset, messageConfig, reportService);

		sender.setProgressListener(new ProgressListener() {

			@Override
			public void progressCompleted() {

				shell.getDisplay().syncExec(new Runnable() {

					@Override
					public void run() {

						progressBarDialog.fillToMax();

						progressBarDialog.close();

						if (listener != null)
							listener.handleEvent(null);

						end(ReportAction.SEND);
					}
				});
			}

			@Override
			public void progressChanged(double progressPercentage) {
				progressBarDialog.addProgress(progressPercentage);
			}

			@Override
			public void progressChanged(double currentProgress, double maxProgress) {
			}

			@Override
			public void progressStopped(Exception e) {
				shell.getDisplay().syncExec(new Runnable() {

					@Override
					public void run() {

						progressBarDialog.close();

						manageException(e, ReportAction.SEND);
					}
				});
			}
		});

		sender.start();
	}

	/**
	 * Warning based on the required operation and on the status of the dataset
	 * 
	 * @param shell
	 * @param operation
	 * @return
	 * @throws NotOverwritableDcfDatasetException
	 */
	public boolean showSendWarning(Shell shell, Dataset dcfDataset) throws NotOverwritableDcfDatasetException {

		boolean goOn = true;

		if (dcfDataset == null)
			return true;

		switch (dcfDataset.getRCLStatus()) {
		case ACCEPTED_DWH:
		case SUBMITTED:
		case PROCESSING:
			throw new NotOverwritableDcfDatasetException(dcfDataset);

		case REJECTED_EDITABLE:
		case VALID:
		case VALID_WITH_WARNING:
			// replace
			goOn = askReplaceConfirmation(dcfDataset);
			break;
		case REJECTED:
		case DELETED:
			// Do nothing, just avoid the default case
			goOn = true;

			break;
		default:
			throw new NotOverwritableDcfDatasetException(dcfDataset);
		}

		// default answer is no
		return goOn;
	}

	/**
	 * Ask to the user confirmation for a generic action.
	 * 
	 * @param action
	 * @return
	 */
	public abstract boolean askConfirmation(ReportAction action);

	/**
	 * Ask confirmation for replacing an existing dataset with a send operation
	 * 
	 * @param sendOp
	 * @return
	 */
	public abstract boolean askReplaceConfirmation(Dataset status);

	/**
	 * Called if an exception occurred
	 * 
	 * @param e
	 * @param action
	 */
	public abstract void manageException(Exception e, ReportAction action);

	/**
	 * Called if a process is finished
	 * 
	 * @param opType
	 */
	public abstract void end(ReportAction action);
}
