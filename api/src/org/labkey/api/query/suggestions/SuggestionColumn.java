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
package org.labkey.api.query.suggestions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the ranking step knows about a candidate column: its metadata plus whatever data-derived facts (values,
 * shapes, ranges) are available. Absent facts disable the tiers that need them.
 */
public class SuggestionColumn
{
    public enum Type { BOOLEAN, DATE, FLOAT, INT, STRING }

    private final String _fieldKey;
    private final String _caption;
    private final Type _type;
    private final @Nullable ColumnInfo _columnInfo;

    private boolean _key;
    private boolean _title;
    private boolean _uniqueId;
    private boolean _rangeUnknown;
    private final List<String> _nameExpressions = new ArrayList<>();
    private @Nullable List<String> _values;
    private @Nullable List<String> _shapes;
    // Number for numeric columns, LocalDate for date columns
    private @Nullable Object _min;
    private @Nullable Object _max;
    private @Nullable String _changeToken;

    public SuggestionColumn(@NotNull String fieldKey, @NotNull String caption, @NotNull Type type, @Nullable ColumnInfo columnInfo)
    {
        _fieldKey = fieldKey;
        _caption = caption;
        _type = type;
        _columnInfo = columnInfo;
    }

    public @NotNull String getFieldKey()
    {
        return _fieldKey;
    }

    public @NotNull String getCaption()
    {
        return _caption;
    }

    public @NotNull Type getType()
    {
        return _type;
    }

    /** Null only for columns built outside a request, such as test fixtures. */
    public @Nullable ColumnInfo getColumnInfo()
    {
        return _columnInfo;
    }

    public boolean isKey()
    {
        return _key;
    }

    public void setKey(boolean key)
    {
        _key = key;
    }

    public boolean isTitle()
    {
        return _title;
    }

    public void setTitle(boolean title)
    {
        _title = title;
    }

    public boolean isUniqueId()
    {
        return _uniqueId;
    }

    public void setUniqueId(boolean uniqueId)
    {
        _uniqueId = uniqueId;
    }

    /** Range stats are unavailable, so range checks are skipped rather than excluding the column. */
    public boolean isRangeUnknown()
    {
        return _rangeUnknown;
    }

    public void setRangeUnknown(boolean rangeUnknown)
    {
        _rangeUnknown = rangeUnknown;
    }

    /** Name expressions whose generated names this column holds, e.g. a sample type's name and aliquot expressions. */
    public @NotNull List<String> getNameExpressions()
    {
        return _nameExpressions;
    }

    public void addNameExpression(@Nullable String nameExpression)
    {
        if (nameExpression != null && !nameExpression.isBlank() && !_nameExpressions.contains(nameExpression))
            _nameExpressions.add(nameExpression);
    }

    public @Nullable List<String> getValues()
    {
        return _values;
    }

    public void setValues(@Nullable List<String> values)
    {
        _values = values;
    }

    public @Nullable List<String> getShapes()
    {
        return _shapes;
    }

    public void setShapes(@Nullable List<String> shapes)
    {
        _shapes = shapes;
    }

    public @Nullable Object getMin()
    {
        return _min;
    }

    public @Nullable Object getMax()
    {
        return _max;
    }

    public void setRange(@Nullable Object min, @Nullable Object max)
    {
        _min = min;
        _max = max;
    }

    /** Extra token for a column whose data changes independently of its table, such as storage columns on samples. */
    public @Nullable String getChangeToken()
    {
        return _changeToken;
    }

    /** Cached facts for this column are keyed by the table's change token plus this one. */
    public void setChangeToken(@Nullable String changeToken)
    {
        _changeToken = changeToken;
    }
}
