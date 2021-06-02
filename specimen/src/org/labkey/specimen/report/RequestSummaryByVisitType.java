package org.labkey.specimen.report;

public class RequestSummaryByVisitType extends SummaryByVisitType
{
    private Integer _destinationSiteId;
    private String _siteLabel;

    public Integer getDestinationSiteId()
    {
        return _destinationSiteId;
    }

    public void setDestinationSiteId(Integer destinationSiteId)
    {
        _destinationSiteId = destinationSiteId;
    }

    public String getSiteLabel()
    {
        return _siteLabel;
    }

    public void setSiteLabel(String siteLabel)
    {
        _siteLabel = siteLabel;
    }
}
