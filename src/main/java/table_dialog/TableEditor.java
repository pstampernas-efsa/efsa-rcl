package table_dialog;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ComboBoxViewerCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ICellEditorListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;

import table_skeleton.TableCell;
import table_skeleton.TableColumn;
import table_skeleton.TableRow;
import xml_catalog_reader.Selection;
import xml_catalog_reader.SelectionList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TableEditor extends EditingSupport {
	
	private static final Logger LOGGER = LogManager.getLogger(TableEditor.class);
	
	private Collection<EditorListener> listeners;
	private TableColumn column;
	private TableView viewer;

	public TableEditor(TableView viewer, TableColumn column) {
		super(viewer.getViewer());
		this.column = column;
		this.viewer = viewer;
		this.listeners = new ArrayList<>();
	}
	
	/**
	 * Listener called when edit starts/ends
	 * @param listener
	 */
	public void addListener(EditorListener listener) {
		this.listeners.add(listener);
	}

	@Override
	protected boolean canEdit(Object arg0) {
		TableRow row = (TableRow) arg0;
		return column.isEditable(row);
	}
	
	/**
	 * Create an empty selection object
	 * @return
	 */
	private Selection getEmptySelection() {
		Selection selection = new Selection();
		selection.setCode("");
		selection.setDescription("");
		return selection;
	}
	
	private CellEditor getTextEditor(TableRow row, boolean pwd) {

		int style = SWT.NONE;
		if (pwd) {
			style = SWT.PASSWORD;
		}
		
		TextCellEditor editor = new TextCellEditor(viewer.getTable(), style);
		editor.addListener(new ICellEditorListener() {
			
			@Override
			public void editorValueChanged(boolean arg0, boolean arg1) {
				//setRowValue(row, editor.getValue());
			}
			
			@Override
			public void cancelEditor() {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void applyEditorValue() {
				// TODO Auto-generated method stub
				
			}
		});
		
		return editor;
	}

	@Override
	protected CellEditor getCellEditor(Object arg0) {
		
		TableRow row = (TableRow) arg0;
		CellEditor editor = null;
		
		switch(column.getType()) {
		
		case PICKLIST:
			
			ComboBoxViewerCellEditor combo = new ComboBoxViewerCellEditor(viewer.getTable());
			combo.setActivationStyle(ComboBoxViewerCellEditor.DROP_DOWN_ON_MOUSE_ACTIVATION);
			
			combo.getViewer().addSelectionChangedListener(new ISelectionChangedListener() {
				
				@Override
				public void selectionChanged(SelectionChangedEvent arg0) {

					IStructuredSelection sel = ((IStructuredSelection) arg0.getSelection());
					
					if (sel.isEmpty())
						return;
					
					Selection selection = (Selection) sel.getFirstElement();

					if (selection != null)
						setValue(row, selection);
				}
			});
			
			// get the list of possible values for the current column
			// filtering by the summarized information type (bse..)
			SelectionList list = column.getList(row);
			
			// empty list if null is found
			if (list == null) {
				list = new SelectionList();
			}
			
			// add also an empty value
			Selection emptySel = getEmptySelection();
			if (!list.contains(emptySel))
				list.add(emptySel);
			
			combo.setContentProvider(new ComboBoxContentProvider());
			combo.setLabelProvider(new ComboBoxLabelProvider());
			
			if (list != null)
				combo.setInput(list);
			
			editor = combo;
			break;
			
		case PASSWORD:
			editor = getTextEditor(row, true);
			
			break;
		default:
			editor = getTextEditor(row, false);
			//editor = new TextCellEditor(viewer.getTable());
			break;
		}
		
		for(EditorListener listener : listeners)
			listener.editStarted();
		
		return editor;
	}

	@Override
	protected Object getValue(Object arg0) {
		
		TableRow row = (TableRow) arg0;
		Object value = null;
		
		switch(column.getType()) {
		
		case PICKLIST:
			value = row.get(column.getId());
			break;
			
		default:

			TableCell selection = row.get(column.getId());

			if (selection != null)
				value = selection.getLabel();

			if (value == null)
				value = "";
			break;
		}

		return value;
	}

	private void setRowValue(Object arg0, Object value) {

		TableRow row = (TableRow) arg0;

		switch(column.getType()) {

		// signed integer
		case INTEGER:

			String newValue = (String) value;

			// if change should be done, change
			if (isNumeric(newValue)) {
				row.put(column.getId(), Integer.valueOf(newValue).toString());
			}

			break;

			// unsigned integer
		case U_INTEGER:

			String unsignedInt = (String) value;

			// if change should be done, change
			if (isUnsignedNumeric(unsignedInt)) {
				row.put(column.getId(), Integer.valueOf(unsignedInt).toString());
			}

			break;

		case PICKLIST:
			Selection sel = (Selection) value;
			TableCell newSelection = new TableCell(sel);
			
			//shahaal: reset the cwdExtContext column if country is trigger
			//shahaal: should be more generic!
			if(column.getId().contains("country")) {
				//get the cwdExtContext column at index 1 (starting from 0)
				String cwdContexCol = row.getSchema().get(1).getId();
				//put in the row in the cwdExtContext the empty cell
				row.put(cwdContexCol, new TableCell());
			}
			
			row.put(column.getId(), newSelection);
			break;

		case STRING:
		case PASSWORD:
			String newValue2 = (String) value;
			row.put(column.getId(), newValue2);
			break;
		default:
			break;
		}
	}
	
	@Override
	protected void setValue(Object arg0, Object value) {
		
		TableRow row = (TableRow) arg0;
		
		// avoid refreshing if same value
		Object oldValue = getValue(arg0);
		
		if (value == null || (oldValue != null && value.equals(oldValue))) {
			
			// edit is ended
			for(EditorListener listener : listeners)
				listener.editEnded(null, column, false);

			return;
		}
		
		setRowValue(arg0, value);
		
		// edit is ended
		for(EditorListener listener : listeners)
			listener.editEnded(row, column, true);
	}
	
	/**
	 * Check if numeric input
	 * @param newValue
	 * @return
	 */
	private boolean isNumeric (String newValue) {

		try {
			Integer.parseInt(newValue);
			return true;
		}
		catch (NumberFormatException e) {
			LOGGER.error("Error in checking if it is a numeric input: ", e);
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Check if numeric input
	 * @param newValue
	 * @return
	 */
	private boolean isUnsignedNumeric (String newValue) {

		try {
			int value = Integer.parseInt(newValue);
			return value >= 0;
		}
		catch (NumberFormatException e) {
			LOGGER.error("Error in checking if it is a numeric input: ", e);
			e.printStackTrace();
			return false;
		}
	}
	
	private class ComboBoxContentProvider implements IStructuredContentProvider {

		@Override
		public void dispose() {}

		@Override
		public void inputChanged(Viewer arg0, Object arg1, Object arg2) {}

		@Override
		public Object[] getElements(Object arg0) {
			SelectionList list = (SelectionList) arg0;
			return list.getSelections().toArray();
		}
	}
	
	private class ComboBoxLabelProvider implements ILabelProvider {

		@Override
		public void addListener(ILabelProviderListener arg0) {}

		@Override
		public void dispose() {}

		@Override
		public boolean isLabelProperty(Object arg0, String arg1) {
			return false;
		}

		@Override
		public void removeListener(ILabelProviderListener arg0) {}

		@Override
		public Image getImage(Object arg0) {
			return null;
		}

		@Override
		public String getText(Object arg0) {
			
			Selection selection = (Selection) arg0;
			return selection.getDescription();
		}
	}
}
