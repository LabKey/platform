package org.labkey.api.ehr.buttons;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ehr.dataentry.DataEntryForm;
import org.labkey.api.ehr.security.EHRRequestAdminPermission;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.ehr.security.EHRInProgressInsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.DataSetTable;

import java.util.Set;

/**
 * User: bimber
 * Date: 8/2/13
 * Time: 12:26 PM
 */
public class GoToTaskButton extends SimpleButtonConfigFactory
{
    private String _formType;

    public GoToTaskButton(Module owner, String formType)
    {
        super(owner, "Bulk Enter Data", "window.location = LABKEY.ActionURL.buildURL('ehr', 'dataEntryForm', null, {formtype: '" + formType +"'})");
        _formType = formType;
    }

    public boolean isAvailable(TableInfo ti)
    {
        if (ti instanceof DataSetTable)
        {
            Set<Class<? extends Permission>> perms = ((DataSetTable) ti).getDataSet().getPermissions(ti.getUserSchema().getUser());
            return perms.contains(EHRInProgressInsertPermission.class);
        }

        return ti.hasPermission(ti.getUserSchema().getUser(), EHRInProgressInsertPermission.class);
    }
}

