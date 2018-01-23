/*
 * Copyright (c) 2010-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.workbook;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.core.CoreController;
import org.labkey.core.query.CoreQuerySchema;

public class WorkbookQueryView extends QueryView
{
    public WorkbookQueryView(ViewContext ctx, UserSchema schema)
    {
        super(schema);

        QuerySettings settings = schema.getSettings(ctx, QueryView.DATAREGIONNAME_DEFAULT, CoreQuerySchema.WORKBOOKS_TABLE_NAME);
        settings.setBaseSort(new Sort("ID"));
        //settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());
        setSettings(settings);

        setShadeAlternatingRows(true);
        setShowBorders(true);
        setShowInsertNewButton(true);
        setShowImportDataButton(false);
        setShowDeleteButton(true);
        setFrame(FrameType.NONE);
    }

    @Override
    public DataView createDataView()
    {
        DataView view = super.createDataView();
        DataRegion region = view.getDataRegion();
        if (region.getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
        {
            ButtonBar bar = region.getButtonBar(DataRegion.MODE_GRID);
            if (null != bar)
            {
                ActionButton btn = null;

                btn = new ActionButton(new ActionURL(CoreController.MoveWorkbooksAction.class, getContainer()), "Move");
                btn.setActionType(ActionButton.Action.POST);
                btn.setIconCls("share");
                btn.setRequiresSelection(true);
                btn.setDisplayPermission(AdminPermission.class);
                bar.add(btn);
            }
        }
        return view;
    }

    @Override
    public ActionButton createInsertButton()
    {
        final ActionButton insertButton = super.createInsertButton();
        insertButton.setCaption("Create Workbook");
        insertButton.setIconCls("plus");
        return insertButton;
    }
}
