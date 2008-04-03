/*
 * Copyright (c) 2007 LabKey Software Foundation
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

import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.CrosstabMember;
import jxl.write.WritableSheet;
import jxl.write.WriteException;
import jxl.write.Label;

import java.util.List;
import java.sql.ResultSet;

/**
 * Used when exporting a crosstab view to Excel.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Feb 5, 2008
 * Time: 5:01:41 PM
 */
public class CrosstabExcelWriter extends ExcelWriter
{
    private List<CrosstabMember> _colMembers = null;
    private int _numRowAxisCols = 0;
    private int _numMeasures = 0;

    public CrosstabExcelWriter(ResultSet rs, List<DisplayColumn> displayColumns, List<CrosstabMember> colMembers, int numRowAxisCols, int numMeasures)
    {
        super(rs, displayColumns);
        _colMembers = colMembers;
        _numRowAxisCols = numRowAxisCols;
        _numMeasures = numMeasures;
    }

    public void renderColumnCaptions(WritableSheet sheet, List<ExcelColumn> visibleColumns) throws WriteException, MaxRowsExceededException
    {
        //add the column members above the normal captions
        int column = _numRowAxisCols;

        for(CrosstabMember member : _colMembers)
        {
            sheet.addCell(new Label(column, getCurrentRow(), member.getCaption(), getBoldFormat()));
            column += _numMeasures;
        }

        incrementRow();

        super.renderColumnCaptions(sheet, visibleColumns);
    }
}
