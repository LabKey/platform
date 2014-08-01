/*
 * Copyright (c) 2004-2014 Fred Hutchinson Cancer Research Center
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

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Footer;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.HttpView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ExcelWriter implements ExportWriter
{
    public enum CaptionType
    {
        Name {
            @Override
            public String getText(ExcelColumn c)
            {
                return c.getName();
            }},
        Label {
            @Override
            public String getText(ExcelColumn c)
            {
                return c.getCaption();
            }};

        public abstract String getText(ExcelColumn c);
    }

    public enum ExcelDocumentType
    {
        xls
        {
            @Override
            public Workbook createWorkbook()
            {
                return new HSSFWorkbook();
            }

            @Override
            public String getMimeType()
            {
                return "application/" + ExcelFactory.SUB_TYPE_BIFF8; 
            }

            @Override
            public int getMaxRows()
            {
                // Return one less than the Excel max since we'll generally be including at least one header row
                return 65535;
            }

            @Override
            public int getMaxColumns()
            {
                return HSSFCell.LAST_COLUMN_NUMBER + 1;
            }
        },
        xlsx
        {
            @Override
            public Workbook createWorkbook()
            {
                // Always use a streaming workbook, set to flush to disk every 1,000 rows, #14960.
                // Note: if we ever need a non-streaming workbook, create a new enum that constructs an XSSFWorkbook.
                return new SXSSFWorkbook(1000);
            }

            @Override
            public String getMimeType()
            {
                return "application/" + ExcelFactory.SUB_TYPE_XSSF; 
            }

            @Override
            public int getMaxRows()
            {
                // Return one less than the Excel max since we'll generally be including at least one header row
                return 1048575;
            }

            @Override
            public int getMaxColumns()
            {
                return SpreadsheetVersion.EXCEL2007.getLastColumnIndex() + 1;
            }
        };

        public abstract Workbook createWorkbook();
        public abstract String getMimeType();
        /** @return the maximum number of rows to SELECT, which is one less than the document's maximum */
        public abstract int getMaxRows();
        public abstract int getMaxColumns();
    }

    public static final int MAX_ROWS = 65535;

    protected Results _rs;

    protected final ExcelDocumentType _docType;

    private String _sheetName;
    private String _footer;
    private String _filenamePrefix;
    private List<String> _headers;
    private List<String> _commentLines;
    private CaptionType _captionType = CaptionType.Label;
    private boolean _insertableColumnsOnly = false;
    private ArrayList<ExcelColumn> _columns = new ArrayList<>(10);
    private boolean _captionRowFrozen = true;
    private boolean _captionRowVisible = true;

    // Careful: these can't be static.  As a cell format is added to a workbook, it gets assigned an internal index number, so each workbook must have a new one.
    private CellStyle _boldFormat = null;
    private CellStyle _wrappingTextFormat = null;
    private CellStyle _nonWrappingTextFormat = null;

    private Map<ExcelColumn.ExcelFormatDescriptor, CellStyle> _formatters = new HashMap<>();

    /** Total number of data rows exported so far, which may span multiple sheets */
    private int _totalDataRows = 0;

    /** First row to write to when starting a new sheet */
    private int _currentRow = 0;
    private int _currentSheet = -1;

    protected final Workbook _workbook;

    public ExcelWriter()
    {
        this(ExcelDocumentType.xls);
    }

    public ExcelWriter(ExcelDocumentType docType)
    {
        this(docType, null);
    }

    protected ExcelWriter(ExcelDocumentType docType, @Nullable Workbook workbook)
    {
        _docType = docType;
        _workbook = workbook == null ? docType.createWorkbook() : workbook;
    }

    public ExcelWriter(Results rs, List<DisplayColumn> displayColumns, ExcelWriter parentWriter)
    {
        this(parentWriter._docType, parentWriter._workbook);
        _wrappingTextFormat = parentWriter.getWrappingTextFormat();
        _nonWrappingTextFormat = parentWriter.getNonWrappingTextFormat();
        _boldFormat = parentWriter.getBoldFormat();
        _formatters = parentWriter._formatters;

        setResults(rs);
        addDisplayColumns(displayColumns);
    }

    public ExcelWriter(Results rs, List<DisplayColumn> displayColumns, ExcelDocumentType docType)
    {
        this(docType);
        setResults(rs);
        addDisplayColumns(displayColumns);
    }

    public ExcelWriter(Results rs, List<DisplayColumn> displayColumns)
    {
        this(rs, displayColumns, ExcelDocumentType.xls);
    }


    public ExcelWriter(ResultSet rs, Map<FieldKey, ColumnInfo> fieldMap, List<DisplayColumn> displayColumns, ExcelDocumentType docType)
    {
        this(docType);
        setResultSet(rs, fieldMap);
        addDisplayColumns(displayColumns);
    }


    public ExcelWriter(DbSchema schema, String query) throws SQLException
    {
        this(ExcelDocumentType.xls);
        ResultSet rs = new SqlSelector(schema, query).setMaxRows(_docType.getMaxRows()).getResultSet();
        setResultSet(rs);
        createColumns(rs.getMetaData());
    }


    public void setCaptionType(CaptionType type)
    {
        _captionType = type;
    }


    public void setShowInsertableColumnsOnly(boolean b)
    {
        _insertableColumnsOnly = b;
        if (_insertableColumnsOnly)
        {
            // Remove any insert only columns that have already made their way into the list
            Iterator<ExcelColumn> i = _columns.iterator();
            while (i.hasNext())
            {
                ExcelColumn column = i.next();
                if (column.getDisplayColumn() != null && column.getDisplayColumn().getColumnInfo() != null && !column.getDisplayColumn().getColumnInfo().isShownInInsertView())
                {
                    i.remove();
                }
            }
        }
    }


    public void createColumns(ResultSetMetaData md) throws SQLException
    {
        int columnCount = md.getColumnCount();
        List<ColumnInfo> cols = new ArrayList<>(columnCount);

        for (int i = 0; i < columnCount; i++)
        {
            int sqlColumn = i + 1;
            cols.add(new ColumnInfo(md, sqlColumn));
        }

        setColumns(cols);
    }


    public void setResults(Results rs)
    {
        _rs = rs;
    }

    @Deprecated /** Use setResults() */
    public void setResultSet(ResultSet rs)
    {
        _rs = new ResultsImpl(rs);
    }

    @Deprecated /** Use setResults() */
    public void setResultSet(ResultSet rs, Map<FieldKey, ColumnInfo> fieldMap)
    {
        _rs = new ResultsImpl(rs, fieldMap);
    }

    // Sheet names must be 31 characters or shorter, and must not contain \:/[]? or *

    public void setSheetName(String sheetName)
    {
        _sheetName = cleanSheetName(sheetName);
    }


    private static final Pattern badSheetNameChars = Pattern.compile("[\\\\:/\\[\\]\\?\\*\\|]");

    public static String cleanSheetName(String sheetName)
    {
        sheetName = badSheetNameChars.matcher(sheetName).replaceAll("_");
        // CONSIDER: collapse whitespaces and underscores

        if (sheetName.length() > 31)
            sheetName = sheetName.substring(0, 31);

        return sheetName;
    }

    public String getSheetName(int index)
    {
        if (null == _sheetName)
            return "data" + (index == 0 ? "" : Integer.toString(index));
        else
            return _sheetName;
    }

    public void setFooter(String footer)
    {
        _footer = footer;
    }

    public String getFooter()
    {
        if (null == _footer)
            return getSheetName(0);
        else
            return _footer;
    }


    public String getFilenamePrefix()
    {
        if (null == _filenamePrefix)
            return getSheetName(0).replaceAll(" ", "_");
        else
            return _filenamePrefix;
    }


    public void setFilenamePrefix(String filenamePrefix)
    {
        _filenamePrefix = filenamePrefix;
    }


    public void setHeaders(List<String> headers)
    {
        _headers = headers;
    }

    public void setHeaders(String... headers)
    {
        _headers = Arrays.asList(headers);
    }


    public List<String> getHeaders()
    {
        return _headers;
    }


    public boolean isCaptionRowFrozen()
    {
        return _captionRowFrozen;
    }


    public void setCaptionRowFrozen(boolean captionColumnFrozen)
    {
        _captionRowFrozen = captionColumnFrozen;
    }


    public boolean isCaptionRowVisible()
    {
        return _captionRowVisible;
    }


    public void setCaptionRowVisible(boolean captionRowVisible)
    {
        _captionRowVisible = captionRowVisible;
    }


    public ExcelColumn getExcelColumn(int index)
    {
        return _columns.get(index);
    }


    public ExcelColumn getExcelColumn(String columnName)
    {
        for (ExcelColumn column : _columns)
        {
            if (column.getName().equalsIgnoreCase(columnName))
                return column;
        }

        return null;
    }


    private void addColumn(DisplayColumn col)
    {
        _columns.add(new ExcelColumn(col, _formatters, _workbook));
    }


    private void addDisplayColumns(List<DisplayColumn> columns)
    {
        for (DisplayColumn column : columns)
        {
            if (_insertableColumnsOnly && (null == column.getColumnInfo() || !column.getColumnInfo().shownInInsertView))
                continue;
            addColumn(column);
        }
    }


    private void addColumns(List<ColumnInfo> cols)
    {
        for (ColumnInfo col : cols)
        {
            if (_insertableColumnsOnly && !col.shownInInsertView)
                continue;
            addColumn(new DataColumn(col));
        }
    }


    public void setDisplayColumns(List<DisplayColumn> columns)
    {
        _columns = new ArrayList<>(10);
        addDisplayColumns(columns);
    }


    public void setColumns(List<ColumnInfo> columns)
    {
        _columns = new ArrayList<>(10);
        addColumns(columns);
    }


    public List<ExcelColumn> getColumns()
    {
        return _columns;
    }


    public List<ExcelColumn> getVisibleColumns(RenderContext ctx)
    {
        if (null == _columns)
            return null;

        List<ExcelColumn> visibleColumns = new ArrayList<>(_columns.size());

        for (ExcelColumn column : _columns)
        {
            if (column.isVisible(ctx))
                visibleColumns.add(column);

            if (visibleColumns.size() >= _docType.getMaxColumns())
            {
                return visibleColumns;
            }
        }

        return visibleColumns;
    }


    // Sets AutoSize property on all columns
    public void setAutoSize(boolean autoSize)
    {
        for (ExcelColumn _column : _columns)
            _column.setAutoSize(autoSize);
    }

    // Write the spreadsheet to the file system.
    public void write(OutputStream stream)
    {
        try
        {
            renderNewSheet();
            _workbook.write(stream);
            stream.flush();
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }

    public void write(HttpServletResponse response)
    {
        write(response, getFilenamePrefix());
    }

    // Create the spreadsheet and stream it to the browser.
    public void write(HttpServletResponse response, String filenamePrefix)
    {
        ServletOutputStream outputStream = getOutputStream(response, filenamePrefix, _docType);

        // The workbook will be streamed to the outputstream
        renderNewSheet();

        try
        {
            _workbook.write(outputStream);

            // Flush the outpustream
            outputStream.flush();
            // Finally, close the outputstream
            outputStream.close();
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
        catch (OpenXML4JRuntimeException e)
        {
            // We can get this message if there's an IOException when writing out the document to the stream.
            // It happens because the browser has disconnected before receiving the full file. We can safely ignore it.
            // Otherwise, rethrow. See issue #14987
            if (e.getMessage() == null || e.getMessage().contains("to be saved in the stream with marshaller"))
            {
                throw e;
            }
        }
    }

    // Create a ServletOutputStream to stream an Excel workbook to the browser.
    // This streaming code is adapted from Guillaume Laforge's sample posted to the JExcelApi Yahoo!
    // group: http://groups.yahoo.com/group/JExcelApi/message/1692
    public static ServletOutputStream getOutputStream(HttpServletResponse response, String filenamePrefix, ExcelDocumentType docType)
    {
        // Flush any extraneous output (e.g., <CR><LF> from JSPs)
        response.reset();

        // First, set the content-type, so that your browser knows which application to launch
        response.setContentType(docType.getMimeType());

        // Set the content-disposition for two reasons :
        // 1) Specify that it is an attachment, so that your browser will open the file as if
        // it were downloading a file (you get a dialog saying you wish to open or save
        // the workbook). The other choice is to specify "inline" instead of "attachment"
        // so that the file is always opened inside the browser, not outside by launching
        // excel as a standalone application (not embeded)
        // 2) Specify the file name of the workbook with a different file name each time
        // so that your browser doesn't put the generated file into its cache

        String filename = FileUtil.makeFileNameWithTimestamp(filenamePrefix, docType.name());
        response.setHeader("Content-disposition", "attachment; filename=\"" + filename +"\"");

        try
        {
            // Get the outputstream of the servlet (BTW, always get the outputstream AFTER you've
            // set the content-disposition and content-type)
            return response.getOutputStream();
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }

        return null;
    }


    public int getCurrentRow()
    {
        return _currentRow;
    }


    public void setCurrentRow(int currentRow) throws MaxRowsExceededException
    {
        _currentRow = currentRow;
        checkCurrentRow();
    }


    protected void incrementRow() throws MaxRowsExceededException
    {
        _currentRow++;
        checkCurrentRow();
    }


    private void checkCurrentRow()  throws MaxRowsExceededException
    {
        if (_currentRow > _docType.getMaxRows())
            throw new MaxRowsExceededException();
    }

    public static class MaxRowsExceededException extends Exception
    {
        protected MaxRowsExceededException()
        {
            super("Maximum rows exceeded");
        }
    }

    public void renderNewSheet()
    {
        _currentRow = 0;
        _currentSheet++;
        renderSheet(_currentSheet);
    }

    public void renderCurrentSheet()
    {
        renderSheet(_currentSheet);
    }

    public void renderSheet(int sheetNumber)
    {
        Sheet sheet;
        //TODO: Pass render context all the way through Excel writers...
        RenderContext ctx = new RenderContext(HttpView.currentContext());

        if (_workbook.getNumberOfSheets() > sheetNumber)
        {
            sheet = _workbook.getSheetAt(sheetNumber);
        }
        else
        {
            sheet = _workbook.getSheet(getSheetName(sheetNumber));
            if (sheet == null)
            {
                sheet = _workbook.createSheet(getSheetName(sheetNumber));
                sheet.getPrintSetup().setPaperSize(PrintSetup.LETTER_PAPERSIZE);
            }
        }

        List<ExcelColumn> visibleColumns = getVisibleColumns(ctx);

        try
        {
            int firstGridRow = getCurrentRow();

            try
            {
                renderCommentLines(sheet);
                renderSheetHeaders(sheet, visibleColumns.size());
                renderColumnCaptions(sheet, visibleColumns);

                firstGridRow = getCurrentRow();

                renderGrid(sheet, visibleColumns);
            }
            catch(MaxRowsExceededException e)
            {
                // Just continue on
            }

            adjustColumnWidths(sheet, firstGridRow, visibleColumns);

            if (null != getFooter())
            {
                Footer hf = sheet.getFooter();
                hf.setLeft("&D");
                hf.setCenter(getFooter());
                hf.setRight("Page &P/&N");
            }
        }
        catch (SQLException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }


    public void adjustColumnWidths(Sheet sheet, int firstGridRow, List visibleColumns)
    {
        int lastGridRow = getCurrentRow() - 1;

        for (int column = visibleColumns.size() - 1; column >= 0; column--)
            ((ExcelColumn) visibleColumns.get(column)).adjustWidth(sheet, column, firstGridRow, lastGridRow);
    }


    public void renderGrid(Sheet sheet, ResultSet rs) throws SQLException, MaxRowsExceededException
    {
        //TODO: Figure out how to pass this through...
        RenderContext ctx = new RenderContext(HttpView.currentContext());
        renderGrid(sheet, getVisibleColumns(ctx), new ResultsImpl(rs));
    }

    public void renderGrid(Sheet sheet, Results rs) throws SQLException, MaxRowsExceededException
    {
        //TODO: Figure out how to pass this through...
        RenderContext ctx = new RenderContext(HttpView.currentContext());
        renderGrid(sheet, getVisibleColumns(ctx), rs);
    }

    // Initialize non-wrapping text format for this worksheet
    protected CellStyle getWrappingTextFormat()
    {
        if (null == _wrappingTextFormat)
        {
            _wrappingTextFormat = _workbook.createCellStyle();
            _wrappingTextFormat.setWrapText(true);
            _wrappingTextFormat.setVerticalAlignment(CellStyle.VERTICAL_TOP);
        }

        return _wrappingTextFormat;
    }


    // Initialize bold format for this worksheet
    protected CellStyle getBoldFormat()
    {
        if (null == _boldFormat)
        {
            Font boldFont = _workbook.createFont();
            boldFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
            _boldFormat = _workbook.createCellStyle();
            _boldFormat.setFont(boldFont);
        }

        return _boldFormat;
    }


    // Initialize non-wrapping text format for this worksheet
    protected CellStyle getNonWrappingTextFormat()
    {
        if (null == _nonWrappingTextFormat)
        {
            _nonWrappingTextFormat = _workbook.createCellStyle();
            _nonWrappingTextFormat.setWrapText(false);
            _nonWrappingTextFormat.setVerticalAlignment(CellStyle.VERTICAL_TOP);
        }

        return _nonWrappingTextFormat;
    }

    public void renderCommentLines(Sheet sheet) throws MaxRowsExceededException
    {
        if (null != _commentLines)
        {
            for (String line : _commentLines)
            {
                Row row = sheet.getRow(getCurrentRow());
                if (row == null)
                {
                    row = sheet.createRow(getCurrentRow());
                }

                Cell cell = row.getCell(0, Row.CREATE_NULL_AS_BLANK);
                cell.setCellValue("#" + line);

                incrementRow();
            }
        }
    }

    public void renderSheetHeaders(Sheet sheet, int columnCount) throws MaxRowsExceededException
    {
        if (null != _headers)
        {
            // Merge cells at top of sheet and write the headers
            // One or more embedded tabs split a header into equally spaced columns
            for (String header : _headers)
            {
                String[] headerColumns = (null == header ? "" : header).split("\t");
                int headerColumnCount = headerColumns.length;
                int headerWidth = Math.max(columnCount, 10);
                int columnWidth = headerWidth / headerColumnCount;   // Use at least 10 Excel columns for header

                for (int j = 0; j < headerColumnCount; j++)
                {
                    int rowNum = getCurrentRow();

                    Row row = sheet.getRow(getCurrentRow());
                    if (row == null)
                    {
                        row = sheet.createRow(getCurrentRow());
                    }

                    Cell cell = row.getCell(j * columnWidth, Row.CREATE_NULL_AS_BLANK);
                    cell.setCellValue(headerColumns[j]);

                    // Wrap text in the case of full-size headers; don't wrap text in column mode.
                    // This helps in cases like "FileName: t:/data/databases/rat051004_NCBI.fasta" in columns.
                    // If text wrap were on, path+filename may appear blank since Excel wraps at spaces.
                    cell.setCellStyle(headerColumnCount > 1 ? getNonWrappingTextFormat() : getWrappingTextFormat());

                    // Give all the remaining space to the last column
                    if (j == headerColumnCount - 1)
                        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, j * columnWidth, headerWidth - 1));
                    else
                        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, j * columnWidth, (j + 1) * columnWidth - 1));
                }

                incrementRow();
            }
        }
    }


    public void renderColumnCaptions(Sheet sheet, List<ExcelColumn> visibleColumns) throws MaxRowsExceededException
    {
        if (!_captionRowVisible)
            return;

        for (int column = 0; column < visibleColumns.size(); column++)
            visibleColumns.get(column).renderCaption(sheet, getCurrentRow(), column, getBoldFormat(), _captionType);

        incrementRow();

        if (_captionRowFrozen)
            sheet.createFreezePane(0, getCurrentRow());
    }


    public void renderGrid(Sheet sheet, List<ExcelColumn> visibleColumns) throws SQLException, MaxRowsExceededException
    {
        renderGrid(sheet, visibleColumns, _rs);
    }


    public void renderGrid(Sheet sheet, List<ExcelColumn> visibleColumns, Results rs) throws SQLException, MaxRowsExceededException
    {
        if (null == rs)
            return;

        try
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
            RenderContext ctx = new RenderContext(HttpView.currentContext());
            ctx.setResults(rs);

            // Output all the rows, but don't exceed the document's maximum number of rows
            while (rs.next() && _currentRow <= _docType.getMaxRows())
            {
                ctx.setRow(factory.getRowMap(rs));
                renderGridRow(sheet, ctx, visibleColumns);
            }
        }
        finally
        {
            rs.close();
        }
    }


    protected void renderGridRow(Sheet sheet, RenderContext ctx, List<ExcelColumn> columns) throws SQLException, MaxRowsExceededException
    {
        int row = getCurrentRow();
        _totalDataRows++;

        for (int column = 0; column < columns.size(); column++)
            columns.get(column).writeCell(sheet, column, row, ctx);

        incrementRow();
    }

    public Workbook getWorkbook()
    {
        return _workbook;
    }

    public ExcelDocumentType getDocumentType()
    {
        return _docType;
    }

    public List<String> getCommentLines()
    {
        return _commentLines;
    }

    public void setCommentLines(List<String> commentLines)
    {
        _commentLines = commentLines;
    }

    @Override
    public int getDataRowCount()
    {
        return _totalDataRows;
    }
}
