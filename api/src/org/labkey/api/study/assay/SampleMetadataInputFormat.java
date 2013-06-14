package org.labkey.api.study.assay;

/**
 * Enum to describe how sample metadata is captured during
 * data import.
 */
public enum SampleMetadataInputFormat
{
    MANUAL("Manual"),                               // form based manual entry
    FILE_BASED("File Upload (metadata only)"),           // metadata is provided from a file (separate from the run data file)
    COMBINED("Combined File Upload (metadata & run data)");     // metadata and run data are combined in a single file

    private String _label;

    SampleMetadataInputFormat(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }
}
