/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.api.reader;

import org.apache.commons.collections4.iterators.ArrayIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.formula.eval.NotImplementedException;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JunitUtil;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Data loader for Excel files -- can infer columns and return rows of data
 */
public class ExcelLoader extends DataLoader
{
    public static FileType FILE_TYPE = new FileType(Arrays.asList(".xlsx", ".xls"), ".xlsx",
            Arrays.asList("application/" + ExcelFactory.SUB_TYPE_BIFF8, "application/" + ExcelFactory.SUB_TYPE_XSSF, "application/" + ExcelFactory.SUB_TYPE_BIFF5));
    private Boolean _isStartDate1904 = null;

    static {
        FILE_TYPE.setExtensionsMutuallyExclusive(false);
    }

    private static final Pattern EXCEL_TIME_PATTERN = Pattern.compile("^[\\[\\]hHmMsS :]+0*[ampAMP/]*(;@)?$");

    public static class Factory extends AbstractDataLoaderFactory
    {
        @NotNull @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new ExcelLoader(file, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new ExcelLoader(is, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull @Override
        public FileType getFileType() { return FILE_TYPE; }
    }

    public static boolean isExcel(final File dataFile)
    {
        try
        {
            return ExcelLoader.FILE_TYPE.isType(dataFile, null, FileUtil.readHeader(dataFile, 8 * 1024));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    private Workbook _workbook = null;
    private ExcelFactory.WorkbookMetadata _workbookMetadata = null;

    private String sheetName;
    private Integer sheetIndex;

    // For Excel sheets that don't have column headers as the first line, the column types can all be seen as Strings.
    // In that case, the cell contents in readFields() might not get the expected values for numeric columns.
    private boolean useColumnFormats = true;

    // keep track if we created a temp file
    private boolean shouldDeleteFile = false;


    private ExcelLoader()
    {}

    public static ExcelLoader create(Path path, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        if (path.getFileSystem() == FileSystems.getDefault())
            return new ExcelLoader(path.toFile(), hasColumnHeaders, mvIndicatorContainer);
        return new ExcelLoader(Files.newInputStream(path), hasColumnHeaders, mvIndicatorContainer);
    }

    public ExcelLoader(File file) throws IOException
    {
        this(file, false);
    }

    public ExcelLoader(File file, boolean hasColumnHeaders) throws IOException
    {
        this(file, hasColumnHeaders, null);
    }

    public ExcelLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);
        setScrollable(false);

        // NOTE: If we don't create a temp file, ExcelFactory will.
        // Create here so we can call getMetadata() and then stream sheets in loadSheetFromXLSX().
        _file = FileUtil.createTempFile("excel", "tmp", true);
        shouldDeleteFile = true;
        try
        {
            FileUtil.copyData(is, _file);
        }
        catch (IOException x)
        {
            FileUtil.deleteTempFile(_file);
            _file = null;
            throw x;
        }
    }

    public ExcelLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);
        setSource(file);
        setScrollable(true);
    }

    public ExcelLoader(File file, ExcelFactory.WorkbookMetadata md, boolean hasColumnHeaders, Container mvIndicatorContainer)
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);
        _file = file;
        _workbookMetadata = md;
        _workbook = md.getWorkbook();
        setScrollable(true);
    }

    // Issue 42244: From the Excel documentation:
    // "Excel supports two date systems. Each date system uses a unique starting date from which all other workbook
    // dates are calculated. Excel 2008 for Mac and earlier Excel for Mac versions calculate dates based on the 1904
    // date system. Excel for Mac 2011 uses the 1900 date system, which guarantees date compatibility with Excel for
    // Windows. All versions of Excel for Windows calculate dates based on the 1900 date system."
    //
    // When using the SAXParser, we need to know whether this current workbook/sheet is 1904-base or 1900-based.
    // We are not using getDateCellValue in that case, which knows how to determine which date system is in use, so
    // we hack up our own way to detect this.  This is derived from this posting:
    // https://alandix.com/code2/apache-poi-detect-1904-date-option/
    void computeIsStartDate1904()
    {
        try
        {
            Boolean isStart1904 = getWorkbookMetadata(null).isStart1904();
            if (null == isStart1904)
            {
                Sheet sheet = getSheet();
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                Cell cell = row.createCell(0);
                cell.setCellValue(0.0);
                Date date = cell.getDateCellValue();
                Calendar cal = new GregorianCalendar();
                cal.setTime(date);
                long year1900 = cal.get(Calendar.YEAR)-1900;
                isStart1904 =  year1900 > 0;
                sheet.removeRow(row);
            }
            _isStartDate1904 = isStart1904;
        }
        catch (IOException x)
        {
            _isStartDate1904 = false;
        }
    }

    private boolean getIsStartDate1904()
    {
        if (_isStartDate1904 == null)
            computeIsStartDate1904();
        return _isStartDate1904;
    }

    public ExcelFactory.WorkbookMetadata getWorkbookMetadata(@Nullable OPCPackage opc) throws IOException
    {
        try
        {
            if (null == _workbookMetadata && null == _workbook && (null != _file || null != opc))
            {
                // if we already have an OPCPackage we can reuse it
                if (null != opc)
                    _workbookMetadata = ExcelFactory.getMetadata(opc);
                else
                    _workbookMetadata = ExcelFactory.getMetadata(_file);
                if (null != _workbookMetadata.getWorkbook())
                    _workbook = _workbookMetadata.getWorkbook();
            }
            if (null == _workbookMetadata)
                _workbookMetadata = ExcelFactory.getMetadata(getWorkbook());
            return _workbookMetadata;
        }
        catch (InvalidFormatException e)
        {
            throw new ExcelFormatException(e);
        }
    }


    private Workbook getWorkbook() throws IOException
    {
        if (null == _workbook)
        {
            try
            {
                _workbook = ExcelFactory.create(_file);
            }
            catch (InvalidFormatException e)
            {
                throw new ExcelFormatException(e);
            }
        }
        return _workbook;
    }


    public List<String> getSheetNames() throws IOException
    {
        return getWorkbookMetadata(null).getSheetNames();
    }


    public void setSheetName(String sheetName)
    {
        this.sheetName = sheetName;
        this.sheetIndex = null;
    }


    public void setSheetIndex(int index)
    {
        this.sheetName = null;
        this.sheetIndex = index;
    }

    public void setUseColumnFormats(boolean useColumnFormats)
    {
        this.useColumnFormats = useColumnFormats;
    }


    private Sheet getSheet() throws IOException
    {
        try
        {
            Workbook workbook = getWorkbook();
            if (sheetName != null)
                return workbook.getSheet(sheetName);
            else
                return workbook.getSheetAt(Objects.requireNonNullElse(sheetIndex, 0));
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            throw new IOException("Invalid Excel file");
        }
    }


    public boolean sheetMatches(int index, String name)
    {
        if (sheetName != null)
            return StringUtils.equals(sheetName, name);
        else if (sheetIndex != null)
            return  sheetIndex == index;
        else
            return index == 0;
    }


    @Override
    public String[][] getFirstNLines(int n) throws IOException
    {
        if (null != _file)
        {
            try
            {
                return getFirstNLinesXLSX(n);
            }
            catch (InvalidFormatException x)
            {
                /* fall through */
            }
        }

        try
        {
            return getFirstNLinesXLS(n);
        }
        catch (NotImplementedException e)
        {
            throw new IOException("Unable to open Excel file: " + (e.getMessage() == null ? "No specific error information available" : e.getMessage()), e);
        }
    }


    @NotNull
    private String[][] getFirstNLinesXLSX(int n) throws IOException, InvalidFormatException
    {
        int row = -1;

        List<Object[]> grid = new ArrayList<>();

        try (AsyncXlsxIterator iter = new AsyncXlsxIterator())
        {
            while (row < n && iter.hasNext())
            {
                grid.add(iter.next().toArray());
            }
        }

        List<String[]> cells = new ArrayList<>();

        for (int i = 0; cells.size() < n && i<grid.size() ; i++)
        {
            Object[] currentRow = grid.get(i);
            List<String> rowData = new ArrayList<>(currentRow.length);
            boolean foundData = false;

            for (Object v : currentRow)
            {
                String data = (v != null && !(v instanceof String)) ? String.valueOf(v) : (String) v;
                if (!StringUtils.isEmpty(data))
                    foundData = true;
                rowData.add(data != null ? data : "");
            }
            if (foundData)
                cells.add(rowData.toArray(new String[0]));
        }

        return cells.toArray(new String[0][]);
    }


    public String[][] getFirstNLinesXLS(int n) throws IOException
    {
        Sheet sheet = getSheet();
        List<String[]> cells = new ArrayList<>();

        for (Row currentRow : sheet)
        {
            List<String> rowData = new ArrayList<>();

            // Excel can report back more rows than exist. If we find no data at all, we should not add a row.
            boolean foundData = false;

            if (currentRow.getPhysicalNumberOfCells() != 0)
            {
                for (int column = 0; column < currentRow.getLastCellNum(); column++)
                {
                    Cell cell = currentRow.getCell(column);
                    if (cell != null)
                    {
                        Object value = PropertyType.getFromExcelCell(cell);

                        String data;

                        // Use ISO format instead of Date.toString(), #21232
                        if (value instanceof Date)
                            data = DateUtil.formatIsoDateShortTime((Date)value);
                        else
                            data = String.valueOf(value);

                        if (data != null && !data.isEmpty())
                            foundData = true;

                        rowData.add(data != null ? data : "");
                    }
                    else
                        rowData.add("");
                }
                if (foundData)
                    cells.add(rowData.toArray(new String[0]));
            }
            if (--n == 0)
                break;
        }

        return cells.toArray(new String[cells.size()][]);
    }


    @Override
    protected DataLoader.DataLoaderIterator _iterator(boolean includeRowHash)
    {
        try
        {
            var md = getWorkbookMetadata(null);
            if (md.isSpreadSheetML())
                return new XlsxIterator();
            else
                return new ExcelIterator();
        }
        catch (InvalidFormatException | IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void close()
    {
        if (_workbook != null)
        {
            try
            {
                _workbook.close();
            }
            catch (IOException ignore)
            {
            }
        }

        if (shouldDeleteFile && null != _file)
            FileUtil.deleteTempFile(_file);
    }


    private static class StopLoadingRows extends RuntimeException
    {}

    private class AsyncXlsxIterator implements CloseableIterator<List<Object>>
    {
        public static final String THREAD_NAME_PREFIX = "Async XLSX parser: ";
        private final BlockingQueue<List<Object>> _queue;
        private final Thread _asyncThread;
        private OPCPackage _xlsxPackage;
        private volatile RuntimeException _exception;
        private volatile boolean _complete = false;

        private List<Object> _nextRow;

        public AsyncXlsxIterator() throws IOException, InvalidFormatException
        {
            _queue = new ArrayBlockingQueue<>(4);
            _asyncThread = startAsyncParsing();
        }

        private Thread startAsyncParsing() throws IOException, InvalidFormatException
        {
            try
            {
                _xlsxPackage = OPCPackage.open(_file.getPath(), PackageAccess.READ);
                ExcelFactory.WorkbookMetadata md = getWorkbookMetadata(_xlsxPackage);
                String[] strings = md.getSharedStrings();
                XSSFReader xssfReader = new XSSFReader(_xlsxPackage);
                StylesTable styles = xssfReader.getStylesTable();
                XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();

                Runnable runnable = () -> {
                    int sheetIndex = -1;
                    while (iter.hasNext())
                    {
                        InputStream stream = iter.next();
                        sheetIndex++;
                        // DO NOT CALL getSheet() for XLSX since it loads all rows into memory!
                        if (sheetMatches(sheetIndex, iter.getSheetName()))
                        {
                            InputSource sheetSource = new InputSource(stream);
                            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
                            try
                            {
                                SAXParser saxParser = saxFactory.newSAXParser();
                                XMLReader sheetParser = saxParser.getXMLReader();
                                SheetHandler handler = new SheetHandler(styles, strings, _queue, getIsStartDate1904());
                                sheetParser.setContentHandler(handler);
                                sheetParser.parse(sheetSource);
                            }
                            catch (StopLoadingRows slr)
                            {
                                /* no problem */
                            }
                            catch (Exception x)
                            {
                                _exception = new RuntimeException(x);
                            }
                            break;
                        }
                    }
                    _complete = true;
                    synchronized (_queue)
                    {
                        _queue.notify();
                    }
                };

                Thread parsingThread = new Thread(runnable, THREAD_NAME_PREFIX + _file.getName());
                parsingThread.start();
                return parsingThread;
            }
            catch (POIXMLException x)
            {
                throw new InvalidFormatException("File is not a valid xlsx file: " + _file.getName() + (x.getMessage() == null ? "" : x.getMessage()));
            }
            catch (InvalidOperationException | UnsupportedFileFormatException x)
            {
                throw new InvalidFormatException("File is not an xlsx file: " + _file.getPath());
            }
            catch (InvalidFormatException | IOException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                throw new IOException(x);
            }
        }

        @Override
        public void close()
        {
            if (_xlsxPackage != null)
            {
                _xlsxPackage.revert();
            }
            _asyncThread.interrupt();
            try
            {
                _asyncThread.join(TimeUnit.SECONDS.toMillis(5));
            }
            catch (InterruptedException ignored) {}
            if (_asyncThread.isAlive())
            {
                throw new IllegalStateException("Async thread still alive");
            }
        }

        @Override
        public boolean hasNext()
        {
            if (_nextRow != null)
            {
                return true;
            }

            do
            {
                try
                {
                    _nextRow = _queue.poll(1, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException ignored)
                {

                }
            }
            while (!_complete && _nextRow == null && _exception == null);

            if (_exception != null)
            {
                throw _exception;
            }

            return _nextRow != null;
        }

        @Override
        public List<Object> next()
        {
            if (_nextRow == null)
            {
                throw new NoSuchElementException();
            }
            var result = _nextRow;
            _nextRow = null;
            return result;
        }
    }

    private class XlsxIterator extends DataLoaderIterator
    {
        private final AsyncXlsxIterator _asyncIter;
        XlsxIterator() throws IOException, InvalidFormatException
        {
            super(_skipLines == -1 ? 1 : _skipLines);
            _asyncIter = new AsyncXlsxIterator();

            int skipLines = _skipLines == -1 ? 1 : _skipLines;

            for (int i = 0; i < skipLines && _asyncIter.hasNext(); i++)
            {
                _asyncIter.next();
            }
        }

        @Override
        public void close() throws IOException
        {
            super.close();
            _asyncIter.close();
        }

        @Override
        protected Object[] readFields() throws IOException
        {
            if (!_asyncIter.hasNext())
            {
                return null;
            }

            ColumnDescriptor[] allColumns = getColumns();
            List<Object> row = _asyncIter.next();
            Object[] fields = new Object[_activeColumns.length];
            for (int columnIndex = 0, fieldIndex = 0; columnIndex < row.size() && columnIndex < allColumns.length; columnIndex++)
            {
                // UNDONE: it seems to me that DataLoader should handle .load==false
                ColumnDescriptor cd = allColumns[columnIndex];
                if (!cd.load)
                    continue;
                fields[fieldIndex++] = row.get(columnIndex);
            }
            return fields;
        }
    }


    private class ExcelIterator extends DataLoader.DataLoaderIterator
    {
        private final Sheet sheet;
        private final int numRows;

        ExcelIterator() throws IOException
        {
            super(_skipLines == -1 ? 1 : _skipLines);

            sheet = getSheet();
            numRows = sheet.getLastRowNum() + 1;
        }

        @Override
        protected Object[] readFields() throws IOException
        {
            if (lineNum() >= numRows)
                return null;

            ColumnDescriptor[] allColumns = getColumns();
            Iterator<ColumnDescriptor> columnIter = new ArrayIterator<>(allColumns);
            Object[] fields = new Object[_activeColumns.length];

            Row row = sheet.getRow(lineNum());
            if (row != null)
            {
                int numCols = row.getLastCellNum();
                for (int columnIndex = 0, fieldIndex = 0; columnIndex < allColumns.length; columnIndex++)
                {
                    boolean loadThisColumn = ((columnIter.hasNext() && columnIter.next().load));

                    if (loadThisColumn)
                    {
                        ColumnDescriptor column = _activeColumns[fieldIndex];
                        Object contents;

                        if (columnIndex < numCols) // We can get asked for more data than we contain, as extra columns can exist
                        {
                            Cell cell = row.getCell(columnIndex);
                            if (cell == null)
                            {
                                contents = "";
                            }
                            else if (useColumnFormats && column.clazz.equals(String.class))
                            {
                                contents = ExcelFactory.getCellStringValue(cell);
                            }
                            else
                            {
                                contents = PropertyType.getFromExcelCell(cell);
                            }
                        }
                        else
                        {
                            contents = "";
                        }
                        fields[fieldIndex++] = contents;
                    }
                }
            }
            return fields;
        }
    }


    /**
     * The main point of this routine is to return a full-fidelity string version of the passed in value.
     * However, when copying a numeric value in a spreadsheet to a string column in the database, it would
     * be nice to use a decent default format.
     *
     * If the caller REALLY cares about the format, it should probably set useFormats==true
     * @param value object to convert
     * @return string representation
      */
    String _toString(Object value, boolean isNumberFormat)
    {
        if (null == value)
            return "";
        String toStringValue = value.toString();
        if (!isNumberFormat || !StringUtils.contains(toStringValue,'.'))
            return toStringValue;

        try
        {
            // try to convert to double and reformat without trailing ...00000001 or ...99999999.
            double d = Double.parseDouble(toStringValue);
            double pos = Math.abs(d);
            if (pos == 0.0)
                return "0";
            if (Double.isFinite(d) && pos <= 9_007_199_254_740_992L && Math.log10(pos)>=-6)
            {
                String formatValue = df.format(d);
                if (d == Double.parseDouble(formatValue))
                    return formatValue;
            }
        }
        catch (NumberFormatException x)
        {
            /* pass */
        }
        return toStringValue;
    }

    DecimalFormat df = new DecimalFormat("###0.#####################");


    public static class ExcelLoaderTestCase extends Assert
    {
        String[] line0 = new String[] { "date", "scan", "time", "mz", "accurateMZ", "mass", "intensity", "charge", "chargeStates", "kl", "background", "median", "peaks", "scan\nFirst", "scanLast", "scanCount", "totalIntensity", "description" };
        String[] line1Xlsx = new String[] { "2006-01-02 00:00:00", "96", "1543.3400999999999", "858.32460000000003", "false", "1714.6346000000001", "2029.6295", "2", "1", "0.19630893999999999", "26.471083", "12.982442000000001", "4", "92", "100", "9", "20248.761999999999", "description" };
        String[] line1Xls = new String[] { "2006-01-02 00:00", "96.0", "1543.3401", "858.3246", "false", "1714.6346", "2029.6295", "2.0", "1.0", "0.19630894", "26.471083", "12.982442", "4.0", "92.0", "100.0", "9.0", "20248.762", "description"
        };
        String[] line7Xlsx = new String[] { "2006-01-02 00:00:00", "249", "1724.5541000000001", "773.42174999999997", "false", "1544.829", "5.9057474000000001", "2", "1", "0.51059710000000003", "0.67020833000000002", "1.4744527000000001", "2", "246", "250", "5", "29.369174999999998" };
        String[] line7Xls = new String[] { "2006-01-02 00:00", "249.0", "1724.5541", "773.42175", "false", "1544.829", "5.9057474", "2.0", "1.0", "0.5105971", "0.67020833", "1.4744527", "2.0", "246.0", "250.0", "5.0", "29.369175" };

        @Test
        public void detect() throws Exception
        {
            File excelSamplesRoot = JunitUtil.getSampleData(null, "dataLoading/excel");

            assertTrue(isExcel(new File(excelSamplesRoot, "ExcelLoaderTest.xls")));
            assertTrue(isExcel(new File(excelSamplesRoot, "SimpleExcelFile.xls")));
            assertTrue(isExcel(new File(excelSamplesRoot, "SimpleExcelFile.xlsx")));
            assertTrue(isExcel(new File(excelSamplesRoot, "fruits.xls")));
            assertTrue(isExcel(new File(excelSamplesRoot, "DatesWithSeconds.xlsx")));

            // Issue 22153: detect xls file without extension
            assertTrue(isExcel(JunitUtil.getSampleData(null, "Nab/seaman/MS010407")));

            // NOTE: DataLoaderService only available when running junit tests with running server
            DataLoaderService svc = DataLoaderService.get();
            if (svc != null)
            {
                DataLoaderFactory factory = svc.findFactory(JunitUtil.getSampleData(null, "Nab/seaman/MS010407"), null);
                assertTrue(factory instanceof ExcelLoader.Factory);
            }

            assertFalse(isExcel(new File(excelSamplesRoot, "notreallyexcel.xls")));
            assertFalse(isExcel(new File(excelSamplesRoot, "notreallyexcel.xlsx")));
            assertFalse(isExcel(new File(excelSamplesRoot, "fruits.tsv")));
        }

        @Test
        public void testColumnTypes() throws Exception
        {
            try (ExcelLoader loader = getExcelLoader("ExcelLoaderTest.xls"))
            {
                checkColumnMetadata(loader);
                checkData(loader);
            }
        }

        @Test
        public void testColumnTypesXlsx() throws Exception
        {
            try (ExcelLoader loader = getExcelLoader("ExcelLoaderTest.xlsx"))
            {
                checkColumnMetadata(loader);
                checkData(loader);
            }
            ensureAsyncThreads(0);
        }

        @Test
        public void testBogusXlsx() throws Exception
        {
            try
            {
                try (ExcelLoader loader = getExcelLoader("notreallyexcel.xlsx"))
                {
                    loader.iterator();
                }
                fail();
            }
            catch (RuntimeException ignored)
            {

            }
            ensureAsyncThreads(0);
        }

        @Test
        public void testExtraHasNext() throws Exception
        {
            for (String file : Arrays.asList("ExcelLoaderTest.xlsx", "ExcelLoaderTest.xls"))
            {
                try (ExcelLoader loader = getExcelLoader(file))
                {
                    try (var iter = loader.iterator())
                    {
                        int rowCount = 0;
                        while (iter.hasNext())
                        {
                            assertTrue(iter.hasNext());
                            iter.next();
                            rowCount++;
                        }
                        assertEquals("Wrong row count for " + file, 7, rowCount);
                    }
                }
                ensureAsyncThreads(0);
            }
        }

        @Test
        public void testPartialXlsxParsing() throws Exception
        {
            try (ExcelLoader loader = getExcelLoader("ExcelLoaderTest.xlsx"))
            {
                try (var iter = loader.iterator())
                {
                    assertTrue(iter.hasNext());
                    ensureAsyncThreads(1);
                    Map<String, Object> row = iter.next();
                    checkFirstRow(row);
                }
            }
            ensureAsyncThreads(0);
        }

        @Test
        public void testFirstNLinesXlsx() throws Exception
        {
            try (ExcelLoader loader = getExcelLoader("ExcelLoaderTest.xlsx"))
            {
                verifyLines(loader, line1Xlsx, line7Xlsx);
            }
            ensureAsyncThreads(0);
        }

        private void ensureAsyncThreads(int expected)
        {
            int actual = 0;
            for (Map.Entry<Thread, StackTraceElement[]> threadEntry : Thread.getAllStackTraces().entrySet())
            {
                Thread t = threadEntry.getKey();
                if (t.isAlive() && t.getName().startsWith(AsyncXlsxIterator.THREAD_NAME_PREFIX))
                {
                    actual++;
                }
            }
            assertEquals("Unexpected number of async parsing threads", expected, actual);
        }

        @Test
        public void testFirstNLinesXls() throws Exception
        {
            try (ExcelLoader loader = getExcelLoader("ExcelLoaderTest.xls"))
            {
                verifyLines(loader, line1Xls, line7Xls);
            }
        }

        private void verifyLines(ExcelLoader loader, String[] expectedLine1, String[] expectedLine7) throws IOException
        {
            String[][] lines = loader.getFirstNLines(5);
            assertEquals(5, lines.length);
            assertArrayEquals(line0, lines[0]);
            assertArrayEquals(expectedLine1, lines[1]);

            lines = loader.getFirstNLines(20);
            assertEquals(8, lines.length);
            assertArrayEquals(line0, lines[0]);
            assertArrayEquals(expectedLine1, lines[1]);
            assertArrayEquals(expectedLine7, lines[7]);
        }

        private ExcelLoader getExcelLoader(String filename) throws IOException
        {
            File excelSamplesRoot = JunitUtil.getSampleData(null, "dataLoading/excel");

            if (!excelSamplesRoot.canRead())
                throw new IOException("Could not read excel samples in: " + excelSamplesRoot);

            File excelFile = new File(excelSamplesRoot, filename);

            return new ExcelLoader(excelFile, true);
        }

        private static void checkColumnMetadata(ExcelLoader loader) throws IOException
        {
            ColumnDescriptor[] columns = loader.getColumns();

            assertEquals(18, columns.length);

            assertEquals(columns[0].clazz, Date.class);
            assertEquals(columns[1].clazz, Integer.class);
            assertEquals(columns[2].clazz, Double.class);

            assertEquals(columns[4].clazz, Boolean.class);

            assertEquals(columns[17].clazz, String.class);
        }

        private static void checkData(ExcelLoader loader)
        {
            List<Map<String, Object>> data = loader.load();

            assertEquals(7, data.size());

            for (Map<String, Object> map : data)
            {
                assertEquals(18, map.size());
            }

            Map<String, Object> firstRow = data.get(0);
            checkFirstRow(firstRow);
        }

        private static void checkFirstRow(Map<String, Object> firstRow)
        {
            assertEquals(96, firstRow.get("scan"));
            assertEquals(92, firstRow.get("scan First"));
            assertFalse((boolean) firstRow.get("accurateMZ"));
            assertEquals("description", firstRow.get("description"));
        }

        @Test
        public void testDoubleToString()
        {
            try (ExcelLoader xl = new ExcelLoader())
            {
                assertEquals("0", xl._toString("0.0", true));
                assertEquals("1", xl._toString("1.0", true));
                assertEquals(String.valueOf(Integer.MAX_VALUE), xl._toString(Integer.MAX_VALUE, true));
                assertEquals("1.572", xl._toString("1.5720000000000001", true));
                assertEquals("2.441", xl._toString("2.4409999999999998", true));
                assertEquals("1.5720000000000001", xl._toString("1.5720000000000001", false));
                assertEquals("2.4409999999999998", xl._toString("2.4409999999999998", false));
                assertEquals("0.000001572", xl._toString("0.000001572", true));
            }
        }

        @Test
        public void testDatesWithSeconds() throws Exception
        {
            try (ExcelLoader loader = getExcelLoader("DatesWithSeconds.xlsx"))
            {
                String date1 = "2023-01-02 09:14:34";
                String date2 = "2023-01-02 15:14:15";
                String date3 = "2023-01-02 09:28:30";

                String[][] rows = loader.getFirstNLines(4);
                assertEquals(date1, rows[1][0]);
                assertEquals(date2, rows[2][0]);
                assertEquals(date3, rows[3][0]);

                List<Map<String, Object>> list = loader.load();
                assertEquals(3, list.size());
                assertEquals(date1, formatDate(list.get(0)));
                assertEquals(date2, formatDate(list.get(1)));
                assertEquals(date3, formatDate(list.get(2)));
            }
        }

        private String formatDate(Map<String, Object> map)
        {
            return DateUtil.formatIsoDateLongTime((Date)map.get("MyDate"));
        }
    }


    /* code modified from example code found XLS2CSV.java
     * http://svn.apache.org/repos/asf/poi/trunk/src/examples/src/org/apache/poi/xssf/eventusermodel/XLSX2CSV.java
     */

/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

    enum xssfDataType
    {
        BOOL,
        ERROR,
        FORMULA,
        INLINESTR,
        SSTINDEX,
        NUMBER,
    }

    class SheetHandler extends DefaultHandler
    {
        /**
         * Table with styles
         */
        private final StylesTable stylesTable;

        /**
         * Table with unique strings
         */
        private final String[] sharedStringsTable;

        /**
         * Destination for data
         */
        private final BlockingQueue<List<Object>> output;
        private long output_rowcount = 0;

        private List<Object> currentRow;
        private int widestRow = 1;


        // Set when V start element is seen
        private boolean vIsOpen;

        // Set when cell start element is seen;
        // used when cell close element is seen.
        private xssfDataType nextDataType;

        // Used to format numeric cell values, if the user prefers us to import data 'as it appears' rather than the actual stored values
        // NOTE: not currently used, not sure when that functionality went away.
        private final boolean useFormats;

        private short formatIndex;
        private String formatString;
        private final DataFormatter formatter;
        private int thisColumn = -1;
        private final boolean isStartDate1904;

        // Gathers characters as they are seen.
        private final StringBuilder value;

        /**
         * Accepts objects needed while parsing.
         *  @param strings Table of shared strings
         * @param target  Sink for output
         * @param isStartDate1904 Indicates which date system is in use for this sheet
         */
            SheetHandler(
                StylesTable styles,
                String[] strings,
                BlockingQueue<List<Object>> target,
                boolean isStartDate1904)
        {
            this.stylesTable = styles;
            this.sharedStringsTable = strings;
            this.value = new StringBuilder();
            this.nextDataType = xssfDataType.NUMBER;
            this.output = target;
            this.formatter = new DataFormatter();
            this.useFormats = false;
            this.isStartDate1904 = isStartDate1904;
        }

        @SuppressWarnings("unused")
        private void debugPrint(String s)
        {
//            System.out.println(StringUtils.repeat(" ", debugIndent) + s);
        }

        /*
           * (non-Javadoc)
           * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
           */
        @Override
        public void startElement(String uri, String localName, String name,
                                 Attributes attributes)
        {
            debugPrint("<" + name + ">");
            if ("row".equals(name))
            {
                currentRow = new ArrayList<>(Math.max(1,widestRow));
            }
            if ("inlineStr".equals(name) || "v".equals(name) || "t".equals(name))
            {
                vIsOpen = true;
                // Clear contents cache
                value.setLength(0);
            }
            // c => cell
            else if ("c".equals(name))
            {
                // Get the cell reference
                String r = attributes.getValue("r");
                if (r != null)
                {
                    int firstDigit = -1;
                    for (int c = 0; c < r.length(); ++c)
                    {
                        if (Character.isDigit(r.charAt(c)))
                        {
                            firstDigit = c;
                            break;
                        }
                    }
                    thisColumn = nameToColumn(r.substring(0, firstDigit));
                }
                else
                    thisColumn = -1;

                // Set up defaults.
                this.nextDataType = xssfDataType.NUMBER;
                this.formatIndex = -1;
                this.formatString = null;
                String cellType = attributes.getValue("t");
                String cellStyleStr = attributes.getValue("s");
                if ("b".equals(cellType))
                    nextDataType = xssfDataType.BOOL;
                else if ("e".equals(cellType))
                    nextDataType = xssfDataType.ERROR;
                else if ("inlineStr".equals(cellType))
                    nextDataType = xssfDataType.INLINESTR;
                else if ("s".equals(cellType))
                    nextDataType = xssfDataType.SSTINDEX;
                else if ("str".equals(cellType))
                    nextDataType = xssfDataType.FORMULA;
                else if (cellStyleStr != null)
                {
                    // It's a number, but almost certainly one
                    //  with a special style or format
                    int styleIndex = Integer.parseInt(cellStyleStr);
                    XSSFCellStyle style = stylesTable.getStyleAt(styleIndex);
                    this.formatIndex = style.getDataFormat();
                    this.formatIndex = style.getDataFormat();
                    this.formatString = style.getDataFormatString();
                    if (this.formatString == null)
                        this.formatString = BuiltinFormats.getBuiltinFormat(this.formatIndex);
                }
            }
        }


        final Date getDateFromExcelDouble(double value)
        {
            return org.apache.poi.ss.usermodel.DateUtil.getJavaDate(value, isStartDate1904);
        }

        final String getIsoStringDateFromExcelDouble(double value)
        {
            // Format as date time with seconds, Issue #48930
            return DateUtil.formatIsoDateLongTime(getDateFromExcelDouble(value));
        }

        private boolean isTimeFormat(String dateFormatString)
        {
            return EXCEL_TIME_PATTERN.matcher(dateFormatString).matches();
        }

        /*
           * (non-Javadoc)
           * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
           */
        @Override
        public void endElement(String uri, String localName, String name)
        {
            Object thisValue;

            if ("c".equals(name))
            {
                // Process the value contents as required.
                // Do now, as characters() may be called more than once
                switch (nextDataType)
                {

                    case BOOL:
                        char first = value.charAt(0);
                        thisValue = first == '0' ? Boolean.FALSE : Boolean.TRUE;
                        break;

                    case ERROR:
                        thisValue = "ERROR:" + value;
                        break;

                    case FORMULA:
                        // A formula could result in a string value,
                        // so always add double-quote characters.
                        thisValue = value.toString();
                        break;

                    case INLINESTR:
                        //noinspection DuplicateBranchesInSwitch
                        thisValue = value.toString();
                        break;

                    case SSTINDEX:
                        String sstIndex = value.toString();
                        try
                        {
                            thisValue = sharedStringsTable[Integer.parseInt(sstIndex)];
                        }
                        catch (NumberFormatException ex)
                        {
                            thisValue = "Failed to parse SST index '" + sstIndex + "': " + ex;
                        }
                        break;

                    case NUMBER:
                        boolean isDateFormat = null!=this.formatString && org.apache.poi.ss.usermodel.DateUtil.isADateFormat(this.formatIndex, this.formatString);
                        boolean isNumberFormat = null != this.formatString && !isDateFormat && !this.formatString.equals("General") && !this.formatString.equals("@"); // i18n???
                        boolean isTimeFormat = false;
                        if (isDateFormat)
                            isTimeFormat = isTimeFormat(this.formatString);

                        if (StringUtils.isBlank(value))
                            thisValue = "";
                        else if (this.formatString != null && (useFormats || isTimeFormat))
                        {
                            thisValue = formatter.formatRawCellContents(Double.parseDouble(value.toString()), this.formatIndex, this.formatString, this.isStartDate1904);
                        }
                        else if (isDateFormat)
                        {
                            thisValue = getIsoStringDateFromExcelDouble(Double.parseDouble(value.toString()));
                        }
                        else
                        {
                            // Excel auto-converts lots of things that are not numbers, such particpantids and sometimes dates
                            // If the value is not explicitly formatted as a number then use Excel's stored string representation and let DataLoader sort it out
                            // NOTE: if we it is formatted as a number we generate our own string representation,
                            // This helps when targeting a string column
                            //     a) to avoid Excel's trailing 0000001 and 9999999 format
                            //     b) avoid scientific notation if possible
                            thisValue = _toString(value, isNumberFormat);
                        }
                        break;

                    default:
                        thisValue = "(TODO: Unexpected type: " + nextDataType + ")";
                        break;
                }

                // Issue 45764: if we couldn't get the column index from the attributes in startElement, use
                // the next value based on the current row ArrayList
                if (thisColumn == -1)
                    thisColumn = currentRow.size();

                while (currentRow.size() <= thisColumn)
                    currentRow.add(null);
                debugPrint("row:" + (output_rowcount+1) + " col:" + thisColumn + " " + thisValue);
                currentRow.set(thisColumn, thisValue);
                value.setLength(0);
            }
            else if ("row".equals(name))
            {
                // We're onto a new row
                widestRow = Math.max(widestRow, currentRow.size());
                try
                {
                    output.put(currentRow);
                }
                catch (InterruptedException e)
                {
                    throw new StopLoadingRows();
                }
                output_rowcount++;
                currentRow = null;
            }

            debugPrint("</" + name + ">");
        }

        /**
         * Captures characters only if a suitable element is open.
         * Originally was just "v"; extended for inlineStr also.
         */
        @Override
        public void characters(char[] ch, int start, int length)
        {
            debugPrint((vIsOpen?"+":" ") + "chars:" + new String(ch,start,length));
            if (vIsOpen)
                value.append(ch, start, length);
        }

        /**
         * Converts an Excel column name like "C" to a zero-based index.
         *
         * @return Index corresponding to the specified name
         */
        private int nameToColumn(String name)
        {
            int column = -1;
            for (int i = 0; i < name.length(); ++i)
            {
                int c = name.charAt(i);
                column = (column + 1) * 26 + c - 'A';
            }
            return column;
        }
    }
}
