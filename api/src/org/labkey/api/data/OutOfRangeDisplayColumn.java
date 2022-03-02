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

package org.labkey.api.data;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Used to render columns that persist their values into two separate database columns - the raw value, and a separate
 * String column that indicates if the value is considered out-of-range (OOR) or not. Similar to MVDisplayColumn, although
 * OOR indicators can be arbitrary strings instead of a discrete list of values.
 *
 * Rendered as the raw value prefixed by the OOR indicator (if any).
 *
 * User: jeckels
 * Date: Jul 30, 2007
 */
public class OutOfRangeDisplayColumn extends DataColumn
{
    private ColumnInfo _oorIndicatorColumn;

    /**
     * Look up the OORIndicator column through QueryService
     */
    public OutOfRangeDisplayColumn(ColumnInfo numberColumn)
    {
        this(numberColumn, null);
    }

    public OutOfRangeDisplayColumn(ColumnInfo numberColumn, ColumnInfo oorIndicatorColumn)
    {
        super(numberColumn);
        _oorIndicatorColumn = oorIndicatorColumn;
    }

    @Override @NotNull
    public HtmlString getFormattedHtml(RenderContext ctx)
    {
        return HtmlStringBuilder.of(getOORPrefix(ctx)).append(super.getFormattedHtml(ctx)).getHtmlString();
    }

    @NotNull
    protected String getOORPrefix(RenderContext ctx)
    {
        if (_oorIndicatorColumn != null)
        {
            Object oorValue = _oorIndicatorColumn.getValue(ctx);
            return null == oorValue ? "" : oorValue.toString();
        }
        else
        {
            // Try to only show the error message for the first row
            int row = 1;
            if (ctx.getResults() != null)
            {
                try
                {
                    row = ctx.getResults().getRow();
                }
                catch (SQLException ignored)
                {
                }
            }
            if (row == 1)
            {
                return "<missing column " + getColumnInfo().getName() + OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX + ">";
            }
        }
        return "";
    }

    @Override
    public @Nullable String getFormattedText(RenderContext ctx)
    {
        // Unlike getTsvFormattedValue() and getFormattedHtml(), super.getFormattedText() will return null when no format is specified.
        // To keep it simple we will always format (e.g. convert to String)
        String oorPrefix = getOORPrefix(ctx);
        String formattedValue = "";
        Object value = getDisplayValue(ctx);
        if (null != value)
        {
            formattedValue = super.getFormattedText(ctx);
            if (null == formattedValue)
                formattedValue = ConvertUtils.convert(value);
        }
        assert null != formattedValue;
        return oorPrefix + formattedValue;
    }

    @Override
    public String getTsvFormattedValue(RenderContext ctx)
    {
        return getOORPrefix(ctx) + StringUtils.defaultString(super.getTsvFormattedValue(ctx));
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);

        if (_oorIndicatorColumn == null)
        {
            FieldKey fk = new FieldKey(getBoundColumn().getFieldKey().getParent(), getBoundColumn().getFieldKey().getName() + OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX);
            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(getBoundColumn().getParentTable(), Collections.singleton(fk));
            _oorIndicatorColumn = cols.get(fk);
        }

        if (_oorIndicatorColumn != null)
        {
            columns.add(_oorIndicatorColumn);
        }
    }


    public static final ColumnInfo numberColumn = new BaseColumnInfo("number", JdbcType.DOUBLE);
    public static final ColumnInfo oorColumn = new BaseColumnInfo( "oor", JdbcType.VARCHAR);

    public static class TestCase extends Assert
    {
        OutOfRangeDisplayColumn dc = new OutOfRangeDisplayColumn(numberColumn, oorColumn);

        private RenderContext renderContext(Object d, Object oor)
        {
            var ret = new RenderContext();
            ret.put(numberColumn.getAlias(), d);
            ret.put(oorColumn.getAlias(), oor);
            return ret;
        }

        @Test
        public void testNull()
        {
            dc.setFormatString(null);

            var txt = dc.getFormattedText(renderContext(null,null));
            assertEquals("", txt);
            var tsv = dc.getTsvFormattedValue(renderContext(null,null));
            assertEquals("", tsv);

            txt = dc.getFormattedText(renderContext(null, "!"));
            assertEquals("!", txt);
            tsv = dc.getTsvFormattedValue(renderContext(null, "!"));
            assertEquals("!", tsv);
        }

        @Test
        public void testNullWithFormat()
        {
            dc.setFormatString("0.00");

            var txt = dc.getFormattedText(renderContext(null, null));
            assertEquals("", txt);
            var tsv = dc.getTsvFormattedValue(renderContext(null, null));
            assertEquals("", tsv);

            dc.setFormatString("0.00");
            txt = dc.getFormattedText(renderContext(null, "!"));
            assertEquals("!", txt);
            tsv = dc.getTsvFormattedValue(renderContext(null, "!"));
            assertEquals("!", tsv);
        }

        @Test
        public void testValue()
        {
            dc.setFormatString(null);

            var txt = dc.getFormattedText(renderContext(0.5, null));
            assertEquals("0.5", txt);
            var tsv = dc.getTsvFormattedValue(renderContext(0.5, null));
            assertEquals("0.5", tsv);

            txt = dc.getFormattedText(renderContext(0.5, "<"));
            assertEquals("<0.5", txt);
            tsv = dc.getTsvFormattedValue(renderContext(0.5, "<"));
            assertEquals("<0.5", tsv);
        }

        @Test
        public void testValueWithFormat()
        {
            dc.setFormatString("0.00");

            dc.setFormatString("0.00");
            var txt = dc.getFormattedText(renderContext(0.5, null));
            assertEquals("0.50", txt);
            var tsv = dc.getTsvFormattedValue(renderContext(0.5, null));
            assertEquals("0.50", tsv);

            dc.setFormatString("0.00");
            txt = dc.getFormattedText(renderContext(0.5, "<"));
            assertEquals("<0.50", txt);
            tsv = dc.getTsvFormattedValue(renderContext(0.5, "<"));
            assertEquals("<0.50", tsv);
        }
    }
}
