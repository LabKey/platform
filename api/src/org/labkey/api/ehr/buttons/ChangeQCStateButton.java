package org.labkey.api.ehr.buttons;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ehr.dataentry.DataEntryForm;
import org.labkey.api.ehr.security.EHRRequestAdminPermission;
import org.labkey.api.ehr.security.EHRScheduledInsertPermission;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.DataSetTable;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

/**
 * User: bimber
 * Date: 8/2/13
 * Time: 12:26 PM
 */
public class ChangeQCStateButton extends SimpleButtonConfigFactory
{
    public ChangeQCStateButton(Module owner, @Nullable String jsClass, @Nullable Set<ClientDependency> clientDependencies)
    {
        this(owner, "Change Request Status", jsClass, clientDependencies);
    }

    public ChangeQCStateButton(Module owner, String text, @Nullable String jsClass, @Nullable Set<ClientDependency> clientDependencies)
    {
        super(owner, text, "EHR.DatasetButtons.changeQCStateHandler(dataRegionName, '" + jsClass + "');");

        if (clientDependencies != null)
        {
            setClientDependencies(clientDependencies);
        }
    }

    public boolean isAvailable(TableInfo ti)
    {
        if (ti instanceof DataSetTable)
        {
            Set<Class<? extends Permission>> perms = ((DataSetTable) ti).getDataSet().getPermissions(ti.getUserSchema().getUser());
            return perms.contains(EHRRequestAdminPermission.class);
        }

        return ti.hasPermission(ti.getUserSchema().getUser(), EHRRequestAdminPermission.class);
    }
}

