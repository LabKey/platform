/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.query.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ColumnValidators
{
    public static void validate(@Nullable ColumnInfo col, @Nullable DomainProperty dp, int rowNum, Object value)
            throws ValidationException
    {
        if (col == null && dp == null)
            return;

        List<ColumnValidator> validators = create(col, dp);

        String name = col != null ? col.getName() : dp.getName();
        for (ColumnValidator v : validators)
        {
            String msg = v.validate(rowNum, value);
            if (msg != null)
                throw new ValidationException(msg, name);
        }
    }

    @NotNull
    public static List<ColumnValidator> create(@Nullable ColumnInfo col, @Nullable DomainProperty dp)
    {
        if (col == null && dp == null)
            return Collections.emptyList();

        List<ColumnValidator> validators = new ArrayList<>();

        add(validators, createRequiredValidator(col, dp));
        add(validators, createLengthValidator(col));
        add(validators, createPropertyValidators(dp));
        add(validators, createDateValidator(col));
        add(validators, createNumberValidator(col));

        if (validators.isEmpty())
            return Collections.emptyList();

        return Collections.unmodifiableList(validators);
    }

    private static void add(List<ColumnValidator> validators, ColumnValidator v)
    {
        if (v != null)
            validators.add(v);
    }

    private static void add(List<ColumnValidator> validators, List<? extends ColumnValidator> vs)
    {
        if (vs != null)
            validators.addAll(vs);
    }

    @Nullable
    public static RequiredValidator createRequiredValidator(@Nullable ColumnInfo col, @Nullable DomainProperty dp)
    {
        boolean supportsMV = (null != col && null != col.getMvColumnName()) || (null != dp && dp.isMvEnabled());
        boolean notnull = null != col && !col.isNullable();
        boolean required = null != dp && dp.isRequired() || null != col && col.isRequired();

        if ((notnull || required) && (col == null || !col.isAutoIncrement()))
        {
            String label = col != null ? col.getName() : dp.getName();
            return new RequiredValidator(label, !notnull && supportsMV);
        }

        return null;
    }

    @Nullable
    public static LengthValidator createLengthValidator(@Nullable ColumnInfo col)
    {
        if (col == null)
            return null;

        JdbcType jdbcType = col.getJdbcType();
        if (jdbcType.isText() && jdbcType != JdbcType.GUID && col.getScale() > 0)
            return new LengthValidator(col.getName(), col.getScale());

        return null;
    }

    @Nullable
    public static List<? extends ColumnValidator> createPropertyValidators(@Nullable DomainProperty dp)
    {
        if (dp == null)
            return null;

        List<? extends IPropertyValidator> validators = dp.getValidators();
        List<PropertyValidator> ret = new ArrayList<>(validators.size());
        for (IPropertyValidator pv : validators)
        {
            ret.add(new PropertyValidator(dp.getName(), dp.getPropertyDescriptor(), pv));
        }

        return ret;
    }

    @Nullable
    public static DateValidator createDateValidator(@Nullable ColumnInfo col)
    {
        if (col == null || !col.getJdbcType().isDateOrTime())
            return null;

        return new DateValidator(col.getName());
    }

    @Nullable
    public static NumberValidator createNumberValidator(@Nullable ColumnInfo col)
    {
        if (col == null || !col.getJdbcType().isReal())
            return null;

        return new NumberValidator(col.getName());
    }

    @Nullable
    public static DuplicateSingleKeyValidator createUniqueValidator(@Nullable ColumnInfo col, boolean caseSensitive)
    {
        if (col == null)
            return null;

        String label = col.getName();
        JdbcType jdbcType = col.getJdbcType();
        return new DuplicateSingleKeyValidator(label, jdbcType, caseSensitive);
    }

}
