package table_relations;

import app_config.AppPaths;

import java.util.Collection;

public class RelationList {
	
	private static Collection<Relation> relationsCache;

	/**
	 * Get all the relations contained in the excel sheet {@link AppPaths#RELATIONS_SHEET}
	 * @return
	 */
	public static Collection<Relation> getAll() {
		if (relationsCache == null) {
			try (RelationParser parser = new RelationParser(AppPaths.TABLES_SCHEMA_FILE)) {
			relationsCache = parser.read();
			} catch (Exception e) {
				throw new RuntimeException("Could not load relations table", e);
			}
		}
		return relationsCache;
	}
}
