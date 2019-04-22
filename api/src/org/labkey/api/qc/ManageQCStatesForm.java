package org.labkey.api.qc;

import org.labkey.api.action.ReturnUrlForm;

public class ManageQCStatesForm extends ReturnUrlForm
{
    private int[] _ids;
    private String[] _labels;
    private String[] _descriptions;
    private int[] _publicData;
    private String _newLabel;
    private String _newDescription;
    private boolean _newPublicData;
    private boolean _reshowPage;
    private Integer _defaultPipelineQCState;
    private Integer _defaultAssayQCState;
    private Integer _defaultDirectEntryQCState;
    private boolean _showPrivateDataByDefault;
    private boolean _blankQCStatePublic;
    private String _returnUrl;

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

    public String getNewLabel()
    {
        return _newLabel;
    }

    public void setNewLabel(String newLabel)
    {
        _newLabel = newLabel;
    }

    public String getNewDescription()
    {
        return _newDescription;
    }

    public void setNewDescription(String newDescription)
    {
        _newDescription = newDescription;
    }

    public boolean isNewPublicData()
    {
        return _newPublicData;
    }

    public void setNewPublicData(boolean newPublicData)
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

    public Integer getDefaultPipelineQCState()
    {
        return _defaultPipelineQCState;
    }

    public void setDefaultPipelineQCState(Integer defaultPipelineQCState)
    {
        _defaultPipelineQCState = defaultPipelineQCState;
    }

    public Integer getDefaultAssayQCState()
    {
        return _defaultAssayQCState;
    }

    public void setDefaultAssayQCState(Integer defaultAssayQCState)
    {
        _defaultAssayQCState = defaultAssayQCState;
    }

    public Integer getDefaultDirectEntryQCState()
    {
        return _defaultDirectEntryQCState;
    }

    public void setDefaultDirectEntryQCState(Integer defaultDirectEntryQCState)
    {
        _defaultDirectEntryQCState = defaultDirectEntryQCState;
    }

    public boolean isShowPrivateDataByDefault()
    {
        return _showPrivateDataByDefault;
    }

    public void setShowPrivateDataByDefault(boolean showPrivateDataByDefault)
    {
        _showPrivateDataByDefault = showPrivateDataByDefault;
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
