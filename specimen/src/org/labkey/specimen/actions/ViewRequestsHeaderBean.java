package org.labkey.specimen.actions;

import org.labkey.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.specimen.query.SpecimenRequestQueryView;
import org.labkey.api.view.ViewContext;

import java.util.List;

public class ViewRequestsHeaderBean
{
    public static final String PARAM_STATUSLABEL = "SpecimenRequest.Status/Label~eq";
    public static final String PARAM_CREATEDBY = "SpecimenRequest.CreatedBy/DisplayName~eq";

    private final SpecimenRequestQueryView _view;
    private final ViewContext _context;

    public ViewRequestsHeaderBean(ViewContext context, SpecimenRequestQueryView view)
    {
        _view = view;
        _context = context;
    }

    public SpecimenRequestQueryView getView()
    {
        return _view;
    }

    public List<SpecimenRequestStatus> getStauses()
    {
        return SpecimenRequestManager.get().getRequestStatuses(_context.getContainer(), _context.getUser());
    }

    public boolean isFilteredStatus(SpecimenRequestStatus status)
    {
        return status.getLabel().equals(_context.getActionURL().getParameter(PARAM_STATUSLABEL));
    }
}
