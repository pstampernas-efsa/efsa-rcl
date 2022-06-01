package xlsx_reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;

import app_config.AppPaths;
import table_list.TableListParser;
import table_relations.RelationParser;

public class TableSchemaList extends ArrayList<TableSchema> {

	private static final Logger LOGGER = LogManager.getLogger(TableSchemaList.class);

	private static final long serialVersionUID = 1L;

	private static HashMap<String, TableSchemaList> schemasCache;

	public static TableSchemaList getAll(String tablesSchemaFilename) throws IOException {

		// if first time
		if (schemasCache == null) {
			schemasCache = new HashMap<>();
		}

		// if the schema was not loaded yet
		if (schemasCache.get(tablesSchemaFilename) == null) {

			TableSchemaList list = new TableSchemaList();

			SchemaReader parser = new SchemaReader(tablesSchemaFilename);

			for (int i = 0; i < parser.getNumberOfSheets(); ++i) {

				parser = new SchemaReader(tablesSchemaFilename);

				Sheet sheet = parser.getSheetAt(i);

				// skip special sheets
				if (RelationParser.isRelationsSheet(sheet.getSheetName())
						|| TableListParser.isTablesSheet(sheet.getSheetName()))
					continue;

				// parse
				parser.read(sheet.getSheetName());

				// get parsed schema
				TableSchema schema = parser.getSchema();

				// add to cache
				list.add(schema);
			}

			schemasCache.put(tablesSchemaFilename, list);

			parser.close();
		}

		return schemasCache.get(tablesSchemaFilename);
	}

	/**
	 * Get all the table schemas which were defined by the user
	 * 
	 * @return
	 * @throws IOException
	 */
	public static TableSchemaList getAll() throws IOException {
		return getAll(AppPaths.TABLES_SCHEMA_FILE);
	}

	public TableSchema getSchemaByName(String name) {

		for (TableSchema schema : this) {

			if (schema.getSheetName().equals(name))
				return schema;
		}

		return null;
	}

	public static TableSchema getByName(String tablesSchemaFilename, String sheetName) {

		TableSchemaList schemas;
		try {
			schemas = getAll(tablesSchemaFilename);
		} catch (IOException e) {
			LOGGER.error("Cannot get tables schemas from filename=" + tablesSchemaFilename + ". Returning null", e);
			e.printStackTrace();
			return null;
		}

		for (TableSchema schema : schemas) {

			if (schema.getSheetName().equals(sheetName))
				return schema;
		}

		return null;
	}

	/**
	 * Load a generic schema from the {@link CustomPaths#TABLES_SCHEMA_FILE} file
	 * using the {@code sheetName} sheet
	 * 
	 * @param sheetName
	 * @return
	 * @throws IOException
	 */
	public static TableSchema getByName(String sheetName) {
		return getByName(AppPaths.TABLES_SCHEMA_FILE, sheetName);
	}

}