package org.labkey.api.laboratory.button;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ehr.security.EHRCompletedUpdatePermission;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;

/**
 * User: bimber
 * Date: 9/8/13
 * Time: 10:18 AM
 */
public class ChangeAssayResultStatusBtn extends SimpleButtonConfigFactory
{
    public ChangeAssayResultStatusBtn(Module owner)
    {
        this(owner, "Change Result Status");
    }

    public ChangeAssayResultStatusBtn(Module owner, String label)
    {
        super(owner, label, "Laboratory.window.ChangeAssayResultStatusWindow.buttonHandler(dataRegionName);");
        setClientDependencies(ClientDependency.fromModuleName("laboratory"), ClientDependency.fromFilePath("laboratory/window/ChangeAssayResultStatusWindow.js"));
    }

    public boolean isAvailable(TableInfo ti)
    {
        return super.isAvailable(ti) && ti.hasPermission(ti.getUserSchema().getUser(), UpdatePermission.class);
    }
}
