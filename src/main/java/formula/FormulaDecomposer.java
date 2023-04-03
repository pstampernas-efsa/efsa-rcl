package formula;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * This class is used to decompose a compound field into its
 * children fields. In particular, it is needed the formula
 * that was used to generate the compound field and the 
 * actual value of the compound field.
 * The compound field can have different syntax. In particular, a specific
 * function need to be called for the specific syntax.
 * <ul>
 * 	<li>{@link #decomposeSimpleField(String, false)} Data separated by delimiter, for example with $: data1$data2$...</li>
 * 	<li>{@link #decomposeSimpleField(String, true)} Data separated by delimiter with attributes names inside it. For example with $: 
 * 	    attribute1=data1$attribute2=data2$...</li>
 * 	<li>{@link #decomposeFoodexCode(false)} Foodex syntax: baseTerm#facet1$facet2$...</li>
 * 	<li>{@link #decomposeFoodexCode(true)} Foodex syntax with attributes: baseTerm#attr1=facet1$attr2=facet2$...</li>
 * </ul>
 * @author avonva
 *
 */
public class FormulaDecomposer {
	
	private static final Logger LOGGER = LogManager.getLogger(FormulaDecomposer.class);
	
	public enum AttributeIdentifier {
		NAME_VALUE,
		FACET_HEADER
	}
	
	/**
	 * get the base term of a foodex field
	 * @param field
	 * @return
	 * @throws ParseException
	 */
	public String getBaseTerm(String value) throws ParseException {
		
		String[] split = value.split("#");
		String baseTerm = split[0];
		return baseTerm;
	}
	
	
	/**
	 * Split a foodex code
	 * @param value the entire foodex code
	 * @param identifier how the facets are identified (by attribute name or by facet header)
	 * @return
	 * @throws ParseException 
	 */
	public FoodexElement splitFoodex(String value, AttributeIdentifier identifier) throws ParseException {
		
		String[] split = value.split("#");
		
		// if single element, we have only the base term
		if (split.length == 1) {
			return new FoodexElement(value, new ArrayList<>());
		}
		
		String baseTerm = split[0];
		String rawFacetList = split[1];
		
		Collection<AttributeElement> facets = new ArrayList<>();
		
		if (identifier == AttributeIdentifier.NAME_VALUE)
			facets = split(rawFacetList);
		else
			facets = splitFacetsByHeader(rawFacetList);
		
		return new FoodexElement(baseTerm, facets);
	}
	
	/**
	 * Split a list of facets into facet header > facet code list
	 * @param rawFacetList
	 * @return
	 * @throws ParseException 
	 */
	private Collection<AttributeElement> splitFacetsByHeader(String rawFacetList) throws ParseException {
		
		Collection<AttributeElement> out = new ArrayList<>();
		
		Collection<String> facets = splitList(rawFacetList, "$");
	
		for (String facet : facets) {
			
			String[] split = facet.split("\\.");
			if (split.length != 2) {
				throw new ParseException(facet, -1);
			}
			
			String facetHeader = split[0];
			String facetCode = split[1];
			
			// create a new facet attribute and return it
			AttributeElement f = new AttributeElement(facetHeader, facetCode);
			out.add(f);
		}
		
		return out;
	}
	
	
	/**
	 * Split a simple repeatable attribute field
	 * as a=value1$b=value2$c=value3.
	 * @param value
	 * @return collection of attribute elements
	 * @throws ParseException 
	 */
	public Collection<AttributeElement> split(String value) throws ParseException {
		
		Collection<AttributeElement> out = new ArrayList<>();
		
		Collection<String> attributes = splitList(value, "$");
		
		for (String attribute : attributes) {
			
			String[] split = attribute.split("=");
			
			if (split.length != 2) {
				throw new ParseException(attribute, -1);
			}
			
			// save attribute and value
			out.add(new AttributeElement(split[0], split[1]));
		}

		return out;
	}
	
	
	/**
	 * Split a list of elements separated by a separator
	 * @param value
	 * @param separator
	 * @return
	 */
	private Collection<String> splitList(String value, String separator) {
		
		Collection<String> out = new ArrayList<>();
		
		StringTokenizer st = new StringTokenizer(value, separator);
		
		while (st.hasMoreTokens()) {
			out.add(st.nextToken());
		}
		
		return out;
	}

	
	public static void main(String[] args) throws ParseException {
			
		String value = "A04MQ#F01.A057B$F02.A06AM$F21.A07RV$F31.A16NK";
		
		LOGGER.info("Foodex code with facet headers: " + value);
		
		FormulaDecomposer d = new FormulaDecomposer();
		FoodexElement a = d.splitFoodex(value, AttributeIdentifier.FACET_HEADER);
		LOGGER.info("BASETERM: " + a.getBaseTerm());
		LOGGER.info("FACETS: " + a.getFacetList());
		
		value = "a=fjaijd$b=iajhiaj";
		LOGGER.info("Simple attribute name/value: " + value);
		LOGGER.info("ATTRIBUTES: " + d.split(value));

		value = "A04MQ#allele1=A057B$allele2=19KMO8";
		LOGGER.info("Foodex code with attributes: " + value);
		
		FoodexElement code = d.splitFoodex(value, AttributeIdentifier.NAME_VALUE);
		LOGGER.info("BASETERM: " + code.getBaseTerm());
		LOGGER.info("FACETS: " + code.getFacetList());
	}
}
