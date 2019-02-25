package org.labkey.study.controllers.assay.actions;

import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.assay.AssayManager;
import org.springframework.validation.BindException;

@RequiresPermission(DeletePermission.class)
public class DeleteProtocolAction extends MutatingApiAction<GWTProtocol>
{
    @Override
    public Object execute(GWTProtocol protocol, BindException errors) throws Exception
    {
        ExpProtocol expProtocol = AssayManager.get().findExpProtocol(protocol, getContainer());
        if (expProtocol == null)
            throw new NotFoundException();

        // user must have both design assay AND delete permission, as this will delete both the design and uploaded data
        if (!expProtocol.getContainer().hasPermission(getUser(), DesignAssayPermission.class))
            throw new UnauthorizedException("You do not have sufficient permissions to delete this assay design.");

        expProtocol.delete(getUser());
        return success("Deleted assay protocol '" + protocol.getName() + "'");
    }
}
