/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;

public class SurveySchema
{
    private static final SurveySchema _instance = new SurveySchema();
    public static final String DB_SCHEMA_NAME = "survey";

    public static SurveySchema getInstance()
    {
        return _instance;
    }

    private SurveySchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.survey.SurveySchema.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(DB_SCHEMA_NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getSurveyDesignsTable()
    {
        return getSchema().getTable("SurveyDesigns");
    }

    public TableInfo getSurveysTable()
    {
        return getSchema().getTable("Surveys");
    }
}
