/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateColumn;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.study.controllers.StudyController;
import org.springframework.validation.Errors;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Created by Joe on 9/8/2014.
 */
public class LocationQueryView extends QueryView
{
    public LocationQueryView(UserSchema schema, QuerySettings settings, @Nullable Errors errors)
    {
        super(schema, settings, errors);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        bar.add(createViewButton(getViewItemFilter()));
        bar.add(createReportButton());

        // Only admins can insert/delete locations
        if (getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            bar.add(createInsertButton());
            bar.add(createDeleteButton());
            ActionURL deleteUnusedURL = new ActionURL(StudyController.DeleteAllUnusedLocationsAction.class, getSchema().getContainer());
            deleteUnusedURL.addReturnURL(getReturnURL());
            ContainerFilter cFilter = getContainerFilter();
            if (null != cFilter)
            {
                ContainerFilter.Type type = cFilter.getType();

                if (null != type)
                    deleteUnusedURL.addParameter("containerFilter", type.name());
            }
            ActionButton deleteAllUnused = new ActionButton(deleteUnusedURL, "Delete All Unused");
            deleteAllUnused.setActionType(ActionButton.Action.LINK);
            deleteAllUnused.setRequiresSelection(false, "Are you sure you want to delete the selected location?", "Are you sure you want to delete the selected locations?");
            deleteUnusedURL.addReturnURL(getViewContext().getActionURL());
            bar.add(deleteAllUnused);
        }

        List<String> recordSelectorColumns = view.getDataRegion().getRecordSelectorValueColumns();
        bar.add(createExportButton(recordSelectorColumns));

        ActionButton b = createPrintButton();
        if (null != b)
            bar.add(createPrintButton());
    }

    @Override
    protected void addDetailsAndUpdateColumns(List<DisplayColumn> ret, TableInfo table)
    {
        StringExpression urlDetails = urlExpr(QueryAction.detailsQueryRow);

        if (urlDetails != null && urlDetails != AbstractTableInfo.LINK_DISABLER)
        {
            ret.add(new DetailsColumn(urlDetails, table));
        }

        // Only admins can update locations
        if (getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            StringExpression urlUpdate = urlExpr(QueryAction.updateQueryRow);

            if (urlUpdate != null)
            {
                UpdateColumn update = new UpdateColumn(urlUpdate) {
                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Container c = ContainerManager.getForId((String)ctx.get("container"));
                        if (c.hasPermission(getUser(), AdminPermission.class))
                            super.renderGridCellContents(ctx, out);
                        else
                            out.write("&nbsp;");
                    }
                };
                ret.add(0, update);
            }
        }
    }
}
