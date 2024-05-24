package org.labkey.specimen.actions;

import org.labkey.api.action.QueryViewAction;
import org.labkey.specimen.query.SpecimenQueryView;

public class SpecimenViewTypeForm extends QueryViewAction.QueryExportForm
{
    public enum PARAMS
    {
        showVials,
        viewMode
    }

    private boolean _showVials;
    private SpecimenQueryView.Mode _viewMode = SpecimenQueryView.Mode.DEFAULT;

    public boolean isShowVials()
    {
        return _showVials;
    }

    public void setShowVials(boolean showVials)
    {
        _showVials = showVials;
    }

    public String getViewMode()
    {
        return _viewMode.name();
    }

    public SpecimenQueryView.Mode getViewModeEnum()
    {
        return _viewMode;
    }

    public void setViewMode(String viewMode)
    {
        if (viewMode != null)
            _viewMode = SpecimenQueryView.Mode.valueOf(viewMode);
    }
}
