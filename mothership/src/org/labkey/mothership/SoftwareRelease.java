package org.labkey.mothership;

/**
 * User: jeckels
 * Date: Aug 29, 2006
 */
public class SoftwareRelease
{
    private int _releaseId;
    private int _SVNRevision;
    private String _description;
    private String _container;

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public int getSVNRevision()
    {
        return _SVNRevision;
    }

    public void setSVNRevision(int SVNRevision)
    {
        _SVNRevision = SVNRevision;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public int getReleaseId()
    {
        return _releaseId;
    }

    public void setReleaseId(int releaseId)
    {
        _releaseId = releaseId;
    }
}
