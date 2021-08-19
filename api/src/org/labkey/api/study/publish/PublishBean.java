package org.labkey.api.study.publish;

import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

import java.util.List;
import java.util.Set;

public class PublishBean
{
    private final List<Integer> _ids;
    private final Set<Container> _studies;
    private final boolean _nullStudies;
    private final boolean _insufficientPermissions;
    private final String _dataRegionSelectionKey;
    private final ActionURL _returnURL;
    private final ActionURL _successURL;
    private final String _containerFilterName;
    private final List<Integer> _batchIds;
    private final String _batchNoun;
    private final boolean _autoLinkEnabled;

    public PublishBean(ActionURL successURL,
                       List<Integer> ids, String dataRegionSelectionKey,
                       Set<Container> studies, boolean nullStudies, boolean insufficientPermissions, ActionURL returnURL,
                       String containerFilterName, List<Integer> batchIds, String batchNoun, boolean autoLinkEnabled)
    {
        _successURL = successURL;
        _insufficientPermissions = insufficientPermissions;
        _studies = studies;
        _nullStudies = nullStudies;
        _ids = ids;
        _dataRegionSelectionKey = dataRegionSelectionKey;
        _returnURL = returnURL;
        _containerFilterName = containerFilterName;
        _batchIds = batchIds;
        _batchNoun = batchNoun;
        _autoLinkEnabled = autoLinkEnabled;
    }

    public ActionURL getSuccessURL()
    {
        return _successURL;
    }

    public ActionURL getReturnURL()
    {
        return _returnURL;
    }

    public List<Integer> getIds()
    {
        return _ids;
    }

    public String getDataRegionSelectionKey()
    {
        return _dataRegionSelectionKey;
    }

    public Set<Container> getStudies()
    {
        return _studies;
    }

    public boolean isNullStudies()
    {
        return _nullStudies;
    }

    public boolean isInsufficientPermissions()
    {
        return _insufficientPermissions;
    }

    public String getContainerFilterName()
    {
        return _containerFilterName;
    }

    public List<Integer> getBatchIds()
    {
        return _batchIds;
    }

    public String getBatchNoun()
    {
        return _batchNoun;
    }

    public Boolean isAutoLinkEnabled()
    {
        return _autoLinkEnabled;
    }
}
