/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.experiment.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.query.FieldKey;

/**
 * User: kevink
 * Date: 3/16/16
 */
public class ChildOfClause extends LineageClause
{
    public ChildOfClause(@NotNull FieldKey fieldKey, Object value)
    {
        super(fieldKey, value);
    }

    protected ExpLineageOptions createOptions()
    {
        return LineageHelper.createChildOfOptions();
    }

    protected String getLsidColumn()
    {
        return "lsid";
    }

    protected String filterTextType()
    {
        return "child of";
    }
}
