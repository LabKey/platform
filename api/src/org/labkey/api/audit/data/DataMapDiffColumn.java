/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A column used to help render the changes between two encoded audit datamaps (primarily used to show record changes),
 * in a user readeable format
 */
public class DataMapDiffColumn extends AliasedColumn
{
    public DataMapDiffColumn(TableInfo parent, String name, final ColumnInfo oldValues, final ColumnInfo newValues)
    {
        super(parent, name, oldValues);

        setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ChangesDisplayColumn(colInfo, newValues);
            }
        });
    }

    private static class ChangesDisplayColumn extends DataColumn
    {
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
        public String getFormattedValue(RenderContext ctx)
        {
            return formatColumn(ctx.get(_oldValues.getFieldKey()), ctx.get(_newValues.getFieldKey()), "<br>");
        }

        @Override
        public String getTsvFormattedValue(RenderContext ctx)
        {
            return formatColumn(ctx.get(_oldValues.getFieldKey()), ctx.get(_newValues.getFieldKey()), "\n");
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return formatColumn(ctx.get(_oldValues.getFieldKey()), ctx.get(_newValues.getFieldKey()), "\n");
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
        private String formatColumn(Object oldContent, Object newContent, String lineBreak)
        {
            if (oldContent != null || newContent != null)
            {
                String delim = "";
                StringBuilder sb = new StringBuilder();

                Map<String, String> oldValues = Collections.emptyMap();
                Map<String, String> newValues = Collections.emptyMap();

                if (oldContent instanceof String)
                    oldValues = AbstractAuditTypeProvider.decodeFromDataMap((String)oldContent);

                if (newContent instanceof String)
                    newValues = AbstractAuditTypeProvider.decodeFromDataMap((String)newContent);

                for (Map.Entry<String, String> entry : oldValues.entrySet())
                {
                    String oldValue = entry.getValue();
                    if (oldValue == null)
                        oldValue = "";

                    String newValue = newValues.remove(entry.getKey());
                    if (newValue == null)
                        newValue = "";

                    if (!newValue.equals(oldValue))
                    {
                        sb.append(delim);
                        sb.append(entry.getKey()).append(": ").append(oldValue);
                        sb.append("&nbsp;&raquo;&nbsp;").append(newValue);

                        delim = lineBreak;
                    }
                }

                for (Map.Entry<String, String> entry : newValues.entrySet())
                {
                    sb.append(delim);
                    sb.append(entry.getKey()).append(": ");

                    String newValue = entry.getValue();
                    if (newValue == null)
                        newValue = "";

                    sb.append("&nbsp;&raquo;&nbsp;");
                    sb.append(newValue);

                    delim = lineBreak;
                }

                return sb.toString();
            }
            return "";
        }
    }
}
