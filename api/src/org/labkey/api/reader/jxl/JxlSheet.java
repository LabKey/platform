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

import org.apache.poi.hssf.util.PaneInformation;
import org.apache.poi.ss.usermodel.AutoFilter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellRange;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Footer;
import org.apache.poi.ss.usermodel.Header;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: May 2, 2011
 * Time: 6:52:27 PM
 */
public class JxlSheet implements Sheet
{
    private final Workbook _workbook;
    private final jxl.Sheet _sheet;

    public JxlSheet(jxl.Sheet sheet, Workbook workbook)
    {
        _sheet = sheet;
        _workbook = workbook;
    }

    @Override
    public Row createRow(int rownum)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeRow(Row row)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Row getRow(int rownum)
    {
        jxl.Cell[] cells = _sheet.getRow(rownum);
        if (cells != null)
            return new JxlRow(cells, rownum, this);

        return null;
    }

    @Override
    public int getPhysicalNumberOfRows()
    {
        return _sheet.getRows();
    }

    @Override
    public int getFirstRowNum()
    {
        return 0;
    }

    @Override
    public int getLastRowNum()
    {
        return _sheet.getRows() - 1;
    }

    @Override
    public void setColumnHidden(int columnIndex, boolean hidden)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isColumnHidden(int columnIndex)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setColumnWidth(int columnIndex, int width)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getColumnWidth(int columnIndex)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setDefaultColumnWidth(int width)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setRepeatingColumns(CellRangeAddress cellRangeAddress)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setRepeatingRows(CellRangeAddress cellRangeAddress)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellRangeAddress getRepeatingColumns()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellRangeAddress getRepeatingRows()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void showInPane(int i, int i2)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getDefaultColumnWidth()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getDefaultRowHeight()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public float getDefaultRowHeightInPoints()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setDefaultRowHeight(short height)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setDefaultRowHeightInPoints(float height)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellStyle getColumnStyle(int column)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int addMergedRegion(CellRangeAddress region)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setVerticallyCenter(boolean value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setHorizontallyCenter(boolean value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getHorizontallyCenter()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getVerticallyCenter()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeMergedRegion(int index)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getNumMergedRegions()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellRangeAddress getMergedRegion(int index)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Iterator<Row> rowIterator()
    {
        return new JxlRowIterator();
    }

    @Override
    public void setAutobreaks(boolean value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setDisplayGuts(boolean value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setDisplayZeros(boolean value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isDisplayZeros()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setFitToPage(boolean value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setRowSumsBelow(boolean value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setRowSumsRight(boolean value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getAutobreaks()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getDisplayGuts()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getFitToPage()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getRowSumsBelow()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getRowSumsRight()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isPrintGridlines()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setPrintGridlines(boolean show)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public PrintSetup getPrintSetup()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Header getHeader()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Footer getFooter()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setSelected(boolean value)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public double getMargin(short margin)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setMargin(short margin, double size)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getProtect()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void protectSheet(String password)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getScenarioProtect()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getTopRow()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public short getLeftCol()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void shiftRows(int startRow, int endRow, int n)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void shiftRows(int startRow, int endRow, int n, boolean copyRowHeight, boolean resetOriginalRowHeight)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void createFreezePane(int colSplit, int rowSplit, int leftmostColumn, int topRow)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void createFreezePane(int colSplit, int rowSplit)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void createSplitPane(int xSplitPos, int ySplitPos, int leftmostColumn, int topRow, int activePane)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public PaneInformation getPaneInformation()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setDisplayGridlines(boolean show)
    {
        _sheet.getSettings().setShowGridLines(show);
    }

    @Override
    public boolean isDisplayGridlines()
    {
        return _sheet.getSettings().getShowGridLines();
    }

    @Override
    public void setDisplayFormulas(boolean show)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isDisplayFormulas()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setDisplayRowColHeadings(boolean show)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isDisplayRowColHeadings()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setRowBreak(int row)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isRowBroken(int row)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeRowBreak(int row)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int[] getRowBreaks()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int[] getColumnBreaks()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setColumnBreak(int column)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isColumnBroken(int column)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeColumnBreak(int column)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setColumnGroupCollapsed(int columnNumber, boolean collapsed)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void groupColumn(int fromColumn, int toColumn)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void ungroupColumn(int fromColumn, int toColumn)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void groupRow(int fromRow, int toRow)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void ungroupRow(int fromRow, int toRow)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setRowGroupCollapsed(int row, boolean collapse)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setDefaultColumnStyle(int column, CellStyle style)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void autoSizeColumn(int column)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void autoSizeColumn(int column, boolean useMergedCells)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Drawing createDrawingPatriarch()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Workbook getWorkbook()
    {
        return _workbook;
    }

    @Override
    public String getSheetName()
    {
        return _sheet.getName();
    }

    @Override
    public boolean isSelected()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellRange<? extends Cell> setArrayFormula(String formula, CellRangeAddress range)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellRange<? extends Cell> removeArrayFormula(Cell cell)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public DataValidationHelper getDataValidationHelper()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void addValidationData(DataValidation dataValidation)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public AutoFilter setAutoFilter(CellRangeAddress range)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Iterator<Row> iterator()
    {
        return new JxlRowIterator();
    }

    @Override
    public void setRightToLeft(boolean b)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isRightToLeft()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setForceFormulaRecalculation(boolean b)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean getForceFormulaRecalculation()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public SheetConditionalFormatting getSheetConditionalFormatting()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public float getColumnWidthInPixels(int i)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int addMergedRegionUnsafe(CellRangeAddress cellRangeAddress)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void validateMergedRegions()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void removeMergedRegions(Collection<Integer> collection)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public List<CellRangeAddress> getMergedRegions()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public boolean isPrintRowAndColumnHeadings()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setPrintRowAndColumnHeadings(boolean b)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setZoom(int i)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Comment getCellComment(CellAddress cellAddress)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Map<CellAddress, ? extends Comment> getCellComments()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Drawing getDrawingPatriarch()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public List<? extends DataValidation> getDataValidations()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public int getColumnOutlineLevel(int i)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Hyperlink getHyperlink(int i, int i1)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public Hyperlink getHyperlink(CellAddress cellAddress)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public List<? extends Hyperlink> getHyperlinkList()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public CellAddress getActiveCell()
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    @Override
    public void setActiveCell(CellAddress cellAddress)
    {
        throw new UnsupportedOperationException("method not yet supported");
    }

    protected class JxlRowIterator implements Iterator<Row>
    {
        private int _current;
        private int _last;

        public JxlRowIterator()
        {
            _last = _sheet.getRows();
        }

        @Override
        public boolean hasNext()
        {
            return _current < _last;
        }

        @Override
        public Row next()
        {
            return getRow(_current++);
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException("remove not supported for JxlRowIterator");
        }
    }
}
