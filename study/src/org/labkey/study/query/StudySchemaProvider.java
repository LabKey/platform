/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

public class StudySchemaProvider extends DefaultSchema.SchemaProvider
{
    public StudySchemaProvider(@Nullable Module module)
    {
        super(module);
    }

    @Override
    public boolean isAvailable(DefaultSchema schema, Module module)
    {
        return true;
    }

    public QuerySchema createSchema(DefaultSchema schema, Module module)
    {
        Container container = schema.getContainer();
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study == null)
        {
            return StudyQuerySchema.createSchemaWithoutStudy(container, schema.getUser());
        }
        return StudyQuerySchema.createSchema(study, schema.getUser(), true);
    }
}
