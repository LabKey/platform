/*
 * Copyright (c) 2012-2026 LabKey Corporation
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
 */package org.labkey.api.audit.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A column used to help render the changes between two encoded audit datamaps (primarily used to show record changes),
 * in a user readeable format
 */
public class DataMapDiffColumn extends AliasedColumn
{
    public DataMapDiffColumn(TableInfo parent, String name, final ColumnInfo oldValues, final ColumnInfo newValues)
    {
        super(parent, name, oldValues);

        setDisplayColumnFactory(colInfo -> new ChangesDisplayColumn(colInfo, newValues));
    }

    private static class ChangesDisplayColumn extends DataColumn
    {
        private static final HtmlString HTML_ARROW = HtmlString.unsafe("&nbsp;&raquo;&nbsp;");

        private record Change(String field, String oldValue, String newValue) {}

        ColumnInfo _oldValues;
        ColumnInfo _newValues;

        public ChangesDisplayColumn(ColumnInfo oldValues, ColumnInfo newValues)
        {
            super(oldValues);
            _oldValues = oldValues;
            _newValues = newValues;
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            Object value = super.getValue(ctx);

            return value != null ? value : "";
        }

        @Override @NotNull
        public HtmlString getFormattedHtml(RenderContext ctx)
        {
            return formatHtml(ctx);
        }

        @Override
        public String getTsvFormattedValue(RenderContext ctx)
        {
            return formatText(ctx);
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return formatText(ctx);
        }

        @Override
        public boolean isFilterable()
        {
            return false;
        }

        @Override
        public boolean isSortable()
        {
            return false;
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);

            keys.add(_oldValues.getFieldKey());
            keys.add(_newValues.getFieldKey());
        }

        @NotNull
        private HtmlString formatHtml(RenderContext ctx)
        {
            return HtmlString.join(getChanges(ctx).stream()
                .map(c -> HtmlStringBuilder.of(c.field()).append(": ").append(c.oldValue()).append(HTML_ARROW).append(c.newValue()).getHtmlString())
                .toList(), HtmlString.BR);
        }

        @NotNull
        private String formatText(RenderContext ctx)
        {
            return getChanges(ctx).stream()
                .map(c -> c.field() + ": " + c.oldValue() + " > " + c.newValue())
                .collect(Collectors.joining("\n"));
        }

        private List<Change> getChanges(RenderContext ctx)
        {
            Map<String, String> oldValues = DataMapColumn.decode(ctx, ctx.get(_oldValues.getFieldKey()));
            Map<String, String> newValues = DataMapColumn.decode(ctx, ctx.get(_newValues.getFieldKey()));
            List<Change> changes = new ArrayList<>();

            for (Map.Entry<String, String> entry : oldValues.entrySet())
            {
                String oldValue = Objects.toString(entry.getValue(), "");
                String newValue = Objects.toString(newValues.get(entry.getKey()), "");
                if (!newValue.equals(oldValue))
                    changes.add(new Change(entry.getKey(), oldValue, newValue));
            }

            for (Map.Entry<String, String> entry : newValues.entrySet())
            {
                if (!oldValues.containsKey(entry.getKey()))
                    changes.add(new Change(entry.getKey(), "", Objects.toString(entry.getValue(), "")));
            }

            return changes;
        }
    }
}
