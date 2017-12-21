/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import org.apache.poi.POIXMLException;
import org.apache.poi.UnsupportedFileFormatException;
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
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.jetbrains.annotations.NotNull;
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
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Data loader for Excel files -- can infer columns and return rows of data
 *
 * User: jgarms
 * Date: Oct 22, 2008
 */
public class ExcelLoader extends DataLoader
{
    public static FileType FILE_TYPE = new FileType(Arrays.asList(".xlsx", ".xls"), ".xlsx",
            Arrays.asList("application/" + ExcelFactory.SUB_TYPE_BIFF8, "application/" + ExcelFactory.SUB_TYPE_XSSF, "application/" + ExcelFactory.SUB_TYPE_BIFF5));

    static {
        FILE_TYPE.setExtensionsMutuallyExclusive(false);
    }

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


    private InputStream _is = null;
    private Workbook _workbook = null;

    private String sheetName;
    private Integer sheetIndex;

    private ExcelLoader()
    {}

    public ExcelLoader(File file) throws IOException
    {
        this(file, false);
    }

    public ExcelLoader(File file, boolean hasColumnHeaders) throws IOException
    {
        this(file, hasColumnHeaders, null);
    }

    public ExcelLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer)
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);
        _is = is;
        setScrollable(false);
    }


    public ExcelLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);
        setSource(file);
        setScrollable(true);
    }

    public ExcelLoader(Workbook workbook, boolean hasColumnHeaders, Container mvIndicatorContainer)
    {
        super(mvIndicatorContainer);
        setHasColumnHeaders(hasColumnHeaders);
        _file = null;
        _workbook = workbook;
        setScrollable(true);
    }


    private Workbook getWorkbook() throws IOException
    {
        if (null == _workbook)
        {
            try
            {
                if (null != _is)
                {
                    _workbook = ExcelFactory.create(_is);
                }
                else if (null != _file)
                {
                    _workbook = ExcelFactory.create(_file);
                }
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
        List<String> names = new ArrayList<>();

        Workbook workbook = getWorkbook();
        for (int i=0; i < workbook.getNumberOfSheets(); i++)
            names.add(workbook.getSheetName(i));
        return names;
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

    private Sheet getSheet() throws IOException
    {
        try
        {
            Workbook workbook = getWorkbook();
            if (sheetName != null)
                return workbook.getSheet(sheetName);
            else if (sheetIndex != null)
                return workbook.getSheetAt(sheetIndex);
            else
                return workbook.getSheetAt(0);
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            throw new IOException("Invalid Excel file");
        }
    }


    public String[][] getFirstNLines(int n) throws IOException
    {
        if (null != _file)
        {
            try
            {
                List<List<?>> grid = getParsedGridXLSX();
                List<String[]> cells = new ArrayList<>();

                for (int i=0 ; cells.size() < n && i<grid.size() ; i++)
                {
                    List<?> currentRow = grid.get(i);
                    List<String> rowData = new ArrayList<>(currentRow.size());
                    boolean foundData = false;

                    for (Object v : currentRow)
                    {
                        String data = (v != null && !(v instanceof String)) ? String.valueOf(v) : (String) v;
                        if (!StringUtils.isEmpty(data))
                            foundData = true;
                        rowData.add(data != null ? data : "");
                    }
                    if (foundData)
                        cells.add(rowData.toArray(new String[rowData.size()]));
                }

                return cells.toArray(new String[cells.size()][]);
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
                            data = DateUtil.formatDateTimeISO8601((Date)value);
                        else
                            data = String.valueOf(value);

                        if (data != null && !"".equals(data))
                            foundData = true;

                        rowData.add(data != null ? data : "");
                    }
                    else
                        rowData.add("");
                }
                if (foundData)
                    cells.add(rowData.toArray(new String[rowData.size()]));
            }
            if (--n == 0)
                break;
        }

        return cells.toArray(new String[cells.size()][]);
    }

    public CloseableIterator<Map<String, Object>> iterator()
    {
        try
        {
            if (null != _file)
            {
                try
                {
                    return new XlsxIterator();
                }
                catch (InvalidFormatException x)
                {
                    /* fall through */
                }
            }
            return new ExcelIterator();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

/*
    public void finalize() throws Throwable
    {
        workbook.close();
        super.finalize();
    }
*/

    public void close()
    {
    }


//    public List<Map<String, Object>> loadSAXY() throws IOException
//    {
//        try
//        {
//            LinkedList<ArrayList<Object>> output = loadSheetFromXLSX();
//
//            // arrays to maps
//            if (output.isEmpty())
//                return Collections.emptyList();
//            ArrayListMap.FindMap<String> findMap = new ArrayListMap.FindMap<String>(new CaseInsensitiveHashMap<Integer>());
//            List<Object> firstRow = output.removeFirst();
//            for (int index=0 ; index<firstRow.size() ; index++)
//                findMap.put(String.valueOf(firstRow.get(index)),index);
//            ArrayList<Map<String,Object>> maps = new ArrayList<Map<String, Object>>(output.size());
//            for (ArrayList<Object> row : output)
//                maps.add(new _ArrayListMap(findMap,row));
//            return maps;
//        }
//        catch (InvalidFormatException x)
//        {
//            // maybe .xls
//            return load();
//        }
//    }


    private List<List<?>> _parsedGridXLSX = null;

    private List<List<?>> getParsedGridXLSX() throws IOException, InvalidFormatException
    {
        if (null == _parsedGridXLSX)
            _parsedGridXLSX = loadSheetFromXLSX();
        return _parsedGridXLSX;
    }

    private List<List<?>> loadSheetFromXLSX() throws IOException, InvalidFormatException
    {
        OPCPackage xlsxPackage = null;
        try
        {
            List<List<Object>> collect = new LinkedList<>();
            xlsxPackage = OPCPackage.open(_file.getPath(), PackageAccess.READ);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(xlsxPackage);
            XSSFReader xssfReader = new XSSFReader(xlsxPackage);
            StylesTable styles = xssfReader.getStylesTable();
            XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            if (iter.hasNext())
            {
                InputStream stream = iter.next();
                InputSource sheetSource = new InputSource(stream);
                SAXParserFactory saxFactory = SAXParserFactory.newInstance();
                SAXParser saxParser = saxFactory.newSAXParser();
                XMLReader sheetParser = saxParser.getXMLReader();
                SheetHandler handler = new SheetHandler(styles, strings, 1, collect);
                sheetParser.setContentHandler(handler);
                sheetParser.parse(sheetSource);
            }
            List<List<?>> ret = new ArrayList<>(collect.size());
            ret.addAll(collect);
            return ret;
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
        finally
        {
            if (null != xlsxPackage)
                xlsxPackage.revert();
        }
    }


    private class XlsxIterator extends DataLoaderIterator
    {
        final List<List<?>> grid;

        XlsxIterator() throws IOException, InvalidFormatException
        {
            super(_skipLines == -1 ? 1 : _skipLines);
            grid = getParsedGridXLSX();
        }

        @Override
        protected Object[] readFields() throws IOException
        {
            if (lineNum() >= grid.size())
                return null;

            ColumnDescriptor[] allColumns = getColumns();
            List<?> row = grid.get(lineNum());
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
                            else if (column.clazz.equals(String.class))
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

        public void close() throws IOException
        {
            super.close();       // TODO: Shouldn't this close the workbook?
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
        }
        return toStringValue;
    }

    DecimalFormat df = new DecimalFormat("###0.#####################");


    public static class ExcelLoaderTestCase extends Assert
    {
        @Test
        public void detect() throws Exception
        {
            File excelSamplesRoot = JunitUtil.getSampleData(null, "dataLoading/excel");

            assertTrue(isExcel(new File(excelSamplesRoot, "ExcelLoaderTest.xls")));
            assertTrue(isExcel(new File(excelSamplesRoot, "SimpleExcelFile.xls")));
            assertTrue(isExcel(new File(excelSamplesRoot, "SimpleExcelFile.xlsx")));
            assertTrue(isExcel(new File(excelSamplesRoot, "fruits.xls")));

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
            assertFalse(isExcel(new File(excelSamplesRoot, "fruits.tsv")));
        }

        @Test
        public void testColumnTypes() throws Exception
        {
            File excelSamplesRoot = JunitUtil.getSampleData(null, "dataLoading/excel");

            if (null == excelSamplesRoot || !excelSamplesRoot.canRead())
                throw new IOException("Could not read excel samples in: " + excelSamplesRoot);

            File metadataSample = new File(excelSamplesRoot, "ExcelLoaderTest.xls");

            ExcelLoader loader = new ExcelLoader(metadataSample, true);
            checkColumnMetadata(loader);
            checkData(loader);
            loader.close();
        }

        private static void checkColumnMetadata(ExcelLoader loader) throws IOException
        {
            ColumnDescriptor[] columns = loader.getColumns();

            assertTrue(columns.length == 18);

            assertEquals(columns[0].clazz, Date.class);
            assertEquals(columns[1].clazz, Integer.class);
            assertEquals(columns[2].clazz, Double.class);

            assertEquals(columns[4].clazz, Boolean.class);

            assertEquals(columns[17].clazz, String.class);
        }

        private static void checkData(ExcelLoader loader)
        {
            List<Map<String, Object>> data = loader.load();

            assertTrue(data.size() == 7);

            for (Map map : data)
            {
                assertTrue(map.size() == 18);
            }

            Map firstRow = data.get(0);
            assertTrue(firstRow.get("scan").equals(96));
            assertTrue(firstRow.get("accurateMZ").equals(false));
            assertTrue(firstRow.get("description").equals("description"));
        }

        @Test
        public void testDoubleToString()
        {
            ExcelLoader xl = new ExcelLoader();

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
        private final ReadOnlySharedStringsTable sharedStringsTable;

        /**
         * Destination for data
         */
        private final Collection<List<Object>> output;

        private ArrayList<Object> currentRow;
        private int widestRow = 1;

        /**
         * Number of columns to read starting with leftmost
         */
        private final int minColumnCount;

        // Set when V start element is seen
        private boolean vIsOpen;

        // Set when cell start element is seen;
        // used when cell close element is seen.
        private xssfDataType nextDataType;

            // Used to format numeric cell values.
        private boolean useFormats = false;
        private short formatIndex;
        private String formatString;
        private final DataFormatter formatter;
        private int thisColumn = -1;

        // Gathers characters as they are seen.
        private StringBuilder value;

        private int debugIndent = 0;

        /**
         * Accepts objects needed while parsing.
         *
         * @param strings Table of shared strings
         * @param cols    Minimum number of columns to show
         * @param target  Sink for output
         */
        SheetHandler(
                StylesTable styles,
                ReadOnlySharedStringsTable strings,
                int cols,
                Collection<List<Object>> target)
        {
            this.stylesTable = styles;
            this.sharedStringsTable = strings;
            this.minColumnCount = cols;
            this.value = new StringBuilder();
            this.nextDataType = xssfDataType.NUMBER;
            this.output = target;
            this.formatter = new DataFormatter();
            this.useFormats = false;
        }

        private void debugPrint(String s)
        {
//            System.out.println(StringUtils.repeat(" ", debugIndent) + s);
        }

        /*
           * (non-Javadoc)
           * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
           */
        public void startElement(String uri, String localName, String name,
                                 Attributes attributes) throws SAXException
        {
            debugIndent++;
            debugPrint("<" + name + ">");
            if ("row".equals(name))
            {
                currentRow = new ArrayList<>(Math.max(1,widestRow));
                output.add(currentRow);
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
                    this.formatString = style.getDataFormatString();
                    if (this.formatString == null)
                        this.formatString = BuiltinFormats.getBuiltinFormat(this.formatIndex);
                }
            }
        }

        /*
           * (non-Javadoc)
           * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
           */
        public void endElement(String uri, String localName, String name)
                throws SAXException
        {
            Object thisValue = null;

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
                        thisValue = "ERROR:" + value.toString();
                        break;

                    case FORMULA:
                        // A formula could result in a string value,
                        // so always add double-quote characters.
                        thisValue = value.toString();
                        break;

                    case INLINESTR:
//                        XSSFRichTextString rtsi = new XSSFRichTextString(value.toString());
//                        thisValue = rtsi.toString();
                        thisValue = value.toString();
                        break;

                    case SSTINDEX:
                        String sstIndex = value.toString();
                        try
                        {
                            int idx = Integer.parseInt(sstIndex);
                            String raw = sharedStringsTable.getEntryAt(idx);
//                          XSSFRichTextString is really expensive, put this back if we need it (examples anyone?)
//                            XSSFRichTextString rtss = new XSSFRichTextString(sharedStringsTable.getEntryAt(idx));
//                            thisValue = rtss.toString();
                            thisValue = raw;
                        }
                        catch (NumberFormatException ex)
                        {
                            thisValue = "Failed to parse SST index '" + sstIndex + "': " + ex.toString();
                        }
                        break;

                    case NUMBER:
                        boolean isDateFormat = null!=this.formatString && org.apache.poi.ss.usermodel.DateUtil.isADateFormat(this.formatIndex, this.formatString);
                        boolean isNumberFormat = null != this.formatString && !isDateFormat && !this.formatString.equals("General") && !this.formatString.equals("@"); // i18n???

                        if (StringUtils.isBlank(value))
                            thisValue = "";
                        else if (this.formatString != null && (useFormats || isDateFormat))
                        {
                            thisValue = formatter.formatRawCellContents(Double.parseDouble(value.toString()), this.formatIndex, this.formatString);
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

                while (currentRow.size() <= thisColumn)
                    currentRow.add(null);
                debugPrint("row:" + output.size() + " col:" + thisColumn + " " + thisValue);
                currentRow.set(thisColumn, thisValue);
                value.setLength(0);
            }
            else if ("row".equals(name))
            {
                // We're onto a new row
                widestRow = Math.max(widestRow,currentRow.size());
                currentRow = null;
            }

            debugPrint("</" + name + ">");
            debugIndent--;
        }

        /**
         * Captures characters only if a suitable element is open.
         * Originally was just "v"; extended for inlineStr also.
         */
        public void characters(char[] ch, int start, int length)
                throws SAXException
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
