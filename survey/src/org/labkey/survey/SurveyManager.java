/*
 * Copyright (c) 2012 LabKey Corporation
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

package org.labkey.survey;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.survey.model.SurveyDesign;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SurveyManager
{
    private static final SurveyManager _instance = new SurveyManager();

    private SurveyManager()
    {
        // prevent external construction with a private default constructor
    }

    public static SurveyManager get()
    {
        return _instance;
    }

    @Nullable
    public JSONObject createSurveyTemplate(ViewContext context, String schemaName, String queryName)
    {
        BindException errors = new NullSafeBindException(this, "form");
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);
        Map<String, Object> survey = new HashMap<String, Object>();

        if (schema != null)
        {
            QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, queryName);
            QueryView view = schema.createView(context, settings, errors);

            if (view != null)
            {
                survey.put("layout", "auto");

                Map<String, Object> panel = new HashMap<String, Object>();

                panel.put("collapsible", true);
                panel.put("title", queryName);

                List<Map<String, Object>> columns = new ArrayList<Map<String, Object>>();
                for (DisplayColumn dc : view.getDisplayColumns())
                {
                    if (dc.isQueryColumn())
                        columns.add(JsonWriter.getMetaData(dc, null, false, true, false));
                }
                panel.put("items", columns);

                survey.put("items", Collections.singletonList(panel));
            }
        }
        return new JSONObject(survey);
    }

    public SurveyDesign saveSurveyDesign(Container container, User user, SurveyDesign survey)
    {
        DbScope scope = SurveySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();

            SurveyDesign ret;
            if (survey.isNew())
                ret = Table.insert(user, SurveySchema.getInstance().getSurveyDesignsTable(), survey);
            else
                ret = Table.update(user, SurveySchema.getInstance().getSurveyDesignsTable(), survey, survey.getRowId());

            scope.commitTransaction();
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public SurveyDesign getSurvey(Container container, User user, int surveyId)
    {
        return new TableSelector(SurveySchema.getInstance().getSurveyDesignsTable(), new SimpleFilter("rowId", surveyId), null).getObject(SurveyDesign.class);
    }
}