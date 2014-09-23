/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateColumn;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.study.controllers.StudyController;
import org.springframework.validation.Errors;

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
        bar.add(createInsertButton());
        bar.add(createDeleteButton());
        ActionURL deleteUnusedURL = new ActionURL(StudyController.DeleteAllUnusedLocationsAction.class, getSchema().getContainer());
        deleteUnusedURL.addReturnURL(getReturnURL());
        ActionButton delete = new ActionButton(deleteUnusedURL, "Delete All Unused");
        delete.setActionType(ActionButton.Action.LINK);
        delete.setRequiresSelection(false, "Are you sure you want to delete the selected location?", "Are you sure you want to delete the selected locations?");
        deleteUnusedURL.addReturnURL(getViewContext().getActionURL());
        bar.add(delete);
        bar.add(createExportButton(false));
        bar.add(createPrintButton());
        bar.add(createPageSizeMenuButton());
    }

    @Override
    protected void addDetailsAndUpdateColumns(List<DisplayColumn> ret, TableInfo table)
    {
        StringExpression urlDetails = urlExpr(QueryAction.detailsQueryRow);

        if (urlDetails != null && urlDetails != AbstractTableInfo.LINK_DISABLER)
        {
            ret.add(new DetailsColumn(urlDetails, table));
        }

        StringExpression urlUpdate = urlExpr(QueryAction.updateQueryRow);

        if (urlUpdate != null)
        {
            ret.add(0, new UpdateColumn(urlUpdate));
        }
    }
}
