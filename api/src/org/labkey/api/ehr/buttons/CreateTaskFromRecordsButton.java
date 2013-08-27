package org.labkey.api.ehr.buttons;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.security.EHRInProgressInsertPermission;
import org.labkey.api.ehr.security.EHRScheduledInsertPermission;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.DataSetTable;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

/**
 * User: bimber
 * Date: 8/2/13
 * Time: 12:26 PM
 */
public class CreateTaskFromRecordsButton extends SimpleButtonConfigFactory
{
    public CreateTaskFromRecordsButton(Module owner, String btnLabel, String taskLabel, String formType)
    {
        super(owner, btnLabel, "EHR.DatasetButtons.createTaskFromRecordHandler(dataRegionName, '" + formType + "', '" + taskLabel + "')");
        setClientDependencies(ClientDependency.fromFilePath("ehr/window/CreateTaskFromRecordsWindow.js"));
    }

    public boolean isAvailable(TableInfo ti)
    {
        if (ti instanceof DataSetTable)
        {
            Set<Class<? extends Permission>> perms = ((DataSetTable) ti).getDataSet().getPermissions(ti.getUserSchema().getUser());
            return perms.contains(EHRScheduledInsertPermission.class);
        }

        return ti.hasPermission(ti.getUserSchema().getUser(), EHRScheduledInsertPermission.class);
    }
}

