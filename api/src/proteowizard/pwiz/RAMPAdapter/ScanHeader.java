/*******************************************************************************
 * --------------------------------------------------------------------------- *
 * File: * @(#) ScanHeader.java *  
 ******************************************************************************/
package proteowizard.pwiz.RAMPAdapter;

import java.io.Serializable;

/**
 * ScanHeader is a class that contains all information
 * associated with a Scan, except for the actual peakList.
 * The separation between the peaklist and the other information
 * was made because parsing the peaklist costs a lot of time, and
 * in this way, programs can parse headers separately, and not parse
 * the peaklist when it's not needed.
 * 
 * @author B. Pratt
 */
public class ScanHeader extends ScanHeaderStruct implements Serializable
{

    /**
	 * String respresentation of a ScanHeader object.
	 * 
	 * Note: This is most likely not an optimal way to build the string.
	 * Hopefully this method will only be used for testing.
	 */
	public String toString()
	{
		StringBuffer tmpStrBuffer = new StringBuffer(1000);
		tmpStrBuffer.append("SCANHEADER\n");
		tmpStrBuffer.append("==========\n");
		tmpStrBuffer.append("num = " + getSeqNum() + "\n");
		tmpStrBuffer.append("msLevel = " + getMsLevel() + "\n");
		tmpStrBuffer.append("peaksCount = " + getPeaksCount() + "\n");
		tmpStrBuffer.append("scanType = " + getScanType() + "\n");
		tmpStrBuffer.append("retentionTime = " + getRetentionTime() + "\n");
		tmpStrBuffer.append("basePeakIntensity = " + getBasePeakIntensity() + "\n");
		tmpStrBuffer.append("totIonCurrent = " + getTotIonCurrent() + "\n");
		tmpStrBuffer.append("precursorScanNum = " + getPrecursorScanNum() + "\n");
		tmpStrBuffer.append("precursorCharge = " + getPrecursorCharge() + "\n");
		tmpStrBuffer.append("collisionEnergy = " + getCollisionEnergy() + "\n");
		tmpStrBuffer.append("ionisationEnergy = " + getIonisationEnergy() + "\n");

		return (tmpStrBuffer.toString());
	}	

}
