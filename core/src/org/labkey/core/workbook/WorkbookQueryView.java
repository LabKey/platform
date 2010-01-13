package org.labkey.core.workbook;

import org.labkey.api.query.QueryView;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ActionURL;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.CoreController;
import org.labkey.core.admin.AdminController;

/**
 * Created by IntelliJ IDEA.
 * User: labkey
 * Date: Jan 5, 2010
 * Time: 5:09:55 PM
 */
public class WorkbookQueryView extends QueryView
{
    public WorkbookQueryView(ViewContext ctx, CoreQuerySchema schema)
    {
        super(schema);

        QuerySettings settings = new QuerySettings(ctx, QueryView.DATAREGIONNAME_DEFAULT);
        settings.setSchemaName(schema.getSchemaName());
        settings.setQueryName(CoreQuerySchema.WORKBOOKS_TABLE_NAME);
        settings.setAllowChooseQuery(false);
        settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());
        setSettings(settings);

        setShadeAlternatingRows(true);
        setShowDeleteButton(true);
        setShowRecordSelectors(true);
        setFrame(FrameType.NONE);
    }

    @Override
    public DataView createDataView()
    {
        DataView view = super.createDataView();
        DataRegion region = view.getDataRegion();
        if(region.getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
        {
            ButtonBar bar = region.getButtonBar(DataRegion.MODE_GRID);
            if (null != bar)
            {
                ActionButton btn = null;

                btn = new ActionButton(new ActionURL(CoreController.MoveWorkbooksAction.class, getContainer()), "Move");
                btn.setActionType(ActionButton.Action.POST);
                btn.setRequiresSelection(true);
                btn.setDisplayPermission(AdminPermission.class);
                bar.add(btn);

                btn = new ActionButton(new ActionURL(CoreController.CreateWorkbookAction.class, getContainer()), "Create New Workbook");
                btn.setActionType(ActionButton.Action.LINK);
                btn.setDisplayPermission(InsertPermission.class);
                bar.add(btn);

                btn = new ActionButton(new ActionURL(CoreController.ManageWorkbooksAction.class, getContainer()), "Manage Workbooks");
                btn.setActionType(ActionButton.Action.LINK);
                btn.setDisplayPermission(AdminPermission.class);
                bar.add(btn);
            }
        }
        return view;
    }
}
