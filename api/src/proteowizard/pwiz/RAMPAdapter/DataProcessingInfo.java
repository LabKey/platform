/*******************************************************************************
 * --------------------------------------------------------------------------- *
 * File: * @(#) DataProcessingInfo.java * Author: * Mathijs Vogelzang
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
package proteowizard.pwiz.RAMPAdapter;

/**
 * DataProcessingInfo contains information about what settings and software were
 * used to process the data contained in a pwiz-supported mass spec file.
 * 
 * @author B. Pratt
 */
public class DataProcessingInfo
{
	public static final int UNKNOWN = -1, YES = 1, NO = 0;

	protected double intensityCutoff;
	protected int centroided, deisotoped, chargeDeconvoluted, spotIntegration;

	protected SoftwareInfo[] softwareUsed;

	public DataProcessingInfo() {
		centroided = UNKNOWN;
		deisotoped = UNKNOWN;
		chargeDeconvoluted = UNKNOWN;
		spotIntegration = UNKNOWN;

		intensityCutoff = -1;
	}

	/**
	 * Was the data centroided?
	 * 
	 * @return UNKNOWN, YES or NO.
	 */
	public int getCentroided()
	{
		return centroided;
	}

	/**
	 * Set centroided to one of UNKNOWN, YES or NO.
	 * 
	 * @param centroided
	 *            The value to set.
	 */
	public void setCentroided(int centroided)
	{
		this.centroided = centroided;
	}

	/**
	 * Was the data charge deconvoluted?
	 * 
	 * @return UNKNOWN, YES or NO.
	 */
	public int getChargeDeconvoluted()
	{
		return chargeDeconvoluted;
	}

	/**
	 * Set charge deconvoluted to one of UNKNOWN, YES or NO.
	 * 
	 * @param chargeDeconvoluted
	 *            The value to set.
	 */	
	public void setChargeDeconvoluted(int chargeDeconvoluted)
	{
		this.chargeDeconvoluted = chargeDeconvoluted;
	}

	/**
	 * Was the data charge deisotoped?
	 * 
	 * @return UNKNOWN, YES or NO.
	 */
	public int getDeisotoped()
	{
		return deisotoped;
	}

	/**
	 * Set deisotoped to one of UNKNOWN, YES or NO.
	 * 
	 * @param deisotoped
	 *            The value to set.
	 */	
	public void setDeisotoped(int deisotoped)
	{
		this.deisotoped = deisotoped;
	}

	/**
	 * Return the intensity cutoff that was used to eliminate
	 * low-signal peaks.
	 * 
	 * A negative value means the cutoff is not known.
	 * 
	 * @return Returns the intensityCutoff, or a negative value
	 * when the cutoff is not known.
	 */
	public double getIntensityCutoff()
	{
		return intensityCutoff;
	}

	/**
	 * Set the intensity cutoff that was used to eliminate
	 * low-signal peaks.
	 * 
	 * A negative value means the cutoff is not known.
	 * 
	 * @param intensityCutoff
	 *            The intensityCutoff to set.
	 */
	public void setIntensityCutoff(double intensityCutoff)
	{
		this.intensityCutoff = intensityCutoff;
	}

	/**
	 * Return an array of information about all software
	 * that was used to process the data, in chronological 
	 * order. 
	 * 
	 * @return An array of information about software
	 */
	public SoftwareInfo[] getSoftwareUsed()
	{
		return softwareUsed;
	}

	/**
	 * Set the chronological array of used software for
	 * data processing.
	 * 
	 * @param softwareUsed
	 *            The array of info about software.
	 */
	public void setSoftwareUsed(SoftwareInfo[] softwareUsed)
	{
		this.softwareUsed = softwareUsed;
	}

	/**
	 * Were spots integrated?
	 * 
	 * @return UNKNOWN, YES or NO.
	 */
	public int getSpotIntegration()
	{
		return spotIntegration;
	}

	/**
	 * Set spot integration to one of UNKNOWN, YES or NO.
	 * 
	 * @param spotIntegration
	 *            The value to set.
	 */		
	public void setSpotIntegration(int spotIntegration)
	{
		this.spotIntegration = spotIntegration;
	}
}