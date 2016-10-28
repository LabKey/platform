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
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.data.xml.queryCustomView.OperatorType;

/**
 * User: kevink
 * Date: 5/23/16
 */
public class ChildOfCompareType extends CompareType
{
    public ChildOfCompareType()
    {
        super("Is Child Of", "exp:childof", "EXP_CHILD_OF", true, " is child of", OperatorType.EXP_CHILDOF);
    }

    @Override
    protected SimpleFilter.FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
    {
        return new ChildOfClause(fieldKey, value);
    }

    @Override
    public boolean meetsCriteria(Object value, Object[] paramVals)
    {
        throw new UnsupportedOperationException("Conditional formatting not yet supported for EXP_CHILD_OF");
    }
}
