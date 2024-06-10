package org.labkey.api.assay.plate;

import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.collections.RowMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ExcelCellUtils;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlateMapExcelWriter extends ExcelWriter
{
    private static final Logger logger = LogHelper.getLogger(PlateMapExcelWriter.class, "Plate map export");

    private final Plate _plate;

    private final QueryView _queryView;

    private final List<DisplayColumn> _displayColumns;

    private final Map<String, CellStyle> _cellStyleMap = new HashMap<>();

    // Map of Row label (A, B, etc.) to Column Data, which is a Map of Column Label (1, 2, etc.) to Well Data (Sample
    // ID, metadata column values)
    private final Map<Integer, Map<Integer, RowMap<Object>>> _wellData = new HashMap<>();

    public PlateMapExcelWriter(Plate plate, List<DisplayColumn> displayColumns, QueryView queryView) throws SQLException, IOException
    {
        super(ExcelDocumentType.xlsx);
        setAutoSize(true);
        _plate = plate;
        _displayColumns = displayColumns;
        _queryView = queryView;
        initializeWellData();
    }

    private void initializeWellData() throws SQLException, IOException
    {
        // Iterate through the data in the _queryView
        // for every row insert into _wellData:
        //  Get the rowHashMap from _wellData via the row.Row value, if null insert a new HashMap into wellData
        //  Insert the row object into the rowHashMap with row.Col as  the key
        try (Results results = _queryView.getResults())
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(results);
            while (results.next())
            {
                RowMap<Object> well = factory.getRowMap(results);
                Integer row = (Integer) well.get("Row");
                Integer col = (Integer) well.get("Col");

                Map<Integer, RowMap<Object>> rowMap = _wellData.computeIfAbsent(row, k -> new HashMap<>());

                rowMap.put(col, well);
            }
        }
    }

    private Row getOrCreateRow(Sheet sheet)
    {
        Row row = sheet.getRow(getCurrentRow());
        if (row == null)
        {
            row = sheet.createRow(getCurrentRow());
        }

        return row;
    }

    protected void renderSheetHeader(Sheet sheet) throws MaxRowsExceededException
    {
        Row row = getOrCreateRow(sheet);

        // First column is blank, the rest should use PositionImpl.ALPHABET for column header values
        for (int idx = 1; idx <= _plate.getColumns(); idx++)
        {
            Cell cell = row.getCell(idx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            cell.setCellValue(idx);
        }

        incrementRow();
    }

    protected CellStyle getCellStyle(Workbook workbook, DisplayColumn dc) {
        CellStyle cellStyle = _cellStyleMap.get(dc.getName());

        if (cellStyle == null)
        {
            String formatString = dc.getExcelFormatString();

            if (formatString == null)
                formatString = dc.getFormatString();

            cellStyle = ExcelCellUtils.createCellStyle(workbook, ExcelCellUtils.getSimpleType(dc), formatString);
            _cellStyleMap.put(dc.getName(), cellStyle);
        }

        return cellStyle;
    }

    private String getDisplayColumnAlias(DisplayColumn displayColumn)
    {
        ColumnInfo ci = displayColumn.getColumnInfo();

        if (ci.isLookup() && ci.getDisplayField() != null)
            ci = ci.getDisplayField();

        return ci.getAlias();
    }

    protected void renderGridRow(Sheet sheet, List<DisplayColumn> displayColumns)
    {
        Row excelRow = getOrCreateRow(sheet);

        for (int colIdx = 0; colIdx <= _plate.getColumns(); colIdx++)
        {
            Cell cell = excelRow.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            if (colIdx == 0)
                cell.setCellValue(PositionImpl.ALPHABET[getCurrentRow() - 1]);
            else
            {
                int rowIdx = getCurrentRow() - 1; // account for header
                Map<Integer, RowMap<Object>> row = _wellData.get(rowIdx);

                if (row == null)
                {
                    logger.error("Well data not found for row " + rowIdx);
                    continue;
                }

                RowMap<Object> well = row.get(colIdx - 1);

                if (well == null)
                {
                    logger.error("Well data not found for row: " + rowIdx + ", col: " + (colIdx - 1));
                    continue;
                }

                if (displayColumns.size() == 1)
                {
                    DisplayColumn displayColumn = displayColumns.get(0);
                    String alias = getDisplayColumnAlias(displayColumn);
                    Object value = well.get(alias);

                    if (value != null)
                        ExcelCellUtils.writeCell(sheet.getWorkbook(), cell, displayColumn, value);
                }
                else
                {
                    // Note: Doing a simple string concat of all values and joining them by "\n" is sure to cause some
                    // values to render differently than when we render them individually in the non-summary sheets.
                    // There doesn't seem to be an obviously better solution, so we should find a way to communicate
                    // this to users.
                    List<String> values = displayColumns.stream()
                            .map(dc -> {
                                var value = well.get(getDisplayColumnAlias(dc));

                                if (value == null)
                                    value = "";

                                if (dc.getTsvFormat() != null)
                                    return dc.getTsvFormat().format(value);

                                return value;
                            })
                            .map(Object::toString)
                            .toList();
                    cell.setCellValue(String.join("\n", values));
                    CellStyle style = sheet.getWorkbook().createCellStyle();
                    style.setWrapText(true);
                    cell.setCellStyle(style);
                }
            }
        }
    }

    protected void renderGrid(Sheet sheet, List<DisplayColumn> displayColumns) throws MaxRowsExceededException
    {
        for (int idx = 0; idx < _plate.getRows(); idx++)
        {
            renderGridRow(sheet, displayColumns);
            incrementRow();
        }
    }

    @Override
    protected void renderSheet(Workbook workbook, int sheetNumber)
    {
        RenderContext ctx = new RenderContext(HttpView.currentContext());
        Sheet sheet = ensureSheet(ctx, workbook, sheetNumber, false);
        ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();

        try
        {
            renderSheetHeader(sheet);
            List<DisplayColumn> displayColumns;

            if (sheetNumber == 0) // Summary view, render all values in each cell
                displayColumns = _displayColumns.stream().filter(dc -> !dc.getName().equals("row") && !dc.getName().equals("col")).toList();
            else if (sheetNumber == 1) // Sample ID view
                displayColumns = List.of(_displayColumns.get(0));
            else // CustomField view
            {
                PlateCustomField customField = _plate.getCustomFields().get(sheetNumber - 2);
                FieldKey fieldKey = FieldKey.fromParts("Properties", customField.getName());
                displayColumns = _displayColumns.stream().filter(dc -> dc.getColumnInfo().getFieldKey().equals(fieldKey)).toList();
            }

            renderGrid(sheet, displayColumns);
        }
        catch (Exception e)
        {
            logger.error("Error rendering sheet " + sheetNumber + ": " + e.getMessage());
        }
    }

    @Override
    protected void renderSheets(Workbook workbook)
    {
        List<PlateCustomField> customFields = _plate.getCustomFields();
        // Summary sheet, SampleId sheet, and one sheet per custom field
        int sheetCount = 2 + customFields.size();

        for (int sheetNum = 0; sheetNum < sheetCount; sheetNum++)
        {
            if (sheetNum == 0)
                setSheetName("Summary");
            else if (sheetNum == 1)
                setSheetName("Sample ID");
            else
                setSheetName(customFields.get(sheetNum - 2).getName());

            renderNewSheet(workbook);
        }
    }

    public class PlateMapDisplayColumn extends DisplayColumn
    {
        Class valueClass;

        public PlateMapDisplayColumn(String name, Class valueClass)
        {
            this(name, name, valueClass);
        }

        public PlateMapDisplayColumn(String name, String caption, Class valueClass)
        {
            setName(name);
            setCaption(caption);
            this.valueClass = valueClass;
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            //Ignore the context.
//            return maps.get(currentRow).get(getName());
            return null;
        }

        @Override
        public Class getValueClass()
        {
            return valueClass;
        }


        //NOTE: Methods beyond here are unimplemented, just abstract in base class!
        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void renderDetailsCellContents(RenderContext ctx, Writer out)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void renderTitle(RenderContext ctx, Writer out)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public boolean isSortable()
        {
            return false;
        }

        @Override
        public boolean isFilterable()
        {
            return false;
        }

        @Override
        public boolean isEditable()
        {
            return false;
        }

        @Override
        public void renderFilterOnClick(RenderContext ctx, Writer out)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void renderInputHtml(RenderContext ctx, Writer out, Object value)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void setURL(ActionURL url)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public void setURL(String url)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }

        @Override
        public String getURL()
        {
            return null;
        }

        @Override
        public String renderURL(RenderContext ctx)
        {
            return null;
        }

        @Override
        public boolean isQueryColumn()
        {
            return false;
        }

        @Override
        public ColumnInfo getColumnInfo()
        {
            return null;
        }

        @Override
        public void render(RenderContext ctx, Writer out)
        {
            throw new UnsupportedOperationException("This is for excel only.");
        }
    }
}
