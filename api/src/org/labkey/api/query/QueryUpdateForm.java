/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

/**
 * Extension of the class TableViewForm which deals with the fact that we don't have much control over the names
 * of fields in a user-defined table.
 * All column names are prefixed by "quf_" to generate the name of the field on the input form.
 */
public class QueryUpdateForm extends TableViewForm
{
    /**
     * Prefix prepended to all form elements
     */
    public static final String PREFIX = "quf_";
    private boolean _ignorePrefix; // for usages that want to use the QueryUpdateForm and QUS.updateRows directly

    public QueryUpdateForm(@NotNull TableInfo table, @NotNull ViewContext ctx)
    {
        this(table, ctx, null);
    }

    public QueryUpdateForm(@NotNull TableInfo table, @NotNull ViewContext ctx, boolean ignorePrefix)
    {
        this(table, ctx, null);
        _ignorePrefix = ignorePrefix;
    }

    public QueryUpdateForm(@NotNull TableInfo table, @NotNull ViewContext ctx, @Nullable BindException errors)
    {
        _tinfo = table;
        _dynaClass = new QueryWrapperDynaClass(this);
        setViewContext(ctx);

        // TODO: Fix this hack.
        // This should be a normal form that uses normal Spring parameter binding
        BindException newErrors = bindParameters(ctx.getBindPropertyValues());

        // More hackiness -- can only add more errors if object names match.  Blow up in dev mode, ignore in production.
        if (newErrors.hasErrors() && null != errors)
        {
            assert newErrors.getObjectName().equals(errors.getObjectName());

            if (newErrors.getObjectName().equals(errors.getObjectName()))
                errors.addAllErrors(newErrors);
        }
    }

    @Override
    @Nullable
    public ColumnInfo getColumnByFormFieldName(@NotNull String name)
    {
        if (!_ignorePrefix && name.length() < PREFIX.length())
            return null;

        return getTable().getColumn(_ignorePrefix ? name : name.substring(PREFIX.length()));
    }

    @Override
    public String getFormFieldName(@NotNull ColumnInfo column)
    {
        return (_ignorePrefix ? "" : PREFIX) + column.getName();
    }
}
