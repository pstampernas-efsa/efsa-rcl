package test_case;

/*******************************************************************************
 * All Right Reserved. Copyright (c) 1998, 2004 Jackwind Li Guojie
 * 
 * Created on Mar 18, 2004 1:01:54 AM by JACK $Id$
 *  
 ******************************************************************************/



import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NumberInputDialog extends Dialog {
	
	private static final Logger LOGGER = LogManager.getLogger(NumberInputDialog.class);
	
	private Integer value;
	private String defVal;
	private boolean cancelled = false;
	private Button buttonOK;

	/**
	 * @param parent
	 */
	public NumberInputDialog(Shell parent) {
		super(parent);
	}

	/**
	 * @param parent
	 * @param style
	 */
	public NumberInputDialog(Shell parent, int style) {
		super(parent, style);
	}
	
	public void setDefaultValue(String defVal) {
		this.defVal = defVal;
	}

	public boolean wasCancelled() {
		return this.cancelled;
	}
	
	private void changeValue(String text) {

		try {
			value = new Integer(text);
			buttonOK.setEnabled(true);
		} catch (Exception e) {
			LOGGER.error("Error in changing value: ", e);
			buttonOK.setEnabled(false);
		}
		
		if (text.isEmpty())
			buttonOK.setEnabled(true);
	}
	
	/**
	 * Makes the dialog visible.
	 * 
	 * @return
	 */
	public Integer open() {
		
		Shell parent = getParent();
		final Shell shell =
				new Shell(parent, SWT.TITLE | SWT.BORDER | SWT.APPLICATION_MODAL);
		shell.setText("Number picker");

		shell.setLayout(new GridLayout(2, true));

		Label label = new Label(shell, SWT.NULL);
		label.setText("Please enter a valid number:");

		final Text text = new Text(shell, SWT.SINGLE | SWT.BORDER);

		if (this.defVal != null) {
			text.setText(defVal);
		}
		
		GridData data = new GridData();
		data.widthHint = 100;
		text.setLayoutData(data);

		buttonOK = new Button(shell, SWT.PUSH);
		buttonOK.setText("OK");
		buttonOK.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
		Button buttonCancel = new Button(shell, SWT.PUSH);
		buttonCancel.setText("Cancel");

		text.addListener(SWT.Modify, new Listener() {
			public void handleEvent(Event event) {
				changeValue(text.getText());
			}
		});

		buttonOK.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				
				changeValue(text.getText());
				
				shell.dispose();
			}
		});

		buttonCancel.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				value = null;
				cancelled = true;
				shell.dispose();
			}
		});

		shell.addListener(SWT.Traverse, new Listener() {
			public void handleEvent(Event event) {
				if(event.detail == SWT.TRAVERSE_ESCAPE)
					event.doit = false;
			}
		});

		shell.setDefaultButton(buttonOK);
		
		shell.pack();
		shell.open();

		Display display = parent.getDisplay();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}

		return value;
	}
}