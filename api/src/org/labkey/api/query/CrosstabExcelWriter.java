/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.labkey.api.data.CrosstabTableInfo;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.CrosstabMember;
import org.labkey.api.data.Results;
import org.labkey.api.util.Pair;

import java.util.List;

/**
 * Used when exporting a crosstab view to Excel.
 *
 * User: Dave
 * Date: Feb 5, 2008
 * Time: 5:01:41 PM
 */
public class CrosstabExcelWriter extends ExcelWriter
{
    private boolean _includeDimensionHeader = false;
    private CrosstabTableInfo _table;
    private int _numRowAxisCols = 0;
    private List<Pair<CrosstabMember, List<DisplayColumn>>> _groupedByMember;

    public CrosstabExcelWriter(CrosstabTableInfo table, Results rs, List<DisplayColumn> displayColumns, int numRowAxisCols, ExcelDocumentType docType)
    {
        super(rs, displayColumns, docType);
        _table = table;
        _numRowAxisCols = numRowAxisCols;

        _groupedByMember = CrosstabView.columnsByMember(displayColumns);
    }

    @Override
    public void renderColumnCaptions(Sheet sheet, List<ExcelColumn> visibleColumns) throws MaxRowsExceededException
    {
        int column = _numRowAxisCols;

        // Add a row for the column dimension
        if (_includeDimensionHeader)
        {
            Row dimensionRow = sheet.createRow(getCurrentRow());
            Cell dimensionCell = dimensionRow.getCell(column, MissingCellPolicy.CREATE_NULL_AS_BLANK);
            dimensionCell.setCellStyle(getBoldFormat());
            dimensionCell.setCellValue(_table.getSettings().getColumnAxis().getCaption());

            incrementRow();
        }

        // Add the column members above the normal captions
        for (Pair<CrosstabMember, List<DisplayColumn>> group : _groupedByMember)
        {
            CrosstabMember currentMember = group.first;
            List<DisplayColumn> memberColumns = group.second;
            if (memberColumns.isEmpty())
                continue;

            Row row = sheet.getRow(getCurrentRow());
            if (row == null)
            {
                row = sheet.createRow(getCurrentRow());
            }

            if (currentMember != null)
            {
                Cell cell = row.getCell(column, MissingCellPolicy.CREATE_NULL_AS_BLANK);
                cell.setCellStyle(getBoldFormat());
                cell.setCellValue(currentMember.getCaption());
                column += memberColumns.size();
                if (column >= _docType.getMaxColumns())
                {
                    break;
                }
            }
        }

        incrementRow();

        super.renderColumnCaptions(sheet, visibleColumns);
    }

}
