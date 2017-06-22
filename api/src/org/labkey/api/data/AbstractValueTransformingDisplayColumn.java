/*
 * Copyright (c) 2017 LabKey Corporation
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
import org.labkey.api.util.PageFlowUtil;

/**
 * Base class for transforming a raw DB column into a completely transformed value. An example is wrapping a RowId
 * column in the real table and showing a value that's calculated in Java code instead based on looking up other
 * data. Provides one simple method to implement to handle returning that value in HTML, exports, client API requests, etc.
 * Created by Josh on 5/17/2017.
 */
public abstract class AbstractValueTransformingDisplayColumn<RawDataType, TransformedDataType> extends DataColumn
{
    protected final Class<TransformedDataType> _dataTypeClass;
    protected final boolean _sortable;
    protected final boolean _filterable;

    /** Default to not being sortable or filterable, since we've significantly transformed the underlying value */
    public AbstractValueTransformingDisplayColumn(ColumnInfo col, Class<TransformedDataType> dataTypeClass)
    {
        this(col, dataTypeClass, false, false);
    }

    public AbstractValueTransformingDisplayColumn(ColumnInfo col, Class<TransformedDataType> dataTypeClass, boolean sortable, boolean filterable)
    {
        super(col);
        _dataTypeClass = dataTypeClass;
        _sortable = sortable;
        _filterable = filterable;
    }


    @Override
    public Class<TransformedDataType> getValueClass()
    {
        // Present the transformed type as the class for this column
        return _dataTypeClass;
    }

    @Override
    public Class<TransformedDataType> getDisplayValueClass()
    {
        // Present the transformed type as the class for this column
        return _dataTypeClass;
    }

    /** Wrapper to provide type-safety */
    protected RawDataType getRawValue(RenderContext ctx)
    {
        return (RawDataType)super.getValue(ctx);
    }

    @Override
    public TransformedDataType getValue(RenderContext ctx)
    {
        return transformValue(getRawValue(ctx));
    }

    @Override
    public TransformedDataType getDisplayValue(RenderContext ctx)
    {
        return getValue(ctx);
    }

    @Override
    public @NotNull String getFormattedValue(RenderContext ctx)
    {
        return PageFlowUtil.filter(getDisplayValue(ctx));
    }

    /** Convert from the raw database value to however it should be presented */
    protected abstract TransformedDataType transformValue(RawDataType rawValue);
}
