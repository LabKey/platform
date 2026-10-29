/*
 * Copyright (c) 2014-2026 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.exp.property.PropertyValidatorDef;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.query.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PropertyValidatorDef} bound to a column by {@link ValidatorKind#bind}; unlike {@link AbstractColumnValidator},
 * it enforces nothing without a {@link ValidatorContext}
 */
public class PropertyValidator implements ColumnValidator
{
    final @Nullable ValidatorKind kind;
    final PropertyValidatorDef propertyValidator;
    private final ColumnRenderProperties _columnRenderProperties;
    final List<ValidationError> errors = new ArrayList<>(1);

    /** @param kind null when the definition's type URI has no registered kind */
    public PropertyValidator(ColumnRenderProperties crp, PropertyValidatorDef propertyValidator, @Nullable ValidatorKind kind)
    {
        this.propertyValidator = propertyValidator;
        this.kind = kind;
        this._columnRenderProperties = crp;
    }

    /**
     * Always passes: a kind may need the ValidatorContext's user, container, and cache, so property validators are
     * skipped, not enforced, by context-free callers such as Table.insert/update and {@link ColumnValidators#validate}.
     * CONSIDER: have context-free callers exclude property validators explicitly, so a skipped check is never a silent pass.
     */
    @Override
    public String validate(int rowNum, Object value)
    {
        return null;
    }

    @Override
    public String validate(int rowNum, Object value, ValidatorContext validatorContext, @Nullable Object providedValue)
    {
        // Don't validate null values, #15683, #19352
        if (null == value)
            return null;
        if (null == kind)
            return "Validator type : " + propertyValidator.getTypeURI() + " does not exist.";
        if (kind.validate(propertyValidator, _columnRenderProperties , value, errors, validatorContext, providedValue))
            return null;
        if (errors.isEmpty())
            return null;
        String msg = errors.getFirst().getMessage();
        errors.clear();
        return msg;
    }

    public PropertyValidatorDef getPropertyValidator()
    {
        return propertyValidator;
    }
}
