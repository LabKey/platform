/*******************************************************************************
 * --------------------------------------------------------------------------- *
 * File: * @(#) Scan.java * Author: * Robert M. Hubley
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
 * $Log: Scan.java,v $
 * Revision 1.2  2005/02/02 22:44:32  thijser
 * Clarified getPeakList
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
 * Revision 1.1.1.1 2003/04/09 00:02:54 ppatrick Initial
 * import.
 * 
 * 10-05-2004 added javadocs, M. Vogelzang
 * 
 * 1.2 Added getDoubleRetentionTime method 
 * M. Vogelzang
 * 
 *  
 ******************************************************************************/
package org.systemsbiology.jrap;

import java.io.Serializable;

import java.util.zip.*;

/**
 * A simple class to hold the contents of a scan from a MSXML file.
 * 
 * This is a start. For those who want to get more fancy you should only have to
 * modify the SAX2ScanHandler to replace this.
 *  
 */
public final class Scan implements Serializable
{

	protected ScanHeader header;

	/**
	 * A 2-dimensional array, element 0 contains a list of masses of peaks,
	 * element 1 contains a list of intensities of peaks.
	 */
	protected float[][] floatMassIntensityList = null;
    protected double[][] doubleMassIntensityList = null;

	/**
	 * Default constructor, initializes an empty Scan. A typical application
	 * probably only wants to use MSXMLParser.rap() to create Scan objects.
	 */
	public Scan()
	{
		header = new ScanHeader();
	}

    /*
    helper routine for handling base64 and zlib with mass/intensity data
     */
    private static byte[] decodeAndDecompress(String peakData, int peaksCount, int precision, String compressionType)
    {
        byte[] tmpArr = Base64.decode(peakData);
        if ((compressionType != null) && compressionType.equals("zlib"))
        {
            try{
            Inflater decompresser = new Inflater();
            decompresser.setInput(tmpArr, 0, tmpArr.length);
            byte[] result = new byte[(peaksCount+1)*(precision/4)];
            decompresser.inflate(result);
            decompresser.end();
            tmpArr = result;
            }
            catch(DataFormatException e)
            {
                e.printStackTrace();
            }
        }
        return tmpArr;
    }

    /*
      parse raw mass/intensity data, return as float - even if it's really doubles (see parseRawIntensityDataDouble
      if you want to hold as doubles instead)
     */
    public static float[][] parseRawIntensityData(String peakData, int peaksCount, int precision, String compressionType, String byteOrder)
    {
        if (precision != 32)
        {    // read double, cast to float
            double[][] tmp = parseRawIntensityDataDouble(peakData, peaksCount, precision, compressionType, byteOrder);
            float[][] tmpMassIntensityList = new float[2][tmp[0].length];
            for (int i=0;i<tmp[0].length;i++)
            {
                tmpMassIntensityList[0][i] = (float) tmp[0][i];
                tmpMassIntensityList[1][i] = (float) tmp[1][i];
            }
            return tmpMassIntensityList;
        }
        byte[] tmpArr = decodeAndDecompress(peakData, peaksCount,  precision, compressionType);
        int floatBytes = precision / 8;
        float[][] tmpMassIntensityList =
            new float[2][peaksCount];
        int peakIndex = 0;
        int fieldIndex = 0;

        if (floatBytes <= 0)
            System.err.println("FLOATBYTES <= 0!!!");
        boolean intelByteOrder = (byteOrder!=null) && !byteOrder.equals("network");

        for (int i = 0; i <= tmpArr.length - floatBytes && peakIndex < peaksCount; i += floatBytes)
        {
            int intBits;
            if (intelByteOrder)
            {   // must reverse byte order
                intBits = (((int) tmpArr[i + 3]) & 0xff);
                intBits <<= 8;
                intBits |= (((int) tmpArr[i + 2]) & 0xff);
                intBits <<= 8;
                intBits |= (((int) tmpArr[i + 1]) & 0xff);
                intBits <<= 8;
                intBits |= (((int) tmpArr[i]) & 0xff);
            }
            else
            {
                intBits = (((int) tmpArr[i]) & 0xff);
                intBits <<= 8;
                intBits |= (((int) tmpArr[i + 1]) & 0xff);
                intBits <<= 8;
                intBits |= (((int) tmpArr[i + 2]) & 0xff);
                intBits <<= 8;
                intBits |= (((int) tmpArr[i + 3]) & 0xff);
            }
            // Must be in IEEE 754 encoding!
            tmpMassIntensityList[fieldIndex++][peakIndex] =
                Float.intBitsToFloat(intBits);
            if (fieldIndex == 2)
            {
                fieldIndex = 0;
                peakIndex++;
            }
        }
        return tmpMassIntensityList;
    }                                         

     /*
      parse raw mass/intensity data, return as double - even if it's really floats (see parseRawIntensityData
      if you want to hold as floats instead)
     */
      public static double[][] parseRawIntensityDataDouble(String peakData, int peaksCount, int precision, String compressionType, String byteOrder)
    {
        if (precision != 64)
        {    // read float, cast to double
            float[][] tmp = parseRawIntensityData(peakData, peaksCount, precision, compressionType, byteOrder);
            double[][] tmpMassIntensityList = new double[2][tmp[0].length];
            for (int i=0;i<tmp[0].length;i++)
            {
                tmpMassIntensityList[0][i] = (double) tmp[0][i];
                tmpMassIntensityList[1][i] = (double) tmp[1][i];
            }
            return tmpMassIntensityList;
        }
        byte[] tmpArr = decodeAndDecompress(peakData, peaksCount,  precision, compressionType);
        
        int doubleBytes = precision / 8;
        double[][] tmpMassIntensityList = new double[2][peaksCount];
        int peakIndex = 0;
        int fieldIndex = 0;

        if (doubleBytes <= 0)
            System.err.println("DOUBLEBYTES <= 0!!!");
        boolean intelByteOrder = (byteOrder != null) && !byteOrder.equals("network");


        for (int i = 0; i <= tmpArr.length - doubleBytes && peakIndex < peaksCount; i += doubleBytes)
        {
            long longBits;
            if (intelByteOrder)
            {   // must reverse byte order
                longBits = (((int) tmpArr[i + 7]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 6]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 5]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 4]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 3]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 2]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 1]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i]) & 0xff);                
            }
            else
            {
                longBits = (((int) tmpArr[i]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 1]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 2]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 3]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 4]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 5]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 6]) & 0xff);
                longBits <<= 8;
                longBits |= (((int) tmpArr[i + 7]) & 0xff);
            }

            // Must be in IEEE 754 encoding!
            tmpMassIntensityList[fieldIndex++][peakIndex] =
                Double.longBitsToDouble(longBits);
            if (fieldIndex == 2)
            {
            fieldIndex = 0;
            peakIndex++;
            }
        }
        return tmpMassIntensityList;
    }
    //
	// Setter methods
	//

	/**
	 * Set the number of this scan in the file.
	 */
	public void setNum(int newValue)
	{
		header.num = newValue;
	}

	/**
	 * Set the MS level of this scan
	 * 
	 * @param newValue
	 *            the new MS level of this scan
	 */
	public void setMsLevel(int newValue)
	{
		header.msLevel = newValue;
	}

	/**
	 * Set the number of peaks in this scan.
	 * 
	 * @param newValue
	 *            the new number of peaks in this scan.
	 */
	public void setPeaksCount(int newValue)
	{
		header.peaksCount = newValue;
	}

	/**
	 * Set the polarity of this scan
	 * 
	 * @param newValue
	 *            the new polarity of this scan
	 */
	public void setPolarity(String newValue)
	{
		header.polarity = newValue;
	}

	/**
	 * Set the type of this scan
	 * 
	 * @param newValue
	 *            the new type of this scan
	 */
	public void setScanType(String newValue)
	{
		header.scanType = newValue;
	}

	/**
	 * Set if this scan was centroided or not
	 * 
	 * @param newValue
	 *            0 if this scan was not centroided, 1 if it was
	 */
	public void setCentroided(int newValue)
	{
		header.centroided = newValue;
	}

	/**
	 * Set if this scan was deisotoped or not
	 * 
	 * @param newValue
	 *            0 if this scan was not deisotoped, 1 if it was
	 */
	public void setDeisotoped(int newValue)
	{
		header.deisotoped = newValue;
	}

	/**
	 * Set if this scan was charge deconvoluted or not
	 * 
	 * @param newValue
	 *            0 if this scan was not charge deconvoluted, 1 if it was
	 */
	public void setChargeDeconvoluted(int newValue)
	{
		header.chargeDeconvoluted = newValue;
	}

	/**
	 * Set the retention time at which this scan was acquired
	 * 
	 * @param newValue
	 *            the new value for the retention time.
	 */
	public void setRetentionTime(String newValue)
	{
		header.retentionTime = newValue;
	}

	public void setStartMz(float newValue)
	{
		header.startMz = newValue;
	}

	public void setEndMz(float newValue)
	{
		header.endMz = newValue;
	}

	public void setLowMz(float newValue)
	{
		header.lowMz = newValue;
	}

	public void setHighMz(float newValue)
	{
		header.highMz = newValue;
	}

	/**
	 * Set the base peak m/z value for this scan
	 * 
	 * @param newValue
	 *            the new value to set
	 */
	public void setBasePeakMz(float newValue)
	{
		header.basePeakMz = newValue;
	}

	/**
	 * Set the intensity of the base peak
	 * 
	 * @param newValue
	 *            the new value to set
	 */
	public void setBasePeakIntensity(float newValue)
	{
		header.basePeakIntensity = newValue;
	}

	/**
	 * Set the total ion current of this scan
	 * 
	 * @param newValue
	 *            the new value to set
	 */
	public void setTotIonCurrent(float newValue)
	{
		header.totIonCurrent = newValue;
	}

	/**
	 * Set the precursor m/z value for this scan
	 * 
	 * @param newValue
	 *            the new value to set.
	 */
	public void setPrecursorMz(float newValue)
	{
		header.precursorMz = newValue;
	}

	/**
	 * Set the scan number in which the precursor of this scan was acquired
	 * 
	 * @param newValue
	 *            the new value to set
	 */
	public void setPrecursorScanNum(int newValue)
	{
		header.precursorScanNum = newValue;
	}

	/**
	 * Set the charge of the precursor of this scan
	 * 
	 * @param newValue
	 *            the new value to set.
	 */
	public void setPrecursorCharge(int newValue)
	{
		header.precursorCharge = newValue;
	}

	/**
	 * Set the collision energy used in this scan.
	 * 
	 * @param newValue
	 *            the new value to set.
	 */
	public void setCollisionEnergy(float newValue)
	{
		header.collisionEnergy = newValue;
	}

	/**
	 * Set the ionisation energy used in this scan.
	 * 
	 * @param newValue
	 *            the new value to set
	 */
	public void setIonisationEnergy(float newValue)
	{
		header.ionisationEnergy = newValue;
	}

	public void setPrecision(int newValue)
	{
		header.precision = newValue;
	}

	public void setPeakList(float[][] newValue)
	{
		throw new UnsupportedOperationException(
				"peakList is abandoned, use setMassIntensityList");
	}

	public void setFloatMassIntensityList(float[][] newValue)
	{
		floatMassIntensityList = newValue;
	}

    public void setDoubleMassIntensityList(double[][] newValue)
    {
		doubleMassIntensityList = newValue;
    }

    //mzXML_3.0
    public void setByteOrder(String newValue)
    {
	header.setByteOrder(newValue);
    }

    public void setContentType(String newValue)
    {
	header.setContentType(newValue);
    }

    public void setCompressionType(String newValue)
    {
	header.setCompressionType(newValue);
    }

    public void setCompressedLen(int newValue)
    {
	header.setCompressedLen(newValue);
	}

	//
	// Getter methods
	//

	/**
	 * @return the number of this scan in the mzXML file
	 */
	public int getNum()
	{
		return (header.num);
	}

	/**
	 * 
	 * @return the MS level of this scan
	 */
	public int getMsLevel()
	{
		return (header.msLevel);
	}

	/**
	 * 
	 * @return the number of peaks in this Scan
	 */
	public int getPeaksCount()
	{
		return (header.peaksCount);
	}

	/**
	 * 
	 * @return the polarity of this scan
	 */
	public String getPolarity()
	{
		return (header.polarity);
	}

	/**
	 * 
	 * @return the type of this scan
	 */
	public String getScanType()
	{
		return (header.scanType);
	}

	/**
	 * 
	 * @return 1 if the scan was centroided, 0 if it was not
	 */
	public int getCentroided()
	{
		return (header.centroided);
	}

	/**
	 * 
	 * @return 1 if the scan was deisotoped, 0 if it was not
	 */
	public int getDeisotoped()
	{
		return (header.deisotoped);
	}

	/**
	 * 
	 * @return 1 if the scan was charge deconvoluted, 0 if it was not
	 */
	public int getChargeDeconvoluted()
	{
		return (header.chargeDeconvoluted);
	}

	/**
	 * 
	 * @return the retention time at which this scan was acquired.
	 */
	public String getRetentionTime()
	{
		return (header.retentionTime);
	}

	public float getStartMz()
	{
		return (header.startMz);
	}

	public float getEndMz()
	{
		return (header.endMz);
	}

	public float getLowMz()
	{
		return (header.lowMz);
	}

	public float getHighMz()
	{
		return (header.highMz);
	}

	public float getBasePeakMz()
	{
		return (header.basePeakMz);
	}

	public float getBasePeakIntensity()
	{
		return (header.basePeakIntensity);
	}

	public float getTotIonCurrent()
	{
		return (header.totIonCurrent);
	}

	public float getPrecursorMz()
	{
		return (header.precursorMz);
	}

	public int getPrecursorScanNum()
	{
		return (header.precursorScanNum);
	}

	public int getPrecursorCharge()
	{
		return (header.precursorCharge);
	}

	public float getCollisionEnergy()
	{
		return (header.collisionEnergy);
	}

	public float getIonisationEnergy()
	{
		return (header.ionisationEnergy);
	}

	public int getPrecision()
	{
		return (header.precision);
	}
	
	
    //mzXML_3.0
    public String getByteOrder()
    {
	return (header.getByteOrder());
    }

    public String getContentType()
    {
	return (header.getContentType());
    }

    public String getCompressionType()
    {
	return (header.getCompressionType());
    }

    public int getCompressedLen()
    {
	return (header.getCompressedLen());
    }

    /**
     * In an older version of jrap this function returned a 
     * float [peaksCount][2] of the scan. For performance reasons, this
     * was converted to a float [2][peaksCount], and this is returned
     * by getMassIntensityList. This method will throw and UnsupportedOperationException
     */
	public float[][] getPeakList()
	{
		//	return (peakList);
		throw new UnsupportedOperationException(
				"getPeakList is not supported anymore. Use getMassIntensityList.");
	}

	/**
	 * Return the peaks in this scan as two lists: one of mass values and one of
	 * intensity values. <BR>
	 * Peak 0 mass = list[0][0], peak 0 intensity = list[1][0] <BR>
	 * Peak 1 mass = list[0][1], peak 1 intensity = list[1][1] <BR>
	 * Peak 2 mass = list[0][2], peak 2 intensity = list[1][2] <BR>
	 * etc.
	 * 
	 * @return The list of mass values and intensity values.
	 */

	public float[][] getMassIntensityList()
	{
        if (floatMassIntensityList != null)
        {
            return floatMassIntensityList;
        }
        if (doubleMassIntensityList != null)
        { // downcast to float
            float[][] tmpMassIntensityList = new float[2][doubleMassIntensityList[0].length];
   			for (int i = 0; i < doubleMassIntensityList[0].length; i++)
            {
               tmpMassIntensityList[0][i] = (float) doubleMassIntensityList[0][i];
               tmpMassIntensityList[1][i] = (float) doubleMassIntensityList[1][i];
            }
            return tmpMassIntensityList;
        }
        return null;
    }

    public double[][] getDoubleMassIntensityList()
    {
        if (doubleMassIntensityList != null)
        {
            return doubleMassIntensityList;
        }
        if (floatMassIntensityList != null)
        { // upcast to double
            double[][] tmpMassIntensityList = new double[2][floatMassIntensityList[0].length];
   			for (int i = 0; i < floatMassIntensityList[0].length; i++)
            {
               tmpMassIntensityList[0][i] = (double) floatMassIntensityList[0][i];
               tmpMassIntensityList[1][i] = (double) floatMassIntensityList[1][i];
            }
            return tmpMassIntensityList;
        }
        return null;
    }

	/**
	 * String respresentation of a Scan object.
	 * 
	 * Note: This is most likely not an optimal way to build the string.
	 * Hopefully this method will only be used for testing.
	 */
	public String toString()
	{
		StringBuffer tmpStrBuffer = new StringBuffer(1000);
		tmpStrBuffer.append("SCAN\n");
		tmpStrBuffer.append("====\n");
		tmpStrBuffer.append("num = " + header.num + "\n");
		tmpStrBuffer.append("msLevel = " + header.msLevel + "\n");
		tmpStrBuffer.append("peaksCount = " + header.peaksCount + "\n");
		tmpStrBuffer.append("polarity = " + header.polarity + "\n");
		tmpStrBuffer.append("scanType = " + header.scanType + "\n");
		tmpStrBuffer.append("centroided = " + header.centroided + "\n");
		tmpStrBuffer.append("deisotoped = " + header.deisotoped + "\n");
		tmpStrBuffer.append("chargeDeconvoluted = " + header.chargeDeconvoluted
				+ "\n");
		tmpStrBuffer.append("retentionTime = " + header.retentionTime + "\n");
		tmpStrBuffer.append("startMz = " + header.startMz + "\n");
		tmpStrBuffer.append("endMz = " + header.endMz + "\n");
		tmpStrBuffer.append("lowMz = " + header.lowMz + "\n");
		tmpStrBuffer.append("highMz = " + header.highMz + "\n");
		tmpStrBuffer.append("basePeakMz = " + header.basePeakMz + "\n");
		tmpStrBuffer.append("basePeakIntensity = " + header.basePeakIntensity
				+ "\n");
		tmpStrBuffer.append("totIonCurrent = " + header.totIonCurrent + "\n");
		tmpStrBuffer.append("precursorMz = " + header.precursorMz + "\n");
		tmpStrBuffer.append("precursorScanNum = " + header.precursorScanNum
				+ "\n");
		tmpStrBuffer.append("precursorCharge = " + header.precursorCharge
				+ "\n");
		tmpStrBuffer.append("collisionEnergy = " + header.collisionEnergy
				+ "\n");
		tmpStrBuffer.append("ionisationEnergy = " + header.ionisationEnergy
				+ "\n");
		tmpStrBuffer.append("precision = " + header.precision + "\n");

		//mzXML_3.0
		tmpStrBuffer.append("byteOrder = " + header.byteOrder + "\n");
		tmpStrBuffer.append("contenType = " + header.contentType + "\n");
		tmpStrBuffer.append("compressionType = " + header.compressionType + "\n");
		tmpStrBuffer.append("compressedLen = " + header.compressedLen + "\n");

		tmpStrBuffer.append("peaks:\n");
		if(floatMassIntensityList != null)
		    {
			for (int i = 0; i < floatMassIntensityList[0].length; i++)
			    {
				tmpStrBuffer.append("    mass=" + floatMassIntensityList[0][i]
						    + " intensity=" + floatMassIntensityList[1][i] + "\n");
			    }
			
		    }
		else if(doubleMassIntensityList != null)
		    {
			for (int i = 0; i < doubleMassIntensityList[0].length; i++)
		{
				tmpStrBuffer.append("    mass=" + doubleMassIntensityList[0][i]
						    + " intensity=" + doubleMassIntensityList[1][i] + "\n");
		}
		
		    }

		return (tmpStrBuffer.toString());
	}

	public double getDoubleRetentionTime()
	{
		return header.getDoubleRetentionTime();
	}

	/**
	 * @return Returns the ScanHeader.
	 */
	public ScanHeader getHeader()
	{
		return header;
	}

	/**
	 * @param header
	 *            The ScanHeader to set.
	 */
	public void setHeader(ScanHeader header)
	{
		this.header = header;
	}

}