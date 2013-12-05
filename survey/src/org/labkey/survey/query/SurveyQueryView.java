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
 * User: cnathe
 * Date: 12/12/12
 */
public class SurveyQueryView extends QueryView
{
    public static final String DATA_REGION = "Survey";
    private Integer _surveyDesignId;

    public SurveyQueryView(UserSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);

        if (settings instanceof SurveyQuerySettings)
            _surveyDesignId = ((SurveyQuerySettings) settings).getSurveyDesignId();

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

        // add the survey design Id for the given view (passed through via the SurveyQuerySettings
        if (_surveyDesignId != null && getContainer().hasPermission(getUser(), InsertPermission.class))
        {
            ActionURL insertURL = new ActionURL(SurveyController.UpdateSurveyAction.class, getContainer());
            insertURL.addParameter("surveyDesignId", _surveyDesignId);
            insertURL.addReturnURL(getReturnURL());

            ActionButton insert = new ActionButton(insertURL, "Create Survey");
            insert.setActionType(ActionButton.Action.LINK);
            bar.add(insert);
        }
    }
}
