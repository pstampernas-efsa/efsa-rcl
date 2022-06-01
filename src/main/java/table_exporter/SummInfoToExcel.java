package table_exporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;

import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFTableStyleInfo;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

import global_utils.Warnings;
import table_skeleton.TableColumn;
import table_skeleton.TableRow;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that allow to export the summarised information table to xlsx.
 *
 * @author shahaal
 */

public class SummInfoToExcel {
	
	private static final Logger LOGGER = LogManager.getLogger(SummInfoToExcel.class);

	public XSSFWorkbook createWorkbookFromTable(Collection<TableColumn> headers, Collection<TableRow> records) {

		// create a workbook
		XSSFWorkbook wb = new XSSFWorkbook();

		// add a worksheet
		XSSFSheet sheet = wb.createSheet("Summarized Information");

		int noRecords = records.size();
		int noColumns = headers.size() - 1;

		// Set which area the table should be placed in
		AreaReference reference = wb.getCreationHelper().createAreaReference(new CellReference(0, 0),
				new CellReference(noRecords, noColumns));

		// create the table
		XSSFTable table = sheet.createTable(reference);

		// For now, create the initial style in a low-level way
		table.getCTTable().addNewTableStyleInfo();

		// Style the table
		XSSFTableStyleInfo style = (XSSFTableStyleInfo) table.getStyle();
		style.setName("TableStyleMedium2");
		style.setShowRowStripes(true);

		int rowIndex = 0;
		int cellIndex = 0;

		// add column headers
		XSSFRow header = sheet.createRow((short) rowIndex++);
		for (TableColumn column : headers) {
			XSSFCell cell = header.createCell(cellIndex++);
			cell.setCellValue(column.getLabel());
			
			// set the horizontal alignment
			XSSFCellStyle cellStyle = wb.createCellStyle();
			cellStyle.setAlignment(HorizontalAlignment.CENTER);
			cell.setCellStyle(cellStyle);
		}

		// add data rows
		for (TableRow item : records) {
			// create a new row
			XSSFRow row = sheet.createRow((short) rowIndex++);
			cellIndex = 0;

			for (TableColumn col : headers) {
				// create a new cell
				XSSFCell cell = row.createCell(cellIndex++);

				// set the horizontal alignment
				XSSFCellStyle cellStyle = wb.createCellStyle();
				cellStyle.setAlignment(HorizontalAlignment.LEFT);
				cell.setCellStyle(cellStyle);

				String cellContent = item.getLabel(col.getId());
				String emptyContent = (col.isEditable(item) && cellContent == "") ? "/" : "Not applicable";
				// set the cell's value
				String text = (cellContent != "") ? cellContent : emptyContent;
				cell.setCellValue(text);
			}
		}

		// auto-fit the columns
		for (int i = 0; i < headers.size(); i++) {
			sheet.autoSizeColumn((short) i);
		}

		return wb;
	}

	public void dumpWorkbookToAFile(Shell shell, XSSFWorkbook wb, File file) {
		
		try {
			FileOutputStream fos = new FileOutputStream(file);
			wb.write(fos);
			fos.close();
			Warnings.warnUser(shell, "Success", "Workbook saved successfully.", SWT.ICON_INFORMATION);
		} catch (IOException ioe) {
			LOGGER.error("Error in saving workbook: " , ioe);
			ioe.printStackTrace();
			String msg = ioe.getMessage();
			Warnings.warnUser(shell, "Error", "Save Workbook Failed!\n"+msg);
		}
	}

	/*
	 * public static void main(String[] args) {
	 * 
	 * // input excel file File excelFile = new File("D:/PortableApps/test.xlsx");
	 * 
	 * ExcelXmlConverter converter = new ExcelXmlConverter();
	 * 
	 * try {
	 * 
	 * if(converter.convertXExcelToXml(excelFile)!=null)
	 * System.out.println("Excel convertet with success."); else
	 * System.out.println("Error during Excel conversion. Try again!");
	 * 
	 * } catch (ParserConfigurationException | IOException | TransformerException e)
	 * { // TODO Auto-generated catch block e.printStackTrace(); }
	 * 
	 * }
	 */
}
