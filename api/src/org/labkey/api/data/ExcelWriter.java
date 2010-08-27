/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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

import jxl.HeaderFooter;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.format.PaperSize;
import jxl.format.VerticalAlignment;
import jxl.write.*;
import org.apache.log4j.Logger;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.HttpView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ExcelWriter
{
    public static final int MAX_ROWS = 65535;

    protected ResultSet _rs;
    Map<FieldKey,ColumnInfo> _fieldMap;

    private static Logger _log = Logger.getLogger(ExcelWriter.class);

    private String _sheetName;
    private String _footer;
    private String _filenamePrefix;
    private List<String> _headers;
    private ArrayList<ExcelColumn> _columns = new ArrayList<ExcelColumn>(10);
    private boolean _captionRowFrozen = true;
    private boolean _captionRowVisible = true;

    // Careful: these can't be static.  As a cell format is added to a workbook, it gets assigned an internal index number, so each workbook must have a new one.
    private WritableCellFormat _boldFormat = null;
    private WritableCellFormat _wrappingTextFormat = null;
    private WritableCellFormat _nonWrappingTextFormat = null;

    public static final WritableFont.FontName DEFAULT_FONT = WritableFont.ARIAL;

    private Map<ExcelColumn.ExcelFormatDescriptor, WritableCellFormat> _formatters = new HashMap<ExcelColumn.ExcelFormatDescriptor, WritableCellFormat>();

    private int _currentRow = 0;
    private int _currentSheet = -1;

    private Workbook _template;

    public ExcelWriter()
    {
    }


    public ExcelWriter(ResultSet rs) throws SQLException
    {
        setResultSet(rs);
        createColumns(rs.getMetaData());
    }


    public ExcelWriter(ResultSet rs, List<DisplayColumn> displayColumns)
    {
        setResultSet(rs);
        addDisplayColumns(displayColumns);
    }


    public ExcelWriter(ResultSet rs, Map<FieldKey, ColumnInfo> fieldMap, List<DisplayColumn> displayColumns)
    {
        setResultSet(rs, fieldMap);
        addDisplayColumns(displayColumns);
    }


    public ExcelWriter(DbSchema schema, String query) throws SQLException
    {
        ResultSet rs = Table.executeQuery(schema, new SQLFragment(query), MAX_ROWS);
        setResultSet(rs);
        createColumns(rs.getMetaData());
    }


    public void createColumns(ResultSetMetaData md) throws SQLException
    {
        int columnCount = md.getColumnCount();
        List<ColumnInfo> cols = new ArrayList<ColumnInfo>(columnCount);

        for (int i = 0; i < columnCount; i++)
        {
            int sqlColumn = i + 1;
            cols.add(new ColumnInfo(md, sqlColumn));
        }

        setColumns(cols);
    }


    public void setResultSet(ResultSet rs)
    {
        _rs = rs;
    }

    public void setResultSet(ResultSet rs, Map<FieldKey, ColumnInfo> fieldMap)
    {
        _rs = rs;
        _fieldMap = fieldMap;
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


    public String getSheetName()
    {
        if (null == _sheetName)
            return "data";
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
            return getSheetName();
        else
            return _footer;
    }


    public String getFilenamePrefix()
    {
        if (null == _filenamePrefix)
            return getSheetName().replaceAll(" ", "_");
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


    public List getHeaders()
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
        _columns.add(new ExcelColumn(col, _formatters));
    }


    private void addDisplayColumns(List<DisplayColumn> columns)
    {
        for (DisplayColumn column : columns)
            addColumn(column);
    }


    private void addColumns(List<ColumnInfo> cols)
    {
        for (ColumnInfo col : cols)
            addColumn(new DataColumn(col));
    }


    public void setDisplayColumns(List<DisplayColumn> columns)
    {
        _columns = new ArrayList<ExcelColumn>(10);
        addDisplayColumns(columns);
    }


    public void setColumns(List<ColumnInfo> columns)
    {
        _columns = new ArrayList<ExcelColumn>(10);
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

        List<ExcelColumn> visibleColumns = new ArrayList<ExcelColumn>(_columns.size());

        for (ExcelColumn column : _columns)
        {
            if (column.isVisible(ctx))
                visibleColumns.add(column);
        }

        return visibleColumns;
    }


    // Sets AutoSize property on all columns
    public void setAutoSize(boolean autoSize)
    {
        for (ExcelColumn _column : _columns)
            _column.setAutoSize(autoSize);
    }


    // Write the spreadsheet to the file system.  Used for testing.
    public void write(String fileName)
    {
        try
        {
            WorkbookSettings settings = new WorkbookSettings();
            settings.setArrayGrowSize(300000);
            WritableWorkbook workbook = Workbook.createWorkbook(new File(fileName), settings);
            renderNewSheet(workbook);
            workbook.write();
            workbook.close();
        }
        catch (IOException e)
        {
            _log.error(e);
        }
        catch (WriteException e)
        {
            _log.error(e);
        }
    }

    // Write the spreadsheet to the file system.
    public void write(OutputStream stream)
    {
        try
        {
            WritableWorkbook workbook = (null == _template) ? Workbook.createWorkbook(stream) : Workbook.createWorkbook(stream, _template);
            renderNewSheet(workbook);
            workbook.write();
            stream.flush();
            workbook.close();
        }
        catch (IOException e)
        {
            _log.error(e);
        }
        catch (WriteException e)
        {
            _log.error(e);
        }
    }

    // Create the spreadsheet and stream it to the browser.
    public void write(HttpServletResponse response)
    {
        ServletOutputStream outputStream = getOutputStream(response, getFilenamePrefix());

        // The workbook will be streamed to the outputstream
        WritableWorkbook workbook = getWorkbook(outputStream);

        renderNewSheet(workbook);

        closeWorkbook(workbook, outputStream);
    }


    // Create a ServletOutputStream to stream an Excel workbook to the browser.
    // This streaming code is adapted from Guillaume Laforge's sample posted to the JExcelApi Yahoo!
    // group: http://groups.yahoo.com/group/JExcelApi/message/1692
    public static ServletOutputStream getOutputStream(HttpServletResponse response, String filenamePrefix)
    {
        // Flush any extraneous output (e.g., <CR><LF> from JSPs)
        response.reset();

        // First, set the content-type, so that your browser knows which application to launch
        response.setContentType("application/vnd.ms-excel");

        // Set the content-disposition for two reasons :
        // 1) Specify that it is an attachment, so that your browser will open the file as if
        // it were downloading a file (you get a dialog saying you wish to open or save
        // the workbook). The other choice is to specify "inline" instead of "attachment"
        // so that the file is always opened inside the browser, not outside by launching
        // excel as a standalone application (not embeded)
        // 2) Specify the file name of the workbook with a different file name each time
        // so that your browser doesn't put the generated file into its cache

        String filename = FileUtil.makeFileNameWithTimestamp(filenamePrefix, "xls");
        response.setHeader("Content-disposition", "attachment; filename=\"" + filename +"\"");

        try
        {
            // Get the outputstream of the servlet (BTW, always get the outputstream AFTER you've
            // set the content-disposition and content-type)
            return response.getOutputStream();
        }
        catch (IOException e)
        {
            _log.error(e);
        }

        return null;
    }


    public static WritableWorkbook getWorkbook(ServletOutputStream outputStream)
    {
        return getWorkbook(outputStream, null);
    }

    public static WritableWorkbook getWorkbook(ServletOutputStream outputStream, Workbook template)
    {
        try
        {
            return null == template ? Workbook.createWorkbook(outputStream) : Workbook.createWorkbook(outputStream, template);
        }
        catch (IOException e)
        {
            _log.error(e);
            return null;
        }
    }


    public static void closeWorkbook(WritableWorkbook workbook, ServletOutputStream outputStream)
    {
        try
        {
            workbook.write();
            workbook.close();

            // Flush the outpustream
            outputStream.flush();
            // Finally, close the outputstream
            outputStream.close();
        }
        catch (IOException e)
        {
            _log.error(e);
        }
        catch (WriteException e)
        {
            _log.error(e);
        }
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
        if (_currentRow > MAX_ROWS)
            throw new MaxRowsExceededException();
    }

    public Workbook getTemplate()
    {
        return _template;
    }

    public void setTemplate(Workbook template)
    {
        _template = template;
    }


    public static class MaxRowsExceededException extends Exception
    {
        protected MaxRowsExceededException()
        {
            super("Maximum rows exceeded");
        }
    }


    public void renderNewSheet(WritableWorkbook workbook)
    {
        _currentSheet++;
        _currentRow = 0;
        renderSheet(workbook, _currentSheet);
    }


    public void renderCurrentSheet(WritableWorkbook workbook)
    {
        renderSheet(workbook, _currentSheet);
    }


    public void renderSheet(WritableWorkbook workbook, int sheetNumber)
    {
        WritableSheet sheet;
        //TODO: Pass render context all the way through Excel writers...
        RenderContext ctx = new RenderContext(HttpView.currentContext());

        if (workbook.getNumberOfSheets() > sheetNumber)
        {
            sheet = workbook.getSheet(sheetNumber);
        }
        else
        {
            sheet = workbook.createSheet(getSheetName(), sheetNumber);
            sheet.getSettings().setPaperSize(PaperSize.LETTER);
        }

        List<ExcelColumn> visibleColumns = getVisibleColumns(ctx);

        try
        {
            int firstGridRow = getCurrentRow();

            try
            {
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
                HeaderFooter hf = sheet.getSettings().getFooter();
                hf.getLeft().append("&D");
                hf.getCentre().append(getFooter());
                hf.getRight().append("Page &P/&N");
            }
        }
        catch (WriteException e)
        {
            _log.error("render", e);
        }
        catch (SQLException e)
        {
            _log.error("render", e);
        }
    }


    public void adjustColumnWidths(WritableSheet sheet, int firstGridRow, List visibleColumns)
    {
        int lastGridRow = getCurrentRow() - 1;

        for (int column = visibleColumns.size() - 1; column >= 0; column--)
            ((ExcelColumn) visibleColumns.get(column)).adjustWidth(sheet, column, firstGridRow, lastGridRow);
    }


    public void renderGrid(WritableSheet sheet, ResultSet rs) throws SQLException, WriteException, MaxRowsExceededException
    {
        //TODO: Figure out how to pass this through...
        RenderContext ctx = new RenderContext(HttpView.currentContext());
        renderGrid(sheet, getVisibleColumns(ctx), rs);
    }


    // Initialize non-wrapping text format for this worksheet
    protected WritableCellFormat getWrappingTextFormat() throws WriteException
    {
        if (null == _wrappingTextFormat)
        {
            _wrappingTextFormat = new WritableCellFormat();
            _wrappingTextFormat.setWrap(true);
            _wrappingTextFormat.setVerticalAlignment(VerticalAlignment.TOP);
        }

        return _wrappingTextFormat;
    }


    // Initialize bold format for this worksheet
    protected WritableCellFormat getBoldFormat()
    {
        if (null == _boldFormat)
        {
            WritableFont boldFont = new WritableFont(DEFAULT_FONT, 10, WritableFont.BOLD);
            _boldFormat = new WritableCellFormat(boldFont);
        }

        return _boldFormat;
    }


    // Initialize non-wrapping text format for this worksheet
    protected WritableCellFormat getNonWrappingTextFormat() throws WriteException
    {
        if (null == _nonWrappingTextFormat)
        {
            _nonWrappingTextFormat = new WritableCellFormat();
            _nonWrappingTextFormat.setWrap(false);
            _nonWrappingTextFormat.setVerticalAlignment(VerticalAlignment.TOP);
        }

        return _nonWrappingTextFormat;
    }


    public void renderSheetHeaders(WritableSheet sheet, int columnCount) throws WriteException, MaxRowsExceededException
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
                    int row = getCurrentRow();

                    // Give all the reminaing space to the last column
                    if (j == headerColumnCount - 1)
                        sheet.mergeCells(j * columnWidth, getCurrentRow(), headerWidth - 1, row);
                    else
                        sheet.mergeCells(j * columnWidth, row, (j + 1) * columnWidth - 1, row);

                    WritableCell cell = new Label(j * columnWidth, row, headerColumns[j]);

                    // Wrap text in the case of full-size headers; don't wrap text in column mode.
                    // This helps in cases like "FileName: t:/data/databases/rat051004_NCBI.fasta" in columns.
                    // If text wrap were on, path+filename may appear blank since Excel wraps at spaces.
                    cell.setCellFormat(headerColumnCount > 1 ? getNonWrappingTextFormat() : getWrappingTextFormat());
                    sheet.addCell(cell);
                }

                incrementRow();
            }
        }
    }


    public void renderColumnCaptions(WritableSheet sheet, List<ExcelColumn> visibleColumns) throws WriteException, MaxRowsExceededException
    {
        if (!_captionRowVisible)
            return;

        for (int column = 0; column < visibleColumns.size(); column++)
            visibleColumns.get(column).renderCaption(sheet, getCurrentRow(), column, getBoldFormat());

        incrementRow();

        if (_captionRowFrozen)
            sheet.getSettings().setVerticalFreeze(getCurrentRow());
    }


    public void renderGrid(WritableSheet sheet, List<ExcelColumn> visibleColumns) throws SQLException, WriteException, MaxRowsExceededException
    {
        renderGrid(sheet, visibleColumns, _rs);
    }

    public void renderGrid(WritableSheet sheet, List<ExcelColumn> visibleColumns, ResultSet rs) throws SQLException, WriteException, MaxRowsExceededException
    {
        if (null == rs)
            return;

        try
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
            RenderContext ctx = new RenderContext(HttpView.currentContext());
            ctx.setResultSet(rs, _fieldMap);

            // Output all the rows
            while (rs.next())
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


    protected void renderGridRow(WritableSheet sheet, RenderContext ctx, List<ExcelColumn> columns) throws SQLException, WriteException, MaxRowsExceededException
    {
        int row = getCurrentRow();

        for (int column = 0; column < columns.size(); column++)
            columns.get(column).writeCell(sheet, column, row, ctx);

        incrementRow();
    }
}
