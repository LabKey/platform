/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.labkey.api.query.FieldKey;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wraps any DisplayColumn and causes it to render each value separately. Often used in conjunction with
 * MultiValuedLookupColumn
 *
 * User: adam
 * Date: Sep 14, 2010
 */
public class MultiValuedDisplayColumn extends DisplayColumnDecorator implements IMultiValuedDisplayColumn
{
    private final Set<FieldKey> _fieldKeys = new HashSet<>();
    private final Set<FieldKey> _additionalFieldKeys = new HashSet<>();
    private final ColumnInfo _boundCol;
    private final ColumnInfo _lookupCol;

    public MultiValuedDisplayColumn(DisplayColumn dc)
    {
        this(dc, false);
    }

    /** @param boundColumnIsNotMultiValued true in the case when the bound column is the one that declares the multi-valued FK */
    public MultiValuedDisplayColumn(DisplayColumn dc, boolean boundColumnIsNotMultiValued)
    {
        super(dc);

        _boundCol = dc.getColumnInfo();
        ColumnInfo lookupCol = null;
        if (_boundCol.getFk() instanceof MultiValuedForeignKey)
        {
            // Retrieve the value column so it can be used when rendering json or tsv values.
            MultiValuedForeignKey mvfk = (MultiValuedForeignKey)_boundCol.getFk();
            ColumnInfo childKey = mvfk.createJunctionLookupColumn(_boundCol);
            if (childKey != null && childKey.getFk() != null)
            {
                lookupCol = childKey.getFk().createLookupColumn(childKey, childKey.getFk().getLookupColumnName());
                // Remove the intermediate junction table from the FieldKey
                lookupCol.setFieldKey(new FieldKey(_boundCol.getFieldKey(), lookupCol.getFieldKey().getName()));
                _additionalFieldKeys.add(lookupCol.getFieldKey());
            }
        }
        _lookupCol = lookupCol;

        addQueryFieldKeys(_fieldKeys);
        assert _fieldKeys.contains(getColumnInfo().getFieldKey());
        if (boundColumnIsNotMultiValued)
        {
            // The bound column won't have multiple values, so don't put it in the set that should split the string
            // and iterate through individual values
            _fieldKeys.remove(getColumnInfo().getFieldKey());
        }
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.addAll(_additionalFieldKeys);
    }

    private <K> List<K> values(RenderContext ctx, Function<RenderContext, K> fn)
    {
        ArrayList<K> values = new ArrayList<>();
        try
        {
            if (_lookupCol != null && _column instanceof DataColumn)
                ((DataColumn)_column).setBoundColumn(_lookupCol);
            MultiValuedRenderContext mvCtx = new MultiValuedRenderContext(ctx, _fieldKeys);

            while (mvCtx.next())
            {
                values.add(fn.apply(mvCtx));
            }
            return values;
        }
        finally
        {
            if (_lookupCol != null && _column instanceof DataColumn)
                ((DataColumn)_column).setBoundColumn(_boundCol);
        }
    }

    @Override
    public List<String> renderURLs(RenderContext ctx)
    {
        return values(ctx, super::renderURL);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        try
        {
            if (_lookupCol != null && _column instanceof DataColumn)
                ((DataColumn) _column).setBoundColumn(_lookupCol);
            MultiValuedRenderContext mvCtx = new MultiValuedRenderContext(ctx, _fieldKeys);
            String sep = "";

            while (mvCtx.next())
            {
                out.append(sep);
                super.renderGridCellContents(mvCtx, out);
                sep = ", ";
            }
        }
        finally
        {
            if (_lookupCol != null && _column instanceof DataColumn)
                ((DataColumn)_column).setBoundColumn(_boundCol);
        }

        // TODO: Call super in empty values case?
    }

    public void renderDetailsData(RenderContext ctx, Writer out) throws IOException
    {
        renderGridCellContents(ctx, out);
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return getDisplayValues(ctx).stream().map(o -> o == null ? " " : o.toString()).collect(Collectors.joining(", "));
    }

    @Override
    public List<Object> getDisplayValues(RenderContext ctx)
    {
        return values(ctx, super::getDisplayValue);
    }

    @Override
    public String getTsvFormattedValue(RenderContext ctx)
    {
        return getTsvFormattedValues(ctx).stream().collect(Collectors.joining(", "));
    }

    @Override
    public List<String> getTsvFormattedValues(RenderContext ctx)
    {
        return values(ctx, super::getTsvFormattedValue);
    }

    @Override
    public List<String> getFormattedTexts(RenderContext ctx)
    {
        return values(ctx, super::getFormattedText);
    }

    @Override
    public List<Object> getJsonValue(RenderContext ctx)
    {
        return getJsonValues(ctx);
    }

    @Override
    public List<Object> getJsonValues(RenderContext ctx)
    {
        return values(ctx, super::getJsonValue);
    }

    @Override
    public void renderInputCell(RenderContext ctx, Writer out) throws IOException
    {
        renderInputWrapperBegin(out);
        renderInputHtml(ctx, out, getInputValue(ctx));
        renderInputWrapperEnd(out);
    }

    @Override
    public Object getInputValue(RenderContext ctx)
    {
        return values(ctx, super::getInputValue);
    }
}
