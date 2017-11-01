/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

    public String _successUrl = null;

    public QueryUpdateForm(@NotNull TableInfo table, @NotNull ViewContext ctx)
    {
        this(table, ctx, null);
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

    @Nullable
    public ColumnInfo getColumnByFormFieldName(@NotNull String name)
    {
        if (name.length() < PREFIX.length())
            return null;

        return getTable().getColumn(name.substring(PREFIX.length()));
    }

    public String getFormFieldName(@NotNull ColumnInfo column)
    {
        return PREFIX + column.getName();
    }

    public void setSuccessUrl(String s)
    {
        _successUrl = s;
    }

    public String getSuccessUrl()
    {
        return _successUrl;
    }
}
