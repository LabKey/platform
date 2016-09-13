package org.labkey.api.assay.dilution;

/**
 * Refactored out of class SampleInfo, which was no longer used other than this enum.
 *
 * Created by adam on 9/13/2016.
 */
public enum SampleInfoMethod
{
    Concentration
    {
        public String getAbbreviation()
        {
            return "Conc.";
        }
    },
    Dilution
    {
        public String getAbbreviation()
        {
            return "Dilution";
        }
    };

    public abstract String getAbbreviation();
}
