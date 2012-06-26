/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.reports.ReportService;
import org.labkey.api.view.DataView;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.List;

/**
 * View class for rendering CrosstabTableInfos.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Jan 25, 2008
 * Time: 9:49:51 AM
 */
public class CrosstabView extends QueryView
{
    private int _numRowAxisCols = 0;
    private int _numMeasures = 0;
    private int _numMemberMeasures = 0;
    protected List<FieldKey> _columns;

    /** Must call setSettings before using the view */
    public CrosstabView(UserSchema schema)
    {
        super(schema);
        setViewItemFilter(ReportService.EMPTY_ITEM_LIST);
    }

    public CrosstabView(UserSchema schema, QuerySettings settings, @Nullable Errors errors)
    {
        super(schema, settings, errors);
        setViewItemFilter(ReportService.EMPTY_ITEM_LIST);
    }

    protected DataRegion createDataRegion()
    {
        if(getTable() instanceof CrosstabTableInfo)
        {
            CrosstabTableInfo table = (CrosstabTableInfo)getTable();

            //get the display columns
            //this will also adjust _numRowAxisCols and _numMeasures based on
            //the selected display columns
            List<DisplayColumn> displayCols = getDisplayColumns();

            CrosstabDataRegion rgn = new CrosstabDataRegion(table, _numRowAxisCols, _numMeasures, _numMemberMeasures);
            configureDataRegion(rgn);

            return rgn;
        }
        else
            return super.createDataRegion();
    }

    public List<DisplayColumn> getDisplayColumns()
    {
        assert getTable() instanceof CrosstabTableInfo;
        CrosstabTableInfo table = (CrosstabTableInfo)getTable();

        List<FieldKey> selectedCols = null;

        // First check if something has explictly overridden the columns
        if (_columns != null)
        {
            selectedCols = _columns;
        }
        else if (null != getCustomView())
        {
            selectedCols = getCustomView().getColumns();
        }

        if (selectedCols == null || selectedCols.isEmpty())
        {
            selectedCols = table.getDefaultVisibleColumns();
        }

        //separate the row dimension columns from the measure columns
        ArrayList<FieldKey> rowDimCols = new ArrayList<FieldKey>(selectedCols.size());
        ArrayList<FieldKey> measureCols = new ArrayList<FieldKey>(selectedCols.size());
        ArrayList<FieldKey> measureMemberCols = new ArrayList<FieldKey>(selectedCols.size());
        for (FieldKey col : selectedCols)
        {
            ColumnInfo column = table.getColumn(col);
            if (col.getParts().get(0).startsWith(AggregateColumnInfo.NAME_PREFIX))
                measureCols.add(col);
            else if (column != null && column.getCrosstabColumnMember() != null)
                measureMemberCols.add(col);
            else
                rowDimCols.add(col);
        }

        //add the row dimensions to the complete list of columns
        ArrayList<FieldKey> completeCols = new ArrayList<FieldKey>(rowDimCols);

        // For each of the table's column members (pivot values), expand the
        // selected measureCols that start with magic AggregateColumnInfo.NAME_PREFIX into
        // the set of member+measure columns.
        // NOTE: QueryPivot.PivotTable doesn't use the AggregateColumnInfo.NAME_PREFIX magic so will have no measureCols.
        if (!measureCols.isEmpty())
        {
            for (CrosstabMember member : table.getColMembers())
            {
                for (FieldKey col : measureCols)
                {
                    List<String> parts = new ArrayList<String>(col.getParts());
                    parts.set(0, AggregateColumnInfo.getColumnName(member, table.getMeasureFromKey(col.getParts().get(0))));
                    measureMemberCols.add(FieldKey.fromParts(parts));
                }
            }
        }

        completeCols.addAll(measureMemberCols);

        //set the number of selected measures and row dimensions
        _numMeasures = measureCols.size();
        _numMemberMeasures = measureMemberCols.size();
        _numRowAxisCols = rowDimCols.size();

        //put together the complete list of display columns by getting the
        //renderers from the ColumnInfos for the complete list of columns
        List<DisplayColumn> displayColumns = new ArrayList<DisplayColumn>();
        for(ColumnInfo cinfo : QueryService.get().getColumns(table, completeCols).values())
            displayColumns.add(cinfo.getRenderer());

        return displayColumns;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();

        assert getTable() instanceof CrosstabTableInfo;
        CrosstabTableInfo table = (CrosstabTableInfo)getTable();

        //set the default base sort (remove non-existant sort columns)
        Sort sort = new Sort();
        for (Sort.SortField sortField : CrosstabTable.getDefaultSort().getSortList())
            if (getTable().getColumn(sortField.getColumnName()) != null)
                sort.appendSortColumn(sortField);
        view.getRenderContext().setBaseSort(sort);

        if(null != view.getRenderContext().getBaseFilter())
        {
            //separate the filter clauses that apply to aggregate columns
            //from those applying to the row dimensions and hand the agg
            //ones to the table info, and the other to the view's base filter
            SimpleFilter aggFilter = new SimpleFilter();
            SimpleFilter rowDimFilter = new SimpleFilter();
            assert view.getRenderContext().getBaseFilter() instanceof SimpleFilter;
            for(SimpleFilter.FilterClause clause : ((SimpleFilter)(view.getRenderContext().getBaseFilter())).getClauses())
            {
                boolean aggClause = false;
                for(String colName : clause.getColumnNames())
                    aggClause |= colName.startsWith(AggregateColumnInfo.NAME_PREFIX);

                if(aggClause)
                    aggFilter.addClause(clause);
                else
                    rowDimFilter.addClause(clause);
            }

            if(aggFilter.getClauses().size() > 0)
                table.setAggregateFilter(aggFilter);
            view.getRenderContext().setBaseFilter(rowDimFilter);
        }

        return view;
    }

    public ExcelWriter getExcelWriter(ExcelWriter.ExcelDocumentType docType) throws Exception
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setAllowAsync(false);
        Results rs = rgn.getResultSet(view.getRenderContext());

        CrosstabTableInfo table = (CrosstabTableInfo)getTable();
        List<DisplayColumn> displayColumns = rgn.getDisplayColumns();

        return new CrosstabExcelWriter(rs, getExportColumns(displayColumns), table.getColMembers(), _numRowAxisCols, _numMeasures, docType);
    }
}
