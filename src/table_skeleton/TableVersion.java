package table_skeleton;

public class TableVersion {

	/**
	 * Create a new version formatted as:
	 * 01, 02, 03, ..., 10, 11, ..., 99
	 * @param versionCode
	 * @return
	 */
	public static String createNewVersion(String versionCode) {
		
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
			e.printStackTrace();
		}
		
		return newVersionCode;
	}
}