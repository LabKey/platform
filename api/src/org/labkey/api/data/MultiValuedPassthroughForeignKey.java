/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.logging.LogHelper;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;

/**
 * Lets a lookup continue past a {@link MultiValuedLookupColumn}. The multi-valued column's value is an aggregate of
 * many rows, so the next join can't hang off it; instead this creates another MultiValuedLookupColumn that performs
 * the join inside its own aggregate subquery. Everything other than column creation delegates to the target column's
 * real foreign key.
 *
 * Only single-valued lookups may follow the multi-valued hop -- a second to-many hop would need nested arrays, which
 * neither the delimited transport nor {@link MultiValuedRenderContext} can represent.
 */
public class MultiValuedPassthroughForeignKey implements ForeignKey
{
    private static final Logger LOG = LogHelper.getLogger(MultiValuedPassthroughForeignKey.class, "Lookups traversing a multi-valued column");

    /** Hops after the multi-valued one. Each adds a join and two alias segments against a 63 character identifier limit. */
    public static final int MAX_LOOKUP_DEPTH = 4;

    private final MultiValuedLookupColumn _multiValuedColumn;
    private final ForeignKey _fk;

    public MultiValuedPassthroughForeignKey(MultiValuedLookupColumn multiValuedColumn, ForeignKey fk)
    {
        _multiValuedColumn = multiValuedColumn;
        _fk = fk;
    }

    @Override
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        if (_fk instanceof MultiValuedForeignKey)
        {
            LOG.debug("Not traversing '{}': a second multi-valued lookup would require nested values", _multiValuedColumn.getFieldKey());
            return null;
        }

        if (_multiValuedColumn.getHopCount() >= MAX_LOOKUP_DEPTH)
        {
            LOG.debug("Not traversing '{}': more than {} lookups past a multi-valued column", _multiValuedColumn.getFieldKey(), MAX_LOOKUP_DEPTH);
            return null;
        }

        // Root the inner lookup at the previous hop's display column so its joins land inside the aggregate subquery
        ColumnInfo lookupColumn = _fk.createLookupColumn(_multiValuedColumn.getDisplayColumn(), displayField);

        if (null == lookupColumn)
            return null;

        if (lookupColumn.isMultiValued() || lookupColumn instanceof MultiValuedLookupColumn)
        {
            LOG.debug("Not traversing '{}/{}': the target column is itself multi-valued", _multiValuedColumn.getFieldKey(), displayField);
            return null;
        }

        return new MultiValuedLookupColumn(_multiValuedColumn, _fk, lookupColumn);
    }

    @Override
    @Nullable
    public TableInfo getLookupTableInfo()
    {
        return _fk.getLookupTableInfo();
    }

    @Override
    @Nullable
    public StringExpression getURL(ColumnInfo parent)
    {
        return _fk.getURL(parent);
    }

    @Override
    @NotNull
    public NamedObjectList getSelectList(RenderContext ctx)
    {
        return _fk.getSelectList(ctx);
    }

    @Override
    public Container getLookupContainer()
    {
        return _fk.getLookupContainer();
    }

    @Override
    public String getLookupTableName()
    {
        return _fk.getLookupTableName();
    }

    @Override
    public String getLookupSchemaName()
    {
        return _fk.getLookupSchemaName();
    }

    @Override
    public SchemaKey getLookupSchemaKey()
    {
        return _fk.getLookupSchemaKey();
    }

    @Override
    public String getLookupColumnName()
    {
        return _fk.getLookupColumnName();
    }

    @Override
    public String getLookupDisplayName()
    {
        return _fk.getLookupDisplayName();
    }

    @Override
    public ForeignKey remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> mapping)
    {
        return new MultiValuedPassthroughForeignKey(_multiValuedColumn, _fk.remapFieldKeys(parent, mapping));
    }

    @Override
    public Set<FieldKey> getSuggestedColumns()
    {
        // Suggested columns would resolve against the outer table, not the aggregate subquery
        return null;
    }

    @Override
    public boolean isShowAsPublicDependency()
    {
        return _fk.isShowAsPublicDependency();
    }
}
