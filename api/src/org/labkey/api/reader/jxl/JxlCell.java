/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.reader.jxl;

import jxl.CellType;
import jxl.DateCell;
import jxl.NumberCell;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.labkey.api.util.DateUtil;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * User: klum
 * Date: May 2, 2011
 * Time: 6:54:03 PM
 */
public class JxlCell implements Cell
{
    private final jxl.Cell _cell;
    private final int _idx;
    private final Row _row;

    public JxlCell(jxl.Cell cell, int idx, Row row)
    {
        _cell = cell;
        _idx = idx;
        _row = row;
    }

    @Override
    public int getColumnIndex()
    {
        return _idx;
    }

    @Override
    public int getRowIndex()
    {
        return _row.getRowNum();
    }

    @Override
    public Sheet getSheet()
    {
        return _row.getSheet();
    }

    @Override
    public Row getRow()
    {
        return _row;
    }

    public jxl.Cell getRawCell()
    {
        return _cell;
    }

    @Override
    public void setCellType(int cellType)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getCellType()
    {
        CellType type = _cell.getType();

        if (type.equals(CellType.EMPTY))
            return Cell.CELL_TYPE_BLANK;
        else if (type.equals(CellType.BOOLEAN))
            return Cell.CELL_TYPE_BOOLEAN;
        else if (type.equals(CellType.ERROR))
            return Cell.CELL_TYPE_ERROR;
        else if (type.equals(CellType.BOOLEAN_FORMULA) || type.equals(CellType.DATE_FORMULA) ||
                type.equals(CellType.NUMBER_FORMULA) || type.equals(CellType.STRING_FORMULA))
            return Cell.CELL_TYPE_FORMULA;
        else if (type.equals(CellType.NUMBER) || type.equals(CellType.DATE))
            return Cell.CELL_TYPE_NUMERIC;
        else if (type.equals(CellType.LABEL))
            return Cell.CELL_TYPE_STRING;

        return Cell.CELL_TYPE_STRING;
    }

    @Override
    public int getCachedFormulaResultType()
    {
        CellType type = _cell.getType();
        if (type.equals(CellType.BOOLEAN_FORMULA))
            return Cell.CELL_TYPE_BOOLEAN;
        else if (type.equals(CellType.DATE_FORMULA) || type.equals(CellType.NUMBER_FORMULA))
            return Cell.CELL_TYPE_NUMERIC;
        else if (type.equals(CellType.STRING_FORMULA))
            return Cell.CELL_TYPE_STRING;

        throw new IllegalStateException("Only formula cells have cached results");
    }

    @Override
    public void setCellValue(double value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setCellValue(Date value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setCellValue(Calendar value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setCellValue(RichTextString value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setCellValue(String value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setCellFormula(String formula) throws FormulaParseException
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public String getCellFormula()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public double getNumericCellValue()
    {
        if (_cell.getType() == CellType.NUMBER)
            return ((NumberCell)_cell).getValue();
        if (_cell.getType() == CellType.DATE)
            return ((DateCell)_cell).getDate().getTime();

        return Double.parseDouble(StringUtils.defaultIfBlank(_cell.getContents(), "0.0"));
    }

    @Override
    public Date getDateCellValue()
    {
        if (_cell.getType() == CellType.EMPTY)
            return null;
        
        if (_cell.getType() == CellType.DATE)
        {
            Date date = ((DateCell)_cell).getDate();

            // JXL date cells have a default time zone of GMT/UTC, for excel import we are adopting
            // the POI standard of java default zones for date fields. Convert the existing date from
            // UTC to the default time zone.
            DateFormat format = new SimpleDateFormat(DateUtil.getStandardDateTimeFormatString());
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            String formatString = format.format(date);
            format.setTimeZone(TimeZone.getDefault());

            try
            {
                return format.parse(formatString);
            }
            catch (ParseException e)
            {
                throw new RuntimeException(e);
            }
        }
        return new Date(_cell.getContents());
    }

    @Override
    public RichTextString getRichStringCellValue()
    {
        switch(getCellType()) {
            case CELL_TYPE_BLANK:
                return new JxlRichTextString("");
            case CELL_TYPE_STRING:
                return new JxlRichTextString(_cell.getContents());
            case CELL_TYPE_FORMULA:
                return null;
            default:
                throw new IllegalStateException("Expected string cell type, got '" + _cell.getType() + "'");
        }
    }

    private static class JxlRichTextString implements RichTextString
    {
        String _string;

        private JxlRichTextString(String string)
        {
            _string = string;
        }

        @Override
        public void applyFont(int startIndex, int endIndex, short fontIndex)
        {
        throw new UnsupportedOperationException("method not yet supported");
        }

        @Override
        public void applyFont(int startIndex, int endIndex, Font font)
        {
            throw new UnsupportedOperationException("method not yet supported");
        }

        @Override
        public void applyFont(short fontIndex)
        {
            throw new UnsupportedOperationException("method not yet supported");
        }

        @Override
        public void applyFont(Font font)
        {
            throw new UnsupportedOperationException("method not yet supported");
        }

        @Override
        public void clearFormatting()
        {
            throw new UnsupportedOperationException("method not yet supported");
        }

        @Override
        public String getString()
        {
            return _string;
        }

        @Override
        public int length()
        {
            return _string.length();
        }

        @Override
        public int numFormattingRuns()
        {
            return 0;
        }

        @Override
        public int getIndexOfFormattingRun(int index)
        {
            return 0;
        }

    }

    @Override
    public String getStringCellValue()
    {
        return _cell.getContents();
    }

    @Override
    public void setCellValue(boolean value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setCellErrorValue(byte value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getBooleanCellValue()
    {
        return Boolean.parseBoolean(_cell.getContents());
    }

    @Override
    public byte getErrorCellValue()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setCellStyle(CellStyle style)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellStyle getCellStyle()
    {
        return new JxlCellStyle(_cell);
    }

    @Override
    public void setAsActiveCell()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setCellComment(Comment comment)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Comment getCellComment()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeCellComment()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Hyperlink getHyperlink()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setHyperlink(Hyperlink link)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellRangeAddress getArrayFormulaRange()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isPartOfArrayFormulaGroup()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setCellType(org.apache.poi.ss.usermodel.CellType cellType)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public org.apache.poi.ss.usermodel.CellType getCellTypeEnum()
    {
        return org.apache.poi.ss.usermodel.CellType.forInt(getCellType());
    }

    @Override
    public org.apache.poi.ss.usermodel.CellType getCachedFormulaResultTypeEnum()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellAddress getAddress()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeHyperlink()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }
}
