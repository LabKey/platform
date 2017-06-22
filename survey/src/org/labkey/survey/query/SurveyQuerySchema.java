/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.survey.SurveySchema;
import org.springframework.validation.BindException;

import java.util.Set;

/**
 * User: klum
 * Date: 12/7/12
 */
public class SurveyQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "survey";
    public static final String SCHEMA_DESCR = "Contains data about surveys and responses.";
    public static final String SURVEY_DESIGN_TABLE_NAME = "SurveyDesigns";
    public static final String SURVEYS_TABLE_NAME = "Surveys";

    public SurveyQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, SurveySchema.getInstance().getSchema());
    }

    @Override
    public Set<String> getTableNames()
    {
        return PageFlowUtil.set(SURVEY_DESIGN_TABLE_NAME, SURVEYS_TABLE_NAME);
    }

    @Override
    public TableInfo createTable(String name)
    {
        if (SURVEY_DESIGN_TABLE_NAME.equalsIgnoreCase(name))
            return new SurveyDesignTable(SurveySchema.getInstance().getSchema().getTable(SURVEY_DESIGN_TABLE_NAME), this);
        if (SURVEYS_TABLE_NAME.equalsIgnoreCase(name))
            return new SurveysTable(SurveySchema.getInstance().getSchema().getTable(SURVEYS_TABLE_NAME), this).init();

        return null;
    }

    @Override
    protected QuerySettings createQuerySettings(String dataRegionName, String queryName, String viewName)
    {
        if (queryName != null && queryName.equals(SURVEYS_TABLE_NAME))
            return new SurveyQuerySettings(dataRegionName);

        return super.createQuerySettings(dataRegionName, queryName, viewName);
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        if (SURVEY_DESIGN_TABLE_NAME.equalsIgnoreCase(settings.getQueryName()))
            return new SurveyDesignQueryView(this, settings, errors);
        else if (SURVEYS_TABLE_NAME.equalsIgnoreCase(settings.getQueryName()))
            return new SurveyQueryView(this, settings, errors);

        return super.createView(context, settings, errors);
    }
}
