package org.labkey.api.study;

/**
 * Created by cnathe on 11/21/14.
 */
public enum StudySnapshotType
{
    ancillary("Ancillary", "Create Ancillary Study"),
    publish("Published", "Publish Study"),
    specimen("Specimen", "Publish Specimen Study");

    private String _title;
    private String _jobDescription;

    private StudySnapshotType(String title, String jobDescription)
    {
        _title = title;
        _jobDescription = jobDescription;
    }

    public String getTitle()
    {
        return _title;
    }
    public String getJobDescription()
    {
        return _jobDescription;
    }
}
