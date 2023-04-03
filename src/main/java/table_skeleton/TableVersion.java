package table_skeleton;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TableVersion {

	private static final Logger LOGGER = LogManager.getLogger(TableVersion.class);
	
	public static String extractVersionFrom(String field) {
		
		String[] split = field.split("\\.");
		if (split.length < 2)
			return "00";
		
		return split[1];
	}
	
	public static String getFirstVersion() {
		return "00";
	}
	
	public static boolean isFirstVersion(String version) {
		return version == null || version.isEmpty() 
				|| version.equals("00") || version.equals("0");
	}
	
	public static int getNumVersion(String version) {
		
		if (version == null || version.isEmpty())
			return 0;
		
		return Integer.valueOf(version);
	}
	
	public static String getPreviousVersion(String versionCode) {
		
		// if starting from no version, add the first version
		if (versionCode == null || versionCode.isEmpty()) {
			return getFirstVersion();
		}
		
		String newVersionCode = null;
		
		try {
			
			// get the current version (integer)
			int versionNumber = Integer.valueOf(versionCode);
			
			// increase the version number by 1
			versionNumber--;
			
			if (versionNumber < 0)
				return null;
			
			// convert to string
			newVersionCode = String.valueOf(versionNumber);
			
			// add padding if needed to always get two numbers
			if (versionNumber < 10) {
				newVersionCode = "0" + newVersionCode;
			}
		}
		catch (NumberFormatException e) {
			LOGGER.error("Cannot decrement version. Expected number, found=" + versionCode, e);
			e.printStackTrace();
		}
		
		return newVersionCode;
	}
	
	/**
	 * Create a new version formatted as:
	 * 01, 02, 03, ..., 10, 11, ..., 99
	 * @param versionCode
	 * @return
	 */
	public static String createNewVersion(String versionCode) {
		
		// if starting from no version, add the first version
		if (versionCode == null || versionCode.isEmpty()) {
			return "01";
		}
		
		String newVersionCode = null;
		
		try {
			
			// get the current version (integer)
			int versionNumber = Integer.valueOf(versionCode);
			
			// increase the version number by 1
			versionNumber++;
			
			// convert to string
			newVersionCode = String.valueOf(versionNumber);
			
			// add padding if needed to always get two numbers
			if (versionNumber < 10) {
				newVersionCode = "0" + newVersionCode;
			}
		}
		catch (NumberFormatException e) {
			LOGGER.error("Cannot increment version. Expected number, found=" + versionCode, e);
			e.printStackTrace();
		}
		
		return newVersionCode;
	}
	
	/**
	 * Merge the string passed in name with the version by a dot
	 * @param name
	 * @param version
	 * @return
	 */
	public static String mergeNameAndVersion(String name, String version) {
		return name + "." + version;
	}
}
