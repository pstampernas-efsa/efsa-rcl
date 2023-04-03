package report;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

import amend_manager.ReportImporter;
import app_config.PropertiesReader;
import config.Config;
import data_collection.DcfDataCollectionsList;
import data_collection.GetAvailableDataCollections;
import data_collection.IDataCollectionsDialog;
import data_collection.IDcfDataCollection;
import data_collection.IDcfDataCollectionsList;
import dataset.Dataset;
import dataset.DatasetList;
import global_utils.Warnings;
import i18n_messages.Messages;
import progress_bar.FormProgressBar;
import progress_bar.IndeterminateProgressDialog;
import progress_bar.ProgressListener;
import providers.IReportService;
import soap.DetailedSOAPException;
import soap.GetDataCollectionsList;
import user.User;

/**
 * Download a report into the database
 * @author avonva
 * @author shahaal
 *
 */
public abstract class ReportDownloaderDialog {
	
	static final Logger LOGGER = LogManager.getLogger(ReportDownloaderDialog.class);

	private Shell shell;
	private DatasetList allVersions;
	private IReportService reportService;
	
	public ReportDownloaderDialog(Shell shell, IReportService reportService) {
		this.shell = shell;
		this.reportService = reportService;
	}

	public IReportService getReportService() {
		return reportService;
	}
	
	/**
	 * Get only the available data collections for which the user is registered
	 * @return
	 * @throws DetailedSOAPException
	 */
	private IDcfDataCollectionsList<IDcfDataCollection> getAvailableDcList() throws DetailedSOAPException {
		
		IDcfDataCollectionsList<IDcfDataCollection> output = new DcfDataCollectionsList();
		GetDataCollectionsList<IDcfDataCollection> req = new GetDataCollectionsList<>();
		
		req.getList(Config.getEnvironment(), User.getInstance(), output);
		
		Collection<String> validDcs = GetAvailableDataCollections.getCodes();
		
		IDcfDataCollectionsList<IDcfDataCollection> filteredOutput = new DcfDataCollectionsList();
		
		for(IDcfDataCollection dc : output) {
			// remove not valid data collection
			if (validDcs.contains(dc.getCode())) {
				filteredOutput.add(dc);
			}
		}

		return filteredOutput;
	}
	
	/**
	 * Download a dataset from the dcf
	 * @param validSenderId
	 * @throws DetailedSOAPException 
	 */
	public void download() throws DetailedSOAPException {

		shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
		
		// select the data collection
		IDcfDataCollectionsList<IDcfDataCollection> list;
		try {
			list = getAvailableDcList();
		} catch(DetailedSOAPException e) {
			shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
			throw e;
		}
		
		shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));

		// if no data collection was retrieved
		if (list.isEmpty()) {
			
			Warnings.createFatal(Messages.get("dc.no.element.found", 
					PropertiesReader.getSupportEmail())).open(shell);
			
			return;
		}
		
		IDataCollectionsDialog dcDialog = getDataCollectionsDialog(shell, list);
		
		IDcfDataCollection selectedDc = dcDialog.open();
		
		if (selectedDc == null)
			return;
		
		// get datasets list
		getDatasets(selectedDc.getCode(), new ThreadFinishedListener() {

			public void finished(Runnable runnable) {
				
				GetDatasetListThread getDatasetListThread = (GetDatasetListThread) runnable;
				
				DatasetList list = getDatasetListThread.getDatasetsList();
				
				// open the list of datasets
				IDownloadReportDialog dialog = getDownloadDialog(selectedDc);
				dialog.setDatasetsList(list);
				dialog.open();
				
				// get the chosen dataset
				Dataset selectedDataset = dialog.getSelectedDataset();
				
				if (selectedDataset == null)  // user pressed cancel
					return;

				// get all the versions of the dataset that are present in the DCF
				allVersions = dialog.getSelectedDatasetVersions();
				
				if (allVersions == null)
					return;
				
				// extract the senderId from the composed field (senderId.version)
				String senderId = selectedDataset.getDecomposedSenderId();
				
				// if the report already exists locally, warn that it will be overwritten
				if (reportService.isLocallyPresent(senderId)) {
					
					// ask confirmation to the user
					boolean confirm = askConfirmation();
					
					if (!confirm)
						return;
				}

				// download and import the dataset
				ReportImporter downloader = getImporter(allVersions);
				ReportImporterThread thread = new ReportImporterThread(downloader);
				
				FormProgressBar progressBarDialog = new FormProgressBar(shell, 
						Messages.get("download.progress.title"));
				progressBarDialog.open();
				
				thread.setProgressListener(new ProgressListener() {

					@Override
					public void progressCompleted() {
						
						// show warning
						shell.getDisplay().syncExec(new Runnable() {

							@Override
							public void run() {
								
								progressBarDialog.fillToMax();
								progressBarDialog.close();
								
								end();
							}
						});
					}

					@Override
					public void progressChanged(double progressPercentage) {
						progressBarDialog.setProgress(progressPercentage);
					}

					@Override
					public void progressChanged(double currentProgress, double maxProgress) {}

					@Override
					public void progressStopped(Exception e) {

						// show warning
						shell.getDisplay().syncExec(new Runnable() {

							@Override
							public void run() {
								
								progressBarDialog.close();
								
								// manage the exception
								manageException(e);
							}
						});
					}
				});
				
				thread.start();
			}

			@Override
			public void terminated(Runnable thread, Exception e) {
				
				shell.getDisplay().asyncExec(new Runnable() {
					
					@Override
					public void run() {
						if (e instanceof DetailedSOAPException) {
							Warnings.showSOAPWarning(shell, (DetailedSOAPException) e);
						}
						else {
							Warnings.warnUser(shell, Messages.get("error.title"), Messages.get("download.dataset.error"));
						}
					}
				});
			}
		});
	}
	
	/**
	 * Get dataset list in background with progress bar
	 * @param dcCode
	 * @param listener
	 */
	private void getDatasets(String dcCode, ThreadFinishedListener listener) {
		
		IndeterminateProgressDialog progressBar = new IndeterminateProgressDialog(shell, SWT.APPLICATION_MODAL, 
				Messages.get("get.dataset.list.progress.bar.label"));
		progressBar.open();
		
		GetDatasetListThread thread = new GetDatasetListThread(User.getInstance(), dcCode);
		thread.setListener(new ThreadFinishedListener() {
			
			@Override
			public void finished(Runnable thread) {
				shell.getDisplay().asyncExec(new Runnable() {
					
					@Override
					public void run() {
						progressBar.close();
						listener.finished(thread);
					}
				});
			}

			@Override
			public void terminated(Runnable thread, Exception e) {
				shell.getDisplay().asyncExec(new Runnable() {
					
					@Override
					public void run() {
						progressBar.close();
						listener.terminated(thread, e);
					}
				});
			}
		});
		thread.start();
	}
	
	public DatasetList getAllVersions() {
		return allVersions;
	}
	
	/**
	 * Get the class encharged of importing the report into the database.
	 * The importer must be an extension of
	 * the {@link ReportImporter} class (it already processes
	 * amendments using all the report versions)
	 * @param allVersions
	 */
	public abstract ReportImporter getImporter(DatasetList allVersions);
	
	/**
	 * Get a dialog for picking a data collection
	 * @return
	 */
	public abstract IDataCollectionsDialog getDataCollectionsDialog(Shell shell, 
			IDcfDataCollectionsList<IDcfDataCollection> list);
	
	/**
	 * Get the dialog which should be shown to select the dataset to download.
	 * Here it is possible to customize the columns that should be shown.
	 * @return
	 */
	public abstract IDownloadReportDialog getDownloadDialog(IDcfDataCollection dataCollection);
	
	
	public abstract boolean askConfirmation();
	
	/**
	 * Manage an exception that was thrown during the download process
	 * @param e
	 */
	public abstract void manageException(Exception e);
	
	/**
	 * Called at the end of the process
	 */
	public abstract void end();
}
