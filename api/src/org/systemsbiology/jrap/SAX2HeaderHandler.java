/*******************************************************************************
 * --------------------------------------------------------------------------- *
 * File: * @(#) SAX2HeaderHandler.java * Author: * Mathijs Vogelzang
 * m_v@dds.nl
 * ****************************************************************************** * * *
 * This software is provided ``AS IS'' and any express or implied * *
 * warranties, including, but not limited to, the implied warranties of * *
 * merchantability and fitness for a particular purpose, are disclaimed. * * In
 * no event shall the authors or the Institute for Systems Biology * * liable
 * for any direct, indirect, incidental, special, exemplary, or * *
 * consequential damages (including, but not limited to, procurement of * *
 * substitute goods or services; loss of use, data, or profits; or * * business
 * interruption) however caused and on any theory of liability, * * whether in
 * contract, strict liability, or tort (including negligence * * or otherwise)
 * arising in any way out of the use of this software, even * * if advised of
 * the possibility of such damage. * * *
 * ******************************************************************************
 * 
 * ChangeLog
 * 
 * 10-05-2004 Added this header
 * 
 * Created on May 21, 2004
 *  
 ******************************************************************************/

package org.systemsbiology.jrap;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;

/**
 * SAX2HeaderHandler is a SAX2 XML handler that parses the first part of an
 * mzXML file to extract information that comes before the scans. This data is
 * stored in an instance of MZXMLFileInfo.
 * 
 * @author M. Vogelzang
 */
public class SAX2HeaderHandler extends DefaultHandler
{
	public static final String SUCCESSFUL_COMPLETION_MESSAGE = "Header succesfully read.";

	protected MZXMLFileInfo info;

	protected ArrayList parentFiles;
	protected ArrayList dataProcessingSoftware;
	protected boolean msInstrumentMode;
	protected boolean dataProcessingMode;

	protected void finish()
	{
		if (parentFiles != null)
				info.parentFiles = (ParentFile[]) parentFiles
						.toArray(new ParentFile[0]);
		if (dataProcessingSoftware != null)
				info.dataProcessing.softwareUsed = (SoftwareInfo[]) dataProcessingSoftware
						.toArray(new SoftwareInfo[0]);
	}

	public MZXMLFileInfo getInfo()
	{
		return info;
	}

	public SAX2HeaderHandler()
	{
		info = new MZXMLFileInfo();
		parentFiles = new ArrayList();
	}

	public void characters(char[] ch, int start, int length)
			throws SAXException
	{
		// Do nothing
	}

	public void endElement(String uri, String localName, String qName)
			throws SAXException
	{
		if (qName.equals("msInstrument")) msInstrumentMode = false;
		if (qName.equals("dataProcessing")) dataProcessingMode = false;
	}

	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException
	{
		if (qName.equals("scan"))
		{
			finish();
			throw new SAXException(SUCCESSFUL_COMPLETION_MESSAGE);
		}

		if (qName.equals("parentFile"))
		{
			String typeString = attributes.getValue("fileType");
			int type;
			if (typeString.equals("RAWData"))
				type = ParentFile.TYPE_RAW;
			else if (typeString.equals("processedData"))
				type = ParentFile.TYPE_PROCESSED;
			// HACK
			else if (typeString.equals("ModeledData"))
				type = ParentFile.TYPE_RAW;
			else
				throw new SAXException("Unknown type of parent file: "
						+ typeString);
			parentFiles.add(new ParentFile(attributes.getValue("fileName"),
					attributes.getValue("fileSha1"), type));
		} else if (qName.equals("msInstrument"))
		{
			info.instrumentInfo = new MSInstrumentInfo();
			msInstrumentMode = true;
		} else if (qName.equals("msManufacturer"))
		{
			info.instrumentInfo.manufacturer = attributes.getValue("value");
		} else if (qName.equals("msModel"))
		{
			info.instrumentInfo.model = attributes.getValue("value");
		} else if (qName.equals("msIonisation"))
		{
			info.instrumentInfo.ionization = attributes.getValue("value");
		} else if (qName.equals("msMassAnalyzer"))
		{
			info.instrumentInfo.massAnalyzer = attributes.getValue("value");
		} else if (qName.equals("msDetector"))
		{
			info.instrumentInfo.detector = attributes.getValue("value");
		} else if (qName.equals("operator"))
		{
			parseMSOperator(attributes);
		} else if (qName.equals("software"))
		{
			if (msInstrumentMode)
					info.instrumentInfo.softwareInfo = parseSoftware(attributes);
			if (dataProcessingMode)
					dataProcessingSoftware.add(parseSoftware(attributes));
		} else if (qName.equals("dataProcessing"))
		{
			dataProcessingMode = true;
			dataProcessingSoftware = new ArrayList();
			parseDataProcessing(attributes);
		}
	}

	protected void parseMSOperator(Attributes attributes)
	{
		MSOperator operator = new MSOperator();

		operator.firstName = attributes.getValue("first");
		operator.lastName = attributes.getValue("last");
		operator.email = attributes.getValue("email");
		operator.phoneNumber = attributes.getValue("phone");
		operator.URI = attributes.getValue("URI");

		info.instrumentInfo.operator = operator;
	}

	protected SoftwareInfo parseSoftware(Attributes attributes)
	{
		SoftwareInfo info = new SoftwareInfo();

		info.name = attributes.getValue("name");
		info.type = attributes.getValue("type");
		info.version = attributes.getValue("version");

		return info;
	}

	protected void parseDataProcessing(Attributes attributes)
	{
		String value;
		if ((value = attributes.getValue("intensityCutoff")) != null)
				info.dataProcessing.intensityCutoff = Double.parseDouble(value);

		if ((value = attributes.getValue("centroided")) != null)
				info.dataProcessing.centroided = Integer.parseInt(value);

		if ((value = attributes.getValue("deisotoped")) != null)
				info.dataProcessing.deisotoped = Integer.parseInt(value);

		if ((value = attributes.getValue("chargeDeconvoluted")) != null)
				info.dataProcessing.chargeDeconvoluted = Integer
						.parseInt(value);

		if ((value = attributes.getValue("spotIntegration")) != null)
				info.dataProcessing.spotIntegration = Integer.parseInt(value);
	}
}