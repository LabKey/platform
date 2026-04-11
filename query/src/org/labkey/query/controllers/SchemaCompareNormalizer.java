/*
 * Copyright (c) 2025 LabKey Corporation
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
package org.labkey.query.controllers;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.ExprColumn;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cross-database value normalization for schema comparison checksums.
 * Eliminates known representation differences between SQL Server and PostgreSQL.
 */
public class SchemaCompareNormalizer
{
    private static final Pattern GUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2})(\\.\\d+)?(.*)$");

    /**
     * Normalize a single cell value for deterministic cross-database comparison.
     *
     * Rules:
     * 1. NULL and empty string → null
     * 2. GUIDs → lowercase
     * 3. Trailing whitespace stripped
     * 4. Timestamps truncated to milliseconds (3 decimal places)
     * 5. Floats rounded to 10 significant digits
     * 6. Booleans normalized (0/1 → false/true when type is BOOLEAN)
     */
    public static Object normalizeValue(@Nullable Object value, @Nullable JdbcType jdbcType)
    {
        if (value == null)
            return null;

        // Handle booleans first
        if (value instanceof Boolean)
            return value;

        // Boolean normalization for numeric values
        if (jdbcType == JdbcType.BOOLEAN && value instanceof Number num)
            return num.intValue() != 0;

        // Numeric precision
        if (value instanceof Double d)
            return d == 0.0 ? 0.0 : Double.parseDouble(String.format("%.10g", d));

        if (value instanceof Float f)
            return f == 0.0f ? 0.0 : Double.parseDouble(String.format("%.10g", f.doubleValue()));

        if (value instanceof BigDecimal bd)
        {
            if (jdbcType != null && jdbcType.isInteger())
            {
                return switch (jdbcType)
                {
                    case BIGINT -> bd.longValue();
                    case SMALLINT, TINYINT -> (short) bd.intValue();
                    default -> bd.intValue();
                };
            }
            bd = bd.stripTrailingZeros();
            return Double.parseDouble(String.format("%.10g", bd.doubleValue()));
        }

        if (value instanceof Number)
            return value;

        // Timestamp normalization — truncate to milliseconds
        if (value instanceof Timestamp ts)
            return truncateTimestampToMillis(ts);

        if (value instanceof Date d)
            return truncateTimestampToMillis(new Timestamp(d.getTime()));

        if (value instanceof String s)
        {
            // Strip trailing whitespace
            s = s.stripTrailing();

            // Empty → null
            if (s.isEmpty())
                return null;

            // GUID case normalization
            if (GUID_PATTERN.matcher(s).matches())
                return s.toLowerCase();

            // Timestamp precision — truncate to 3 decimal places
            Matcher m = TIMESTAMP_PATTERN.matcher(s);
            if (m.matches() && m.group(2) != null)
            {
                String frac = m.group(2); // includes the dot
                if (frac.length() > 4) // more than .XXX
                {
                    String truncatedFrac = frac.substring(0, 4); // .XXX
                    s = m.group(1) + truncatedFrac + (m.group(3) != null ? m.group(3) : "");
                }
            }

            return s;
        }

        // byte arrays → hex string for deterministic representation
        if (value instanceof byte[] bytes)
        {
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes)
                hex.append(String.format("%02x", b));
            return hex.toString();
        }

        // Fallback
        return value.toString();
    }

    private static String truncateTimestampToMillis(Timestamp ts)
    {
        // Truncate nanos to milliseconds
        long millis = ts.getTime();
        Timestamp truncated = new Timestamp(millis - (millis % 1000) + (ts.getNanos() / 1_000_000));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String base = sdf.format(truncated);
        int ms = truncated.getNanos() / 1_000_000;
        if (ms > 0)
            return base + "." + String.format("%03d", ms);
        return base;
    }

    static String normalizeWhitespace(@Nullable String value)
    {
        if (value == null)
            return null;
        return value.replace("\r\n", " ").replace("\r", " ").replace("\n", " ").replace("\t", " ");
    }

    /**
     * Normalize an entire row for hashing.
     *
     * @param rowData map of column_name → value
     * @param columnTypes optional map of column_name → JdbcType
     * @return sorted list of (column_name, normalized_value) entries
     */
    public static List<Map.Entry<String, Object>> normalizeRow(
        Map<String, Object> rowData,
        @Nullable Map<String, JdbcType> columnTypes)
    {
        TreeMap<String, Object> normalized = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Map.Entry<String, Object> entry : rowData.entrySet())
        {
            String key = entry.getKey();

            // Skip LabKey metadata fields
            if (key.startsWith("_") && !key.equals("_row"))
                continue;

            Object val = entry.getValue();

            // Handle extended API format: {value: ..., displayValue: ...}
            if (val instanceof Map<?, ?> map && map.containsKey("value"))
                val = map.get("value");

            JdbcType type = columnTypes != null ? columnTypes.get(key) : null;
            normalized.put(key.toLowerCase(), normalizeValue(val, type));
        }

        return normalized.entrySet().stream()
            .map(e -> (Map.Entry<String, Object>) new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * Build a Sort from the table's PK columns for deterministic row ordering.
     * Falls back to all selectable columns if no PK is available.
     */
    public static Sort getSortForChecksum(TableInfo tableInfo)
    {
        return getSortForChecksumWithColumns(tableInfo).sort();
    }

    /**
     * Result of building a cross-database-safe sort. Contains the Sort itself plus any
     * extra ExprColumn instances that must be included in the SELECT for the ORDER BY
     * to resolve (e.g., CAST wrappers for GUID columns).
     */
    public record SortResult(Sort sort, List<ColumnInfo> extraSortColumns) {}

    /**
     * Build a Sort from the table's PK columns, with GUID columns cast to VARCHAR(36)
     * so that SQL Server and PostgreSQL produce identical row ordering.
     */
    public static SortResult getSortForChecksumWithColumns(TableInfo tableInfo)
    {
        List<ColumnInfo> sortCols;
        List<ColumnInfo> pkCols = tableInfo.getPkColumns();

        if (!pkCols.isEmpty())
        {
            sortCols = pkCols;
        }
        else
        {
            sortCols = tableInfo.getColumns().stream()
                .filter(col -> !col.isUnselectable())
                .collect(Collectors.toList());
        }

        List<String> sortNames = new ArrayList<>();
        List<ColumnInfo> extraColumns = new ArrayList<>();

        for (ColumnInfo col : sortCols)
        {
            if (col.getJdbcType() == JdbcType.GUID)
            {
                String exprName = "_sort_guid_" + col.getName().toLowerCase();
                SQLFragment castSql = new SQLFragment("CAST(");
                castSql.append(col.getValueSql(ExprColumn.STR_TABLE_ALIAS));
                castSql.append(" AS VARCHAR(36))");
                ExprColumn exprCol = new ExprColumn(tableInfo, exprName, castSql, JdbcType.VARCHAR, col);
                extraColumns.add(exprCol);
                sortNames.add(exprName);
            }
            else
            {
                sortNames.add(col.getName());
            }
        }

        Sort sort = new Sort(String.join(",", sortNames));
        return new SortResult(sort, Collections.unmodifiableList(extraColumns));
    }

    @SuppressWarnings("unused")
    public static class TestCase extends Assert
    {
        @Test
        public void testNullAndEmpty()
        {
            assertNull(normalizeValue(null, null));
            assertNull(normalizeValue("", null));
            assertNull(normalizeValue("   ", null));
        }

        @Test
        public void testGuidNormalization()
        {
            assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                normalizeValue("A1B2C3D4-E5F6-7890-ABCD-EF1234567890", null));
            assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                normalizeValue("a1b2c3d4-e5f6-7890-abcd-ef1234567890", null));
        }

        @Test
        public void testTrailingWhitespace()
        {
            assertEquals("hello", normalizeValue("hello   ", null));
            assertEquals("hello", normalizeValue("hello\t", null));
        }

        @Test
        public void testWhitespaceNormalization()
        {
            assertEquals("line1 line2 line3 line4", normalizeWhitespace("line1\r\nline2\rline3\nline4"));
            assertEquals("a b", normalizeWhitespace("a\tb"));
        }

        @Test
        public void testTimestampTruncation()
        {
            assertEquals("2024-01-15 10:30:45.123",
                normalizeValue("2024-01-15 10:30:45.123456789", null));
            assertEquals("2024-01-15 10:30:45.100",
                normalizeValue("2024-01-15 10:30:45.1", null));
            // Already at ms precision — no change
            assertEquals("2024-01-15 10:30:45.123",
                normalizeValue("2024-01-15 10:30:45.123", null));
        }

        @Test
        public void testFloatPrecision()
        {
            assertEquals(1.234567890, (double) normalizeValue(1.23456789012345, null), 1e-12);
            assertEquals(0.0, (double) normalizeValue(0.0, null), 0);
        }

        @Test
        public void testBooleanNormalization()
        {
            assertEquals(true, normalizeValue(true, null));
            assertEquals(false, normalizeValue(false, null));
            assertEquals(true, normalizeValue(1, JdbcType.BOOLEAN));
            assertEquals(false, normalizeValue(0, JdbcType.BOOLEAN));
        }

        @Test
        public void testTimestampObject()
        {
            Timestamp ts = Timestamp.valueOf("2024-01-15 10:30:45.123456789");
            String result = (String) normalizeValue(ts, null);
            assertEquals("2024-01-15 10:30:45.123", result);
        }

        @Test
        public void testRegularStringsUnchanged()
        {
            assertEquals("hello world", normalizeValue("hello world", null));
            assertEquals("123", normalizeValue("123", null));
        }

        @Test
        public void testBinaryNormalization()
        {
            byte[] data = new byte[]{0x01, 0x02, (byte) 0xFF};
            assertEquals("0102ff", normalizeValue(data, null));

            byte[] empty = new byte[0];
            assertEquals("", normalizeValue(empty, null));
        }

        @Test
        public void testBigDecimalWithIntegerJdbcType()
        {
            // Integer types should produce integer results, not doubles
            Object intResult = normalizeValue(new BigDecimal("5"), JdbcType.INTEGER);
            assertEquals(5, intResult);
            assertSame(Integer.class, intResult.getClass());

            Object longResult = normalizeValue(new BigDecimal("5"), JdbcType.BIGINT);
            assertEquals(5L, longResult);
            assertSame(Long.class, longResult.getClass());

            Object shortResult = normalizeValue(new BigDecimal("5"), JdbcType.SMALLINT);
            assertEquals((short) 5, shortResult);
            assertSame(Short.class, shortResult.getClass());

            Object tinyResult = normalizeValue(new BigDecimal("5"), JdbcType.TINYINT);
            assertEquals((short) 5, tinyResult);
            assertSame(Short.class, tinyResult.getClass());

            // Non-integer JdbcType should still produce doubles
            Object decimalResult = normalizeValue(new BigDecimal("1.5"), JdbcType.DECIMAL);
            assertSame(Double.class, decimalResult.getClass());

            // Null JdbcType should still produce doubles
            Object nullTypeResult = normalizeValue(new BigDecimal("1.5"), null);
            assertSame(Double.class, nullTypeResult.getClass());
        }

        @Test
        public void testBigDecimalTrailingZeros()
        {
            // 1.50000 and 1.5 should produce the same normalized value
            assertEquals(normalizeValue(new BigDecimal("1.50000"), null),
                normalizeValue(new BigDecimal("1.5"), null));
        }
    }
}
