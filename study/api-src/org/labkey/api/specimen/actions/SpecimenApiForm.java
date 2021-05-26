package org.labkey.api.specimen.actions;

import org.labkey.api.action.HasViewContext;
import org.labkey.api.view.ViewContext;

public class SpecimenApiForm implements HasViewContext
{
    private ViewContext _viewContext;

    @Override
    public ViewContext getViewContext()
    {
        return _viewContext;
    }

    @Override
    public void setViewContext(ViewContext viewContext)
    {
        _viewContext = viewContext;
    }
}
