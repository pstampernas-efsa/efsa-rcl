package xml_catalog_reader;

import java.io.FileNotFoundException;

import javax.xml.stream.XMLStreamException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test the xml parser
 * @author avonva
 *
 */
public class XmlParserTest {
	
	private static final Logger LOGGER = LogManager.getLogger(XmlParserTest.class);

	public static void main(String[] args) throws FileNotFoundException, XMLStreamException {
		XmlParser parser = new XmlParser(args[0]);
		XmlContents contents = parser.parse();
		LOGGER.debug("XmlContents: " + contents);
	}
}
