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

/**
 * Validate that there is a value.
 * There seem to be two kinds of required:
 * those that accept MissingValue and those that don't.
 */
public class RequiredValidator extends AbstractColumnValidator implements UnderstandsMissingValues
{
    final boolean allowMV;
    final boolean allowES;

    public RequiredValidator(String columnName, boolean allowMissingValueIndicators, boolean allowEmptyString)
    {
        super(columnName);
        allowMV = allowMissingValueIndicators;
        allowES = allowEmptyString;
    }

    @Override
    protected String _validate(int rowNum, Object value)
    {
        checkRequired:
        {
            if (null == value)
                break checkRequired;

            if (value instanceof String && ((String)value).length() == 0)
            {
                if (allowES)
                    return null;
                else break checkRequired;
            }

            if (!(value instanceof MvFieldWrapper))
                return null;

            MvFieldWrapper mv = (MvFieldWrapper)value;
            if (null != mv.getValue())
                return null;

            if (!mv.isEmpty() && allowMV)
                return null;
        }

        // DatasetDefinition.importDatasetData:: errors.add("Row " + rowNumber + " does not contain required field " + col.getName() + ".");
        // OntologyManager.insertTabDelimited::  throw new ValidationException("Missing value for required property " + col.getName());
        return "Missing value for required property: " + _columnName;
    }
}
