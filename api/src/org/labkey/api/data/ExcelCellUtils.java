package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Workbook;
import org.labkey.api.util.DateUtil;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Time;
import java.text.Format;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * This is a utility class that contains the necessary methods for writing properly formatted values to Excel cells.
 * This logic was previously housed directly in ExcelColumn, however with the introduction of PlateMapExcelWriter we
 * needed a shared location for rendering formatted values to Excel cells.
 */
public class ExcelCellUtils
{
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_INT = 1;
    public static final int TYPE_DOUBLE = 2;
    public static final int TYPE_STRING = 3;
    public static final int TYPE_MULTILINE_STRING = 4;
    public static final int TYPE_DATE = 5;
    public static final int TYPE_BOOLEAN = 6;
    public static final int TYPE_FILE = 7;
    public static final int TYPE_TIME = 8;

    private static final Date EXCEL_DATE_0 = (new GregorianCalendar(1900, Calendar.JANUARY, 1)).getTime();

    public static int getSimpleType(DisplayColumn dc)
    {
        Class valueClass = dc.getDisplayValueClass();

        if (Integer.class.isAssignableFrom(valueClass) || Integer.TYPE.isAssignableFrom(valueClass) ||
                Long.class.isAssignableFrom(valueClass) || Long.TYPE.isAssignableFrom(valueClass) ||
                Short.class.isAssignableFrom(valueClass) || Short.TYPE.isAssignableFrom(valueClass))
            return TYPE_INT;
        else if (Float.class.isAssignableFrom(valueClass) || Float.TYPE.isAssignableFrom(valueClass) ||
                Double.class.isAssignableFrom(valueClass) || Double.TYPE.isAssignableFrom(valueClass) ||
                BigDecimal.class.isAssignableFrom(valueClass))
            return TYPE_DOUBLE;
        else if (String.class.isAssignableFrom(valueClass))
        {
            if (dc.getColumnInfo() != null && dc.getColumnInfo().getInputRows() > 1)
            {
                return TYPE_MULTILINE_STRING;
            }
            return TYPE_STRING;
        }
        else if (Date.class.isAssignableFrom(valueClass))
        {
            if (Time.class.isAssignableFrom(valueClass))
                return TYPE_TIME;
            else
                return TYPE_DATE;
        }
        else if (Boolean.class.isAssignableFrom(valueClass) || Boolean.TYPE.isAssignableFrom(valueClass))
            return TYPE_BOOLEAN;
        else if (File.class.isAssignableFrom(valueClass))
            return TYPE_FILE;
        else
        {
            return TYPE_UNKNOWN;
        }
    }

    public static String getFormatString(int simpleType, @Nullable String formatString)
    {
        if (formatString != null)
        {
            if (simpleType == TYPE_DATE || simpleType == TYPE_TIME)
            {
                formatString = formatString.replaceAll("aa", "a").replaceAll("a", "AM/PM");
            }
            else if (simpleType == TYPE_INT || simpleType == TYPE_DOUBLE)
            {
                // Excel has a different idea of how to represent scientific notation, so be sure that we
                // transform the Java format if needed.
                // https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=17735
                formatString = formatString.replaceAll("[eE][^\\+]", "E+0");
            }

            return formatString;
        }

        return switch (simpleType)
        {
            case (TYPE_DATE) -> DateUtil.getStandardDateFormatString();
            case (TYPE_TIME) -> DateUtil.getStandardTimeFormatString();
            case (TYPE_INT) -> "0";
            case (TYPE_DOUBLE) -> "0.0000";
            default -> null;
        };
    }

    public static CellStyle createCellStyle(Workbook workbook, int simpleType, @Nullable String formatString)
    {
        if (formatString == null)
            formatString = getFormatString(simpleType, formatString);

        CellStyle style = workbook.createCellStyle();

        return switch (simpleType)
        {
            case (TYPE_INT), (TYPE_DOUBLE), (TYPE_DATE), (TYPE_TIME) ->
            {
                short formatIndex = workbook.createDataFormat().getFormat(formatString);
                style.setDataFormat(formatIndex);
                yield style;
            }
            case (TYPE_MULTILINE_STRING) ->
            {
                style.setWrapText(true);
                yield style;
            }
            default -> null;
        };
    }

    public static void writeCell(Cell cell, CellStyle style, int simpleType, String formatString, ColumnInfo columnInfo, Object value)
    {
        switch (simpleType)
        {
            case (TYPE_DATE):
                // Careful here... need to make sure we adjust dates for GMT.  This constructor automatically does the conversion, but there seem to be
                // bugs in other jxl 2.5.7 constructors: DateTime(c, r, d) forces the date to time-only, DateTime(c, r, d, gmt) doesn't adjust for gmt
                if (value instanceof Date dateVal)
                {
                    if (dateVal.compareTo(EXCEL_DATE_0) < 0)
                    {
                        if (StringUtils.isEmpty(formatString))
                            cell.setCellValue(value.toString());
                        else
                        {
                            // dates before 1900 are invalid for excel, export as formatted string instead
                            formatString = formatString.replaceAll("AM/PM", "a");
                            Format formatter = FastDateFormat.getInstance(formatString);
                            cell.setCellValue(formatter.format(dateVal));
                        }
                    }
                    else
                        cell.setCellValue((Date) value);
                    cell.setCellStyle(style);
                }
                else
                {
                    cell.setCellValue(value.toString());
                }
                break;
            case (TYPE_TIME):
                if (value instanceof Time t)
                {
                    cell.setCellValue(t);
                    cell.setCellStyle(style);
                }
                else
                    cell.setCellValue(value.toString());
                break;
            case (TYPE_INT):
            case (TYPE_DOUBLE):
                if (value instanceof Number n)
                {
                    cell.setCellValue(n.doubleValue());
                    cell.setCellStyle(style);
                }
                //Issue 47268: Export Does Not Include Failed Lookup Values
                //Set Integer broken lookup values as String.
                //Issue 50133 - related (not exactly): We also want to export non-empty values when this corresponds to an ancestor field with more than one value (e.g., "2 values")
                else
                {
                    cell.setCellValue(value.toString());
                }
                break;
            case(TYPE_STRING):
            default:
                // 9729 : CRs are doubled in list data exported to Excel, normalize newlines as '\n'
                String s = value.toString().replaceAll("\r\n", "\n");

                // Check if the string is too long
                if (s.length() > 32767)
                {
                    s = s.substring(0, 32762) + "...";
                }

                cell.setCellValue(s);
                if (style != null)
                    cell.setCellStyle(style);
                break;
        }
    }

    public static void writeCell(Workbook workbook, Cell cell, DisplayColumn displayColumn, Object value)
    {
        int simpleType = getSimpleType(displayColumn);
        String formatString = displayColumn.getExcelFormatString();

        if (formatString == null)
            formatString = displayColumn.getFormatString();

        formatString = getFormatString(simpleType, formatString);
        CellStyle style = createCellStyle(workbook, simpleType, formatString);
        writeCell(cell, style, simpleType, formatString, displayColumn.getColumnInfo(), value);
    }
}
