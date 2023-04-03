package formula;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FoodexElement {
	
	private static final Logger LOGGER = LogManager.getLogger(FoodexElement.class);

	private Collection<AttributeElement> facetList;
	private String baseTerm;
	
	public FoodexElement(String baseTerm, Collection<AttributeElement> facetList) {
		this.baseTerm = baseTerm;
		this.facetList = facetList;
	}
	
	public String getBaseTerm() {
		return baseTerm;
	}
	
	public Collection<AttributeElement> getFacetList() {
		return facetList;
	}
	
	private String printFacets() {
		
		StringBuilder sb = new StringBuilder();
		
		int count = 0;
		for (AttributeElement facet : facetList) {
			
			if (count > 0) {
				sb.append("$");
			}
			
			sb.append(facet.getId()).append(".").append(facet.getValue());
			
			count++;
		}
		LOGGER.debug("Facet : " + sb.toString());
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return baseTerm + "#" + printFacets();
	}
}
