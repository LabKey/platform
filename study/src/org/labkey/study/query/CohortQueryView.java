/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.study.query;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.StudyDefinitionController;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jgarms
 * Date: Jul 25, 2008
 * Time: 10:44:34 AM
 */
public class CohortQueryView extends ExtensibleObjectQueryView
{
    boolean canEditDelete = false;

    public CohortQueryView(User user, StudyImpl study, ViewContext viewContext)
    {
        super(user, study, CohortImpl.DOMAIN_INFO, viewContext, true);

        getSettings().setAllowChooseView(false);
        setShowPagination(false);
        setShowPaginationCount(false);

        canEditDelete = getContainer().hasPermission(user, AdminPermission.class) && StudyManager.getInstance().showCohorts(getContainer(), getUser());
        setShowUpdateColumn(canEditDelete);
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (allowEditing() && canEditDelete)
        {
            ActionURL insertURL = new ActionURL(CohortController.InsertAction.class, getSchema().getContainer());
            ActionButton insertButton = new ActionButton(insertURL, getInsertButtonText(INSERT_ROW_TEXT));
            insertButton.setActionType(ActionButton.Action.GET); // the target is a form handler, so we need to start with a GET
            insertButton.setIconCls("plus");
            bar.add(insertButton);

            ActionURL deleteUnusedURL = new ActionURL(CohortController.DeleteUnusedCohortsAction.class, getSchema().getContainer());
            bar.add(new ActionButton(deleteUnusedURL, "Delete Unused"));

            ActionURL editDefinitionURL = new ActionURL(StudyDefinitionController.EditCohortDefinitionAction.class, getSchema().getContainer());
            bar.add(new ActionButton(editDefinitionURL, "Edit Cohort Definition"));
        }
    }

    @Override
    protected boolean canUpdate()
    {
        return canEditDelete;
    }

    @Override
    protected boolean canDelete()
    {
        return canEditDelete;
    }

    @Override
    public DataView createDataView()
    {
        DataView view = super.createDataView();

        if (canEditDelete)
        {
            TableInfo tableInfo = view.getDataRegion().getTable();
            ColumnInfo rowIdColumn = tableInfo.getColumn("rowId");
            ColumnInfo folderColumn = tableInfo.getColumn("Folder");
            view.getDataRegion().addDisplayColumn(1, new CohortDeleteColumn(rowIdColumn, folderColumn));
        }
        return view;
    }

    private class CohortDeleteColumn extends SimpleDisplayColumn
    {
        private final ColumnInfo rowIdColumn;
        private final ColumnInfo folderColumn;

        public CohortDeleteColumn(ColumnInfo rowIdColumn, ColumnInfo folderColumn)
        {
            this.rowIdColumn = rowIdColumn;
            this.folderColumn = folderColumn;
            setWidth(null);
            setTextAlign("center");
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Integer rowId = (Integer)rowIdColumn.getValue(ctx);
            Container folder = ContainerManager.getForId((String) folderColumn.getValue(ctx));

            if (folder != null)
            {
                CohortImpl cohort = StudyManager.getInstance().getCohortForRowId(folder, getUser(), rowId);
                if (cohort != null)
                {
                    if (!cohort.isInUse())
                    {
                        ActionURL actionURL = new ActionURL(CohortController.DeleteCohortAction.class, folder);
                        actionURL.addParameter("rowId", rowId.toString());

                        out.write(PageFlowUtil.textLink("delete", actionURL.getLocalURIString()));
                    }
                    else
                    {
                        out.write("in use");
                    }
                }
            }
        }
    }
}
