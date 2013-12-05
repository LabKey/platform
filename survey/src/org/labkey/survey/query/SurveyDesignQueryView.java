/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.survey.query;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.survey.SurveyController;
import org.springframework.validation.BindException;

/**
 * User: klum
 * Date: 12/10/12
 */
public class SurveyDesignQueryView extends QueryView
{
    public SurveyDesignQueryView(UserSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);

        settings.setAllowChooseQuery(false);

        setShowDeleteButton(true);
        setShowInsertNewButton(false);
        setShowUpdateColumn(true);
        setShowExportButtons(false);
        setShowImportDataButton(false);
        setShowReports(false);
        setShowRecordSelectors(true);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (getContainer().hasPermission(getUser(), InsertPermission.class))
        {
            ActionURL insertURL = new ActionURL(SurveyController.SurveyDesignAction.class, getContainer());
            insertURL.addReturnURL(getReturnURL());

            ActionButton insert = new ActionButton(insertURL, "Create Survey Design");
            insert.setActionType(ActionButton.Action.LINK);

            bar.add(insert);
        }
    }

    @Override
    public ActionButton createDeleteButton()
    {
        ActionURL url = new ActionURL(SurveyController.DeleteSurveyDesignsAction.class, getContainer());
        url.addReturnURL(getReturnURL());

        ActionButton btnDelete = new ActionButton(url, "Delete");
        btnDelete.setActionType(ActionButton.Action.POST);
        btnDelete.setRequiresSelection(true, "Are you sure you want to delete this survey design and its associated surveys?", "Are you sure you want to delete these ${selectedCount} survey designs and their associated survey instances?");

        return btnDelete;
    }
}
