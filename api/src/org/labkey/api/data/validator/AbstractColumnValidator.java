/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.api.data.validator;

import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.property.ValidatorContext;

public abstract class AbstractColumnValidator implements ColumnValidator
{
    // Column label used only for formatting error messages
    final String _columnName;

    public AbstractColumnValidator(String columnName)
    {
        _columnName = columnName;
    }

    @Override
    public String validate(int rowNum, Object o)
    {
        if (o instanceof MvFieldWrapper && !(this instanceof UnderstandsMissingValues))
            return null;
        return _validate(rowNum, o);
    }

    @Override
    public String validate(int rowNum, Object value, ValidatorContext validatorContext)
    {
        return validate(rowNum, value);
    }

    protected abstract String _validate(int rowNum, Object value);
}
