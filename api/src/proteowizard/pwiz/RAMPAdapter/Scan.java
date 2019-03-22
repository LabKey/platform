/*******************************************************************************
 * --------------------------------------------------------------------------- *
 * File: * @(#) Scan.java * Author: * B. Pratt
 * 
 * pwiz-backed JRAP workalike
 *  
 ******************************************************************************/
package proteowizard.pwiz.RAMPAdapter;

import java.io.Serializable;

/**
 * A simple class to hold the contents of a scan from a pwiz-supported mass spec file.
 * 
 * @author B. Pratt
 *  
 */
public final class Scan implements Serializable
{
    /**
     * peak count, mslevel, etc
     */
    public ScanHeaderStruct hdr;

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
		hdr = new ScanHeaderStruct();
	}

    public Scan(pwiz_RAMPAdapter parser,long scanIndex)
    {
        this();
        parser.getScanHeader(scanIndex, hdr);
    }
   
	//
	// Getter methods
	//

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

    public void setMassIntensityList(vectord data)
    {
            floatMassIntensityList = new float[2][(int)data.size()/2];
   			for (int i = 0; i < (int)data.size()/2; i++)
            {
               floatMassIntensityList[0][i] = (float) data.get(i*2);
               floatMassIntensityList[1][i] = (float) data.get(1+(i*2));
            }
    }

	/**
	 * String representation of a Scan object.
	 * 
	 * Note: This is most likely not an optimal way to build the string.
	 * Hopefully this method will only be used for testing.
	 */
	public String toString()
	{
		StringBuffer tmpStrBuffer = new StringBuffer(1000);
		tmpStrBuffer.append("SCAN\n");
		tmpStrBuffer.append("====\n");
        tmpStrBuffer.append("num = " + hdr.getSeqNum() + "\n");
        tmpStrBuffer.append("msLevel = " + hdr.getMsLevel() + "\n");
        tmpStrBuffer.append("peaksCount = " + hdr.getPeaksCount() + "\n");
        tmpStrBuffer.append("scanType = " + hdr.getScanType() + "\n");
        tmpStrBuffer.append("retentionTime = " + hdr.getRetentionTime() + "\n");
        tmpStrBuffer.append("basePeakIntensity = " + hdr.getBasePeakIntensity() + "\n");
        tmpStrBuffer.append("totIonCurrent = " + hdr.getTotIonCurrent() + "\n");
        tmpStrBuffer.append("precursorScanNum = " + hdr.getPrecursorScanNum() + "\n");
        tmpStrBuffer.append("precursorCharge = " + hdr.getPrecursorCharge() + "\n");
        tmpStrBuffer.append("collisionEnergy = " + hdr.getCollisionEnergy() + "\n");
        tmpStrBuffer.append("ionisationEnergy = " + hdr.getIonisationEnergy() + "\n");


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


}