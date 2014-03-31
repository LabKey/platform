package org.labkey.api.ehr.buttons;

import org.labkey.api.ldk.buttons.ShowEditUIButton;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.template.ClientDependency;

/**

 */
public class EHRShowEditUIButton extends ShowEditUIButton
{
    public EHRShowEditUIButton(Module owner, String schemaName, String queryName, Class<? extends Permission>... perms)
    {
        super(owner, schemaName, queryName, perms);
        setClientDependencies(ClientDependency.fromModuleName("ehr"));
    }

    @Override
    protected String getHandlerName()
    {
        return "EHR.Utils.editUIButtonHandler";
    }
}
