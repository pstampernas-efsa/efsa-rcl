package message_creator;

import app_config.AppPaths;

/**
 * Operation types that are used to create a report
 * 
 * @author avonva
 * @author shahaal
 *
 */
public enum OperationType {
	INSERT("Insert", "Insert"),
	REPLACE("Replace", "Replace"),
	REJECT("Reject", "Reject"),
	SUBMIT("Submit", "Submit"),
	TEST("Test", "Insert"),
	NOT_SUPPORTED("NotSupported", "NotSupported");

	// accepted Dwh status for beta testers
	// ACCEPTED_DWH_BETA("AcceptDwhBeta", "Accept dwh");

	private final String internalOpType;
	private final String opTypeName;

	OperationType(String internalOpType, String opTypeName) {
		this.internalOpType = internalOpType;
		this.opTypeName = opTypeName;
	}

	/**
	 * Get the code of the operation
	 * 
	 * @return
	 */
	public String getInternalOpType() {
		return internalOpType;
	}

	/**
	 * Get the code which will be used in the {@link AppPaths#APP_CONFIG_FILE} as
	 * operation type and also in the exported file.
	 * 
	 * @return
	 */
	public String getOpType() {
		return opTypeName;
	}

	/**
	 * Check if the operation needs an empty dataset to be sent to the dcf
	 * 
	 * @return
	 */
	public boolean needEmptyDataset() {
		return this == REJECT || this == SUBMIT; // || this == OperationType.ACCEPTED_DWH_BETA;
	}

	/**
	 * Get the enumerator that matches the {@code text}
	 * 
	 * @param text
	 * @return
	 */
	public static OperationType fromString(String text) {
		for (OperationType b : OperationType.values()) {
			if (b.internalOpType.equalsIgnoreCase(text)) {
				return b;
			}
		}
		return null;
	}
}
