/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.StudyDefinitionController;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.Study;
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
    public CohortQueryView(User user, Study study, ViewContext viewContext, boolean allowEditing)
    {
        super(user, study, Cohort.class, viewContext, allowEditing);
        QuerySettings settings = getSettings();
        // We don't have many cohorts typically. Let's cut down on the number of buttons,
        // as this isn't a complex view
        settings.setAllowChooseView(false);
        settings.setShowRows(ShowRows.ALL);
        setShowPagination(false);
        setShowPaginationCount(false);
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (allowEditing())
        {
            ActionURL insertURL = new ActionURL(CohortController.InsertAction.class, getSchema().getContainer());
            ActionButton insertButton = new ActionButton(insertURL, "Insert New");
            insertButton.setActionType(ActionButton.Action.GET); // the target is a form handler, so we need to start with a GET
            bar.add(insertButton);

            ActionURL deleteUnusedURL = new ActionURL(CohortController.DeleteUnusedCohortsAction.class, getSchema().getContainer());
            bar.add(new ActionButton(deleteUnusedURL, "Delete Unused"));

            ActionURL editDefinitionURL = new ActionURL(StudyDefinitionController.EditCohortDefinitionAction.class, getSchema().getContainer());
            bar.add(new ActionButton(editDefinitionURL, "Edit Cohort Definition"));
        }
    }

    protected DataView createDataView()
    {
        DataView view = super.createDataView();
        if (allowEditing() &&
                getUser().isAdministrator() &&
                StudyManager.getInstance().showCohorts(getContainer(), getUser()))
        {
            TableInfo tableInfo = view.getDataRegion().getTable();
            ColumnInfo rowIdColumn = tableInfo.getColumn("rowId");
            view.getDataRegion().addDisplayColumn(0, new CohortEditColumn(view.getRenderContext().getContainer(), rowIdColumn));
            view.getDataRegion().addDisplayColumn(1, new CohortDeleteColumn(view.getRenderContext().getContainer(), rowIdColumn));
        }
        return view;
    }

    private class CohortEditColumn extends SimpleDisplayColumn
    {
        private final ColumnInfo rowIdColumn;
        private final Container container;

        public CohortEditColumn(Container container, ColumnInfo rowIdColumn)
        {
            this.container = container;
            this.rowIdColumn = rowIdColumn;
            setWidth(null);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            out.write("[<a href=\"");

            ActionURL actionURL = new ActionURL(CohortController.UpdateAction.class, container);

            String rowId = rowIdColumn.getValue(ctx).toString();
            actionURL.addParameter("rowId", rowId);

            out.write(PageFlowUtil.filter(actionURL.getLocalURIString()));
            out.write("\">");
            out.write("edit");
            out.write("</a>]");
        }
    }

    private class CohortDeleteColumn extends SimpleDisplayColumn
    {
        private final ColumnInfo rowIdColumn;
        private final Container container;

        public CohortDeleteColumn(Container container, ColumnInfo rowIdColumn)
        {
            this.container = container;
            this.rowIdColumn = rowIdColumn;
            setWidth(null);
            setTextAlign("center");
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Integer rowId = (Integer)rowIdColumn.getValue(ctx);
            boolean inUse = StudyManager.getInstance().getCohortForRowId(getContainer(), getUser(), rowId.intValue()).isInUse();

            if (!inUse)
            {
                out.write("[<a href=\"");

                ActionURL actionURL = new ActionURL(CohortController.DeleteCohortAction.class, container);

                actionURL.addParameter("rowId", rowId.toString());

                out.write(PageFlowUtil.filter(actionURL.getLocalURIString()));
                out.write("\">");
                out.write("delete");
                out.write("</a>]");
            }
            else
            {
                out.write("in use");
            }
        }
    }
}
