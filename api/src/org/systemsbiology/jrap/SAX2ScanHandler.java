/*******************************************************************************
 * --------------------------------------------------------------------------- *
 * File: * @(#) SAX2ScanHandler.java * Author: * Robert M. Hubley
 * rhubley@systemsbiology.org
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
 * $Log: SAX2ScanHandler.java,v $
 * Revision 1.4  2006/09/14 19:47:07  eddes_js
 * Fixed 'last intensity not converted' problem.
 *
 * Revision 1.3  2006/05/26 22:46:05  dshteyn
 * Commiting ning's changes for mzXML 3.0 support.
 *
 * Revision 1.2  2005/02/02 22:45:03  thijser
 * Fixed processing of some mzXML files in which a trailing 0 was in the base64 encoded peaklist
 *
 * Revision 1.1  2004/10/19 05:19:33  ajordens
 * Modified the overall structure of the jrap module.  Introduced
 * an ant build process.
 *
 * ant all -> rebuilds everything including javadoc
 * ant run -> runs the mzxml viewer
 *
 * jrap-dist.jar includes all compiled class files and
 * dependent libraries.
 *
 * Code was updated to include the latest modifications from
 * Patrick and Mathijs.
 * Revision 1.1.1.1 2003/04/09 00:02:54 ppatrick
 * Initial import.
 * 
 * 10-05-2004: fixed bug in for loop, M. Vogelzang
 * 
 * 1.2 added logging & handling of precursorMZ 
 * M. Vogelzang  
 * (logging commented out to prevent need of log4j library)
 * 
 ******************************************************************************/
package org.systemsbiology.jrap;
//import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public final class SAX2ScanHandler extends DefaultHandler
{
//	private static final Logger logger =
//		Logger.getLogger(SAX2ScanHandler.class);
	/** A new Scan Object */
	protected Scan tmpScan;

	/** A string to hold the Base64 peak data */
	protected StringBuffer peakData = new StringBuffer();

	/** Flag to indicate we are reading a peak tag */
	protected boolean inPeak = false;

	/** Buffer to hold characters while getting precursorMZ value */
	protected StringBuffer precursorBuffer;

	/** Flag to indicate if we are reading the precursor MZ value */
	protected boolean inPrecursorMZ = false;

	//
	// Getters
	//

	public Scan getScan()
	{
		return (tmpScan);
	}

	private int getIntAttribute(Attributes attrs, String name)
	{
		int result;

		if (attrs.getValue(name) == null) // attribute not present
			return -1;

		try
		{
			result = Integer.parseInt(attrs.getValue(name));
		} catch (NumberFormatException e)
		{
//			logger.error("Numberformatexception!", e);
			result = -1;
		}
		return (result);
	}

	private long getLongAttribute(Attributes attrs, String name)
	{
		long result;

		if (attrs.getValue(name) == null) // attribute not present
			return -1;

		try
		{
			result = Long.parseLong(attrs.getValue(name));
		} catch (NumberFormatException e)
		{
//			logger.error("Numberformatexception!", e);
			result = -1;
		}
		return (result);
	}

	private float getFloatAttribute(Attributes attrs, String name)
	{
		float result;

		if (attrs.getValue(name) == null) // attribute not present
			return -1;

		try
		{
			result = Float.parseFloat(attrs.getValue(name));
		} catch (NumberFormatException e)
		{
//			logger.error("Numberformatexception!", e);
			result = -1;
		} catch (NullPointerException e1)
		{
//			logger.error("Nullpointerexception!", e1);
			result = -1;
		}
		return (result);
	}

	//
	// ContentHandler Methods
	//

	/** Start document. */
	public void startDocument() throws SAXException
	{
		// Nothing to do
	} // startDocument()

	/** Start element. */
	public void startElement(
		String uri,
		String local,
		String raw,
		Attributes attrs)
		throws SAXException
	{
		if (raw.equals("scan"))
		{
			tmpScan = new Scan();
			tmpScan.setNum(getIntAttribute(attrs, "num"));
			tmpScan.setMsLevel(getIntAttribute(attrs, "msLevel"));
			tmpScan.setPeaksCount(getIntAttribute(attrs, "peaksCount"));
			tmpScan.setPolarity(attrs.getValue("polarity"));
			tmpScan.setScanType(attrs.getValue("scanType"));
			tmpScan.setCentroided(getIntAttribute(attrs, "centroided"));
			tmpScan.setDeisotoped(getIntAttribute(attrs, "deisotoped"));
			tmpScan.setChargeDeconvoluted(
				getIntAttribute(attrs, "chargeDeconvoluted"));
			tmpScan.setRetentionTime(attrs.getValue("retentionTime"));
			tmpScan.setStartMz(getFloatAttribute(attrs, "startMz"));
			tmpScan.setEndMz(getFloatAttribute(attrs, "endMz"));
			tmpScan.setLowMz(getFloatAttribute(attrs, "lowMz"));
			tmpScan.setHighMz(getFloatAttribute(attrs, "highMz"));
			tmpScan.setBasePeakMz(getFloatAttribute(attrs, "basePeakMz"));
			tmpScan.setBasePeakIntensity(
				getFloatAttribute(attrs, "basePeakIntensity"));
			tmpScan.setTotIonCurrent(getFloatAttribute(attrs, "totIonCurrent"));
		} else if (raw.equals("peaks"))
		{
			tmpScan.setPrecision(getIntAttribute(attrs, "precision"));
			tmpScan.setByteOrder(attrs.getValue("byteOrder"));
		      
			if(attrs.getValue("contentType") == null)
			    tmpScan.setContentType("none");
			else
			    tmpScan.setContentType(attrs.getValue("contentType"));

			if(attrs.getValue("compressionType") == null)
			    tmpScan.setCompressionType("none");
			else
			    tmpScan.setCompressionType(attrs.getValue("compressionType"));

			tmpScan.setCompressedLen(getIntAttribute(attrs, "compressedLen"));
			inPeak = true;
		} else if (raw.equals("precursorMz"))
		{
			tmpScan.setPrecursorScanNum(
				getIntAttribute(attrs, "precursorScanNum"));
			tmpScan.setPrecursorCharge(
				getIntAttribute(attrs, "precursorCharge"));
			tmpScan.setCollisionEnergy(
				getFloatAttribute(attrs, "collisionEnergy"));
			tmpScan.setIonisationEnergy(
				getFloatAttribute(attrs, "ionisationEnergy"));
			
			precursorBuffer = new StringBuffer();
			inPrecursorMZ = true;
		}
	} // startElement(String,String,StringAttributes)

	public void endElement(String uri, String local, String raw)
		throws SAXException
	{
		if (raw.equals("peaks"))
		{
		    if (tmpScan.getPrecision() == 32)
            {
                tmpScan.setFloatMassIntensityList( Scan.parseRawIntensityData(peakData.toString(),
                        tmpScan.getPeaksCount(),tmpScan.getPrecision(), tmpScan.getCompressionType(),
                        tmpScan.getByteOrder()));
            }
            else if (tmpScan.getPrecision() == 64)
            {
                tmpScan.setDoubleMassIntensityList( Scan.parseRawIntensityDataDouble(peakData.toString(),
                        tmpScan.getPeaksCount(), tmpScan.getPrecision(), tmpScan.getCompressionType(),
                        tmpScan.getByteOrder()));
            }
			inPeak = false;
            peakData.delete(0, peakData.capacity());

			throw (new SAXException("ScanEndFoundException"));			
		} else if (raw.equals("precursorMz"))
		{
			tmpScan.setPrecursorMz(
				Float.parseFloat(precursorBuffer.toString()));
			precursorBuffer = null; // make available for garbage collection

			inPrecursorMZ = false;
		}
	} // endElement()

	/** Characters. */
	public void characters(char ch[], int start, int length)
		throws SAXException
	{
		if (inPeak)
		{
			peakData.append(ch, start, length);
		} else if (inPrecursorMZ)
		{
			precursorBuffer.append(ch, start, length);
		}
	} // characters(char[],int,int);

	/** Ignorable whitespace. */
	public void ignorableWhitespace(char ch[], int start, int length)
		throws SAXException
	{
		// Do nothing
	} // ignorableWhitespace(char[],int,int);

	/** Processing instruction. */
	public void processingInstruction(String target, String data)
		throws SAXException
	{
		// Do nothing
	} // processingInstruction(String,String)

	//
	// ErrorHandler methods
	//

	/** Warning. */
	public void warning(SAXParseException ex) throws SAXException
	{
		// Do nothing
		//printError("Warning", ex);
	} // warning(SAXParseException)

	/** Error. */
	public void error(SAXParseException ex) throws SAXException
	{
		// Do nothing
		//printError("Error", ex);
	} // error(SAXParseException)

	/** Fatal error. */
	public void fatalError(SAXParseException ex) throws SAXException
	{
		// Do nothing
		//printError("Fatal Error", ex);
	} // fatalError(SAXParseException)

	//
	// Protected methods
	//

	/** Prints the error message. */
	protected void printError(String type, SAXParseException ex)
	{
		System.err.print("[");
		System.err.print(type);
		System.err.print("] ");
		if (ex == null)
		{
			System.out.println("!!!");
		}
		String systemId = ex.getSystemId();
		if (systemId != null)
		{
			int index = systemId.lastIndexOf('/');
			if (index != -1)
				systemId = systemId.substring(index + 1);
			System.err.print(systemId);
		}
		System.err.print(':');
		System.err.print(ex.getLineNumber());
		System.err.print(':');
		System.err.print(ex.getColumnNumber());
		System.err.print(": ");
		System.err.print(ex.getMessage());
		System.err.println();
		System.err.flush();
	} // printError(String,SAXParseException)

}
