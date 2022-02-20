package org.labkey.specimen.actions;

import org.labkey.api.data.Container;
import org.labkey.specimen.SpecimenRequestManager;
import org.labkey.api.view.ViewContext;

import java.sql.SQLException;

public class ManageRequestInputsBean
{
    private final SpecimenRequestManager.SpecimenRequestInput[] _inputs;
    private final Container _container;
    private final String _contextPath;

    public ManageRequestInputsBean(ViewContext context) throws SQLException
    {
        _container = context.getContainer();
        _inputs = SpecimenRequestManager.get().getNewSpecimenRequestInputs(_container);
        _contextPath = context.getContextPath();
    }

    public SpecimenRequestManager.SpecimenRequestInput[] getInputs()
    {
        return _inputs;
    }

    public Container getContainer()
    {
        return _container;
    }

    public String getContextPath()
    {
        return _contextPath;
    }
}
