package org.labkey.api.qc;

import org.labkey.api.action.ReturnUrlForm;

public class AbstractManageQCStatesForm extends ReturnUrlForm
{
    private int[] _ids;
    private String[] _labels;
    private String[] _descriptions;
    private int[] _publicData;
    private int[] _newIds;
    private String[] _newLabels;
    private String[] _newDescriptions;
    private int[] _newPublicData;
    private boolean _reshowPage;
    private boolean _blankQCStatePublic;

    public int[] getIds()
    {
        return _ids;
    }

    public void setIds(int[] ids)
    {
        _ids = ids;
    }

    public String[] getLabels()
    {
        return _labels;
    }

    public void setLabels(String[] labels)
    {
        _labels = labels;
    }

    public String[] getDescriptions()
    {
        return _descriptions;
    }

    public void setDescriptions(String[] descriptions)
    {
        _descriptions = descriptions;
    }

    public int[] getPublicData()
    {
        return _publicData;
    }

    public void setPublicData(int[] publicData)
    {
        _publicData = publicData;
    }

    public int[] getNewIds()
    {
        return _newIds;
    }

    public void setNewIds(int[] newIds)
    {
        _newIds = newIds;
    }

    public String[] getNewLabels()
    {
        return _newLabels;
    }

    public void setNewLabels(String[] newLabels)
    {
        _newLabels = newLabels;
    }

    public String[] getNewDescriptions()
    {
        return _newDescriptions;
    }

    public void setNewDescriptions(String[] newDescriptions)
    {
        _newDescriptions = newDescriptions;
    }

    public int[] getNewPublicData()
    {
        return _newPublicData;
    }

    public void setNewPublicData(int[] newPublicData)
    {
        _newPublicData = newPublicData;
    }

    public boolean isReshowPage()
    {
        return _reshowPage;
    }

    public void setReshowPage(boolean reshowPage)
    {
        _reshowPage = reshowPage;
    }

    public boolean isBlankQCStatePublic()
    {
        return _blankQCStatePublic;
    }

    public void setBlankQCStatePublic(boolean blankQCStatePublic)
    {
        _blankQCStatePublic = blankQCStatePublic;
    }
}
