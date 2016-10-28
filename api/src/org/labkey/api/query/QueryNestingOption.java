/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.labkey.api.data.*;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Configuration for a NestedQueryView that understands which columns should be associated with the outer and inner
 * grids. Also tracks the URL at which a specific nested grid can be requested independently via AJAX.
 *
 * User: jeckels
 * Date: Apr 9, 2007
 */
public class QueryNestingOption
{
    private FieldKey _rowIdFieldKey;
    private FieldKey _aggregateRowIdFieldKey;
    private final String _ajaxNestedGridURL;
    private DataColumn _groupIdColumn;

    /**
     * @param aggregateRowIdColumn the column that's an FK to the table that should be shown in the parent grids
     * @param rowIdColumn the column that's the PK (the target of the FK from aggregateRowIdColumn)
     * @param ajaxNestedGridURL the URL to use to request the child grid via AJAX
     */
    public QueryNestingOption(FieldKey aggregateRowIdColumn, FieldKey rowIdColumn, String ajaxNestedGridURL)
    {
        _aggregateRowIdFieldKey = aggregateRowIdColumn;
        _rowIdFieldKey = rowIdColumn;
        _ajaxNestedGridURL = ajaxNestedGridURL;
    }

    public void setupGroupIdColumn(List<DisplayColumn> allColumns, List<DisplayColumn> outerColumns, TableInfo parentTable)
    {
        if (_groupIdColumn != null)
        {
            return;
        }
        Map<FieldKey, ColumnInfo> infos = QueryService.get().getColumns(parentTable, Collections.singleton(_rowIdFieldKey));
        assert infos.size() == 1;

        ColumnInfo info = infos.values().iterator().next();
        _groupIdColumn = new DataColumn(info);
        _groupIdColumn.setVisible(false);
        allColumns.add(_groupIdColumn);
        outerColumns.add(_groupIdColumn);
    }

    public boolean isNested(List<DisplayColumn> columns)
    {
        boolean foundInner = false;
        boolean foundOuter = false;
        for (DisplayColumn column : columns)
        {
            if (isOuter(column))
            {
                foundOuter = true;
            }
            else
            {
                foundInner = true;
            }
        }
        return foundOuter && foundInner;
    }

    private boolean isOuter(DisplayColumn column)
    {
        ColumnInfo colInfo = column.getColumnInfo();
        return colInfo != null && isOuter(colInfo.getFieldKey());
    }

    public boolean isOuter(FieldKey fieldKey)
    {
        return fieldKey.toString().toLowerCase().startsWith(_aggregateRowIdFieldKey.toString().toLowerCase() + "/");
    }

    public FieldKey getRowIdFieldKey()
    {
        return _rowIdFieldKey;
    }

    public FieldKey getAggregateRowIdFieldKey()
    {
        return _aggregateRowIdFieldKey;
    }

    public NestableDataRegion createDataRegion(List<DisplayColumn> originalColumns, String dataRegionName, boolean expanded)
    {
        List<DisplayColumn> innerColumns = new ArrayList<>();
        List<DisplayColumn> outerColumns = new ArrayList<>();
        List<DisplayColumn> allColumns = new ArrayList<>(originalColumns);

        for (DisplayColumn column : originalColumns)
        {
            if (isOuter(column))
            {
                setupGroupIdColumn(allColumns, outerColumns, column.getColumnInfo().getParentTable());
                outerColumns.add(column);
            }
            else
            {
                innerColumns.add(column);
            }
        }

        NestableDataRegion dataRegion = new NestableDataRegion(allColumns, _groupIdColumn.getColumnInfo().getAlias(), _ajaxNestedGridURL);
        // Set the nested button bar as not visible so that we don't render a bunch of nested <form>s which mess up IE.
        dataRegion.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        dataRegion.setExpanded(expanded);
        dataRegion.setRecordSelectorValueColumns(_groupIdColumn.getColumnInfo().getAlias());
        DataRegion nestedRgn = new DataRegion()
        {
            @Override
            protected void renderHeaderScript(RenderContext ctx, Writer out,  Map<String, String> messages, boolean showRecordSelectors) throws IOException
            {
                // Issue 11405: customized grid does not work MS2 query based views.
                // Nested DataRegions don't need to re-render the "new LABKEY.DataRegion(...)" script.
            }
        };

        nestedRgn.setName(dataRegionName);
        ButtonBar bar = new ButtonBar();
        bar.setVisible(false);
        nestedRgn.setShowFilterDescription(false);
        nestedRgn.setButtonBar(bar);
        nestedRgn.setDisplayColumns(innerColumns);
        dataRegion.setNestedRegion(nestedRgn);
        for (DisplayColumn column : outerColumns)
        {
            column.setCaption(column.getColumnInfo().getLabel());
        }
        dataRegion.setDisplayColumns(outerColumns);

        return dataRegion;
    }
}
