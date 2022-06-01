package dataset;

import java.io.Closeable;
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

import amend_manager.DatasetComparison;
import dataset.Header.HeaderNode;
import dataset.Operation.OperationNode;
import table_skeleton.TableRow;
import xml_catalog_reader.XmlContents;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Parser for the .xml dataset and return it into the {@link Dataset} object
 * @author avonva
 *
 */
@Deprecated
public class DatasetParser implements Closeable {
	
	private static final Logger LOGGER = LogManager.getLogger(DatasetParser.class);

	private enum CurrentBlock {
		HEADER,
		OPERATION,
		PAYLOAD,
		RESULT,
		RESULT_NODE,
		MESSAGE,
		DATASET
	}
	
	private CurrentBlock currentBlock;
	private String currentNode;

	private HeaderBuilder headerBuilder;
	private OperationBuilder opBuilder;
	
	private DatasetComparison datasetComp;
	private TableRow datasetRow;
	
	private Dataset dataset;
	private InputStream input;               // input xml
	private XMLEventReader eventReader;      // xml parser
	
	/**
	 * Initialise the parser with a {@link File} object which
	 * contains the path to the file to parse
	 * @param file file which points to the .xml file to parse
	 * @throws FileNotFoundException
	 * @throws XMLStreamException
	 */
	public DatasetParser(File file) throws FileNotFoundException, XMLStreamException {
		this(new FileInputStream(file));
	}
	
	/**
	 * Initialise the parser with an {@link InputStream} object
	 * @param input input stream which contains the .xml file to parse
	 * @throws XMLStreamException
	 */
	public DatasetParser(InputStream input) throws XMLStreamException {
		
		this.input = input;
		this.dataset = new Dataset();
		this.datasetComp = new DatasetComparison();
		
		// Initialise xml parser
		XMLInputFactory factory = XMLInputFactory.newInstance();
		factory.setProperty(XMLInputFactory.IS_COALESCING, true);
		this.eventReader = factory.createXMLEventReader(input);
		
		this.currentBlock = CurrentBlock.MESSAGE;
	}


	/**
	 * Parse the .xml document and get the contents in a java object
	 * {@link XmlContents}
	 * @throws XMLStreamException
	 */
	public Dataset parse() throws XMLStreamException {
		
		// for each node of the xml
		while (eventReader.hasNext()) {

			// read the node
			XMLEvent event = eventReader.nextEvent();

			// actions based on the node type
			switch(event.getEventType()) {

			// if starting xml node
			case XMLStreamConstants.START_ELEMENT:
				start(event);
				break;

			// if looking the xml contents
			case XMLStreamConstants.CHARACTERS:
				parseCharacters(event);
				break;

			// if ending xml node
			case  XMLStreamConstants.END_ELEMENT:
				end(event);
				break;
			}
		}
		
		return this.dataset;
	}
	
	/**
	 * Parse the a node when it starts
	 * @param event
	 */
	private void start(XMLEvent event) {
		
		StartElement startElement = event.asStartElement();

		String qName = startElement.getName().getLocalPart();

		this.currentNode = null;
		
		switch(qName) {
		case "header":
			this.headerBuilder = new HeaderBuilder();
			this.currentBlock = CurrentBlock.HEADER;
			break;
		case "operation":
			this.opBuilder = new OperationBuilder();
			this.currentBlock = CurrentBlock.OPERATION;
			break;
		case "payload":
			this.currentBlock = CurrentBlock.PAYLOAD;
			break;
		case "message":
			this.currentBlock = CurrentBlock.MESSAGE;
			break;
		case "dataset":
			this.currentBlock = CurrentBlock.DATASET;
			break;
		case "result":
			this.datasetRow = new TableRow();
			this.currentBlock = CurrentBlock.RESULT;
			break;
		default:
			this.currentNode = qName;
			break;
		}
	}
	
	/**
	 * Parse the characters of the xml
	 * @param event
	 */
	private void parseCharacters (XMLEvent event) {
		
		// get the xml node value
		String contents = event.asCharacters().getData();
		
		// cannot parse null content or empty contents
		if (contents == null || currentNode == null || contents.trim().isEmpty())
			return;

		switch(currentBlock) {
		
		case HEADER:
			HeaderNode headerNode = HeaderNode.fromString(currentNode);
			switch(headerNode) {
			case TYPE:
				headerBuilder.setType(contents); break;
			case VERSION:
				headerBuilder.setVersion(contents); break;
			case SENDER_MSG_ID:
				headerBuilder.setSenderMessageId(contents); break;
			case SENDER_ORG_CODE:
				headerBuilder.setSenderOrgCode(contents); break;
			case RECEIVER_ORG_CODE:
				headerBuilder.setReceiverOrgCode(contents); break;
			default: break;
			}
			
			break;
			
		case OPERATION:

			OperationNode opNode = OperationNode.fromString(currentNode);
			switch(opNode) {
			case OP_TYPE:
				opBuilder.setOpType(contents); break;
			case SENDER_DATASET_ID:
				opBuilder.setSenderDatasetId(contents); break;
			case DATASET_ID:
				opBuilder.setDatasetId(contents); break;
			case DC_CODE:
				opBuilder.setDcCode(contents); break;
			case DC_TABLE:
				opBuilder.setDcTable(contents); break;
			case ORG_CODE:
				opBuilder.setOrgCode(contents); break;
			case OP_COM:
				opBuilder.setOpCom(contents); break;
			default: break;
			}
			break;
			
		case RESULT:
			
			datasetRow.put(currentNode, contents);
			
			// save also the xml node
			StringBuilder xmlNode = new StringBuilder("<")
					.append(currentNode)
					.append(">")
					.append(contents)
					.append("</")
					.append(currentNode)
					.append(">");
			
			this.datasetComp.addXmlNode(xmlNode.toString());
			break;
			
		default:
			break;
		}

	}
	
	/**
	 * Parse a node when it ends
	 * @param event
	 */
	private void end (XMLEvent event) {
		
		// get the xml node
		EndElement endElement = event.asEndElement();
		String qName = endElement.getName().getLocalPart();

		switch(qName) {
		
		case "header":
			this.dataset.setHeader(headerBuilder.build());
			break;
		case "operation":
			this.dataset.setOperation(opBuilder.build());
			break;
		case "result":
			this.dataset.addRow(datasetRow);
			this.datasetComp.buildXml();
			break;
		default:
			break;
		}
	}
	
	/**
	 * Close the parser
	 * @throws IOException 
	 */
	public void close () throws IOException {
		
		if (eventReader != null) {
			try {
				eventReader.close();
			} catch (XMLStreamException e) {
				LOGGER.error("Error in closing parser", e);
				e.printStackTrace();
			}
		}

		eventReader = null;
		
		if (input != null)
			input.close();
	}
}
