package org.labkey.api.study;

import org.labkey.api.action.ReturnUrlForm;

public class EditDatasetRowForm extends ReturnUrlForm
{
    private String lsid;
    private int datasetId;

    public String getLsid()
    {
        return lsid;
    }

    public void setLsid(String lsid)
    {
        this.lsid = lsid;
    }

    public int getDatasetId()
    {
        return datasetId;
    }

    public void setDatasetId(int datasetId)
    {
        this.datasetId = datasetId;
    }
}
