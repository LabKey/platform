package org.labkey.api.qc;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Oct 9, 2011
 * Time: 2:07:48 PM
 */
public class DataLoaderSettings
{
    private boolean _bestEffortConversion;      // if conversion fails, the original field value is returned

    public boolean isBestEffortConversion()
    {
        return _bestEffortConversion;
    }

    public void setBestEffortConversion(boolean bestEffortConversion)
    {
        _bestEffortConversion = bestEffortConversion;
    }
}
