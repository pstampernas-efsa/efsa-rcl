package amend_manager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dataset.Dataset;
import table_skeleton.TableVersion;

/**
 * Parser for the .xml dataset and return it into the {@link Dataset} object
 * 
 * @author avonva
 * @author shahaal
 *
 */
public class DatasetComparisonParser implements AutoCloseable {

	private static final Logger LOGGER = LogManager.getLogger(DatasetComparisonParser.class);

	private String rowIdField;
	private String versionField;

	private boolean isResultBlock;
	private boolean isVersionNode;
	private boolean endRecord;

	private String currentNode;

	private DatasetComparison datasetComp;
	private String datasetVersion;

	private InputStream input; // input xml
	private XMLEventReader eventReader; // xml parser

	/**
	 * Initialise the parser with a {@link File} object which contains the path to
	 * the file to parse
	 * 
	 * @param file file which points to the .xml file to parse
	 * @throws FileNotFoundException
	 * @throws XMLStreamException
	 */
	public DatasetComparisonParser(File file, String rowIdField, String versionField)
			throws FileNotFoundException, XMLStreamException {
		this(new FileInputStream(file), rowIdField, versionField);
	}

	/**
	 * Initialise the parser with an {@link InputStream} object
	 * 
	 * @param input input stream which contains the .xml file to parse
	 * @throws XMLStreamException
	 */
	public DatasetComparisonParser(InputStream input, String rowIdField, String versionField)
			throws XMLStreamException {

		this.input = input;
		this.rowIdField = rowIdField;
		this.versionField = versionField;

		this.isResultBlock = false;
		this.endRecord = false;

		// initialise xml parser
		XMLInputFactory factory = XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_COALESCING, true);
		this.eventReader = factory.createXMLEventReader(input);
	}

	/**
	 * Get the next parsed object, otherwise null
	 * 
	 * @throws XMLStreamException
	 */
	public DatasetComparison next() throws XMLStreamException {

		this.datasetComp = null;
		this.endRecord = false;

		// for each node of the xml
		while (eventReader.hasNext() && !endRecord) {

			// read the node
			XMLEvent event = eventReader.nextEvent();

			// actions based on the node type
			switch (event.getEventType()) {

			// if starting xml node
			case XMLStreamConstants.START_ELEMENT:
				start(event);
				break;

			// if looking the xml contents
			case XMLStreamConstants.CHARACTERS:
				parseCharacters(event);
				break;

			// if ending xml node
			case XMLStreamConstants.END_ELEMENT:
				end(event);
				break;
			}
		}

		return this.datasetComp;
	}

	/**
	 * Parse the a node when it starts
	 * 
	 * @param event
	 */
	private void start(XMLEvent event) {

		StartElement startElement = event.asStartElement();

		String qName = startElement.getName().getLocalPart();

		this.currentNode = null;

		if (qName.equals("result")) {
			this.datasetComp = new DatasetComparison();
			this.isResultBlock = true;
		} else if (qName.equals(versionField)) {
			this.isVersionNode = true;
		} else
			this.currentNode = qName;
	}

	/**
	 * Parse the characters of the xml
	 * 
	 * @param event
	 */
	private void parseCharacters(XMLEvent event) {

		// get the xml node value
		String contents = event.asCharacters().getData();

		// cannot parse null content or empty contents
		if (contents == null || contents.trim().isEmpty())
			return;

		if (isResultBlock && currentNode != null) {

			if (currentNode.equals("isNullified")) {
				this.datasetComp.setIsNullified(contents);
			} else if (currentNode.equals("amType")) {
				this.datasetComp.setAmType(AmendType.fromCode(contents));
			} else {

				// save also the xml node
				StringBuilder xmlNode = new StringBuilder("<").append(currentNode).append(">").append(contents)
						.append("</").append(currentNode).append(">");

				this.datasetComp.addXmlNode(xmlNode.toString());

				// if we have the id save it
				if (currentNode.equals(rowIdField)) {
					this.datasetComp.setRowId(contents);
				}
			}
		}

		// if dataset version not retrieved yet, search for it
		if (datasetVersion == null && isVersionNode) {
			// extract the version from the field
			datasetVersion = TableVersion.extractVersionFrom(contents);
			isVersionNode = false;
		}
	}

	/**
	 * Parse a node when it ends
	 * 
	 * @param event
	 */
	private void end(XMLEvent event) {

		// get the xml node
		EndElement endElement = event.asEndElement();
		String qName = endElement.getName().getLocalPart();

		switch (qName) {
		case "result":
			this.datasetComp.buildXml();
			this.datasetComp.setVersion(datasetVersion);
			this.isResultBlock = false;
			this.endRecord = true;
			break;
		default:
			break;
		}
	}

	/**
	 * Close the parser
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {

		if (eventReader != null) {
			try {
				eventReader.close();
			} catch (XMLStreamException e) {
				LOGGER.error("Cannot close the DatasetComparisonParser", e);
				e.printStackTrace();
			}
		}

		eventReader = null;

		if (input != null)
			input.close();
	}

	/*
	 * public static void main(String[] args) throws IOException, XMLStreamException
	 * {
	 * 
	 * String filename = "D:/PortableApps/test1.xml"; File file = new
	 * File(filename);
	 * 
	 * DatasetComparisonParser parser = new DatasetComparisonParser(file,
	 * "senderDatasetId", "resId");
	 * 
	 * DatasetComparison comp; while ((comp = parser.next()) != null) {
	 * //parser.next(); System.out.println(comp.toString());
	 * System.out.println(comp.getIsNullified()); }
	 * 
	 * parser.close(); }
	 */
}
