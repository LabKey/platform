<%
/*
 * Copyright (c) 2026 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page import="org.junit.Test" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="org.labkey.api.data.BaseColumnInfo" %>
<%@ page import="org.jetbrains.annotations.NotNull" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="org.labkey.api.exp.PropertyType" %>
<%@ page import="org.labkey.api.ontology.Quantity" %>
<%@ page import="org.labkey.api.ontology.Unit" %>
<%@ page import="org.labkey.api.ontology.KindOfQuantity" %>
<%@ page import="org.labkey.api.data.MutableColumnInfo" %>
<%@ page import="org.labkey.api.data.WrappedColumnInfo" %>
<%@ page import="org.apache.commons.beanutils.ConversionException" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.dialect.SqlDialect" %>
<%@ page import="org.labkey.api.data.CoreSchema" %>
<%@ page import="java.nio.ByteBuffer" %>
<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>
<%--
This tests uses MockRequest to test some expected Headers and Meta tags for various types of requests.
--%>
<%!
    void testConvert(ColumnInfo col, Object expected, Object val)
    {
        var result = col.convert(val);
        assertNotNull(result);
        //assertEquals(col.getJdbcType().getJavaClass(), result.getClass());
        assertEquals(expected.getClass(), result.getClass());
        assertEquals(expected, result);
    }

    void testConvertsToNull(ColumnInfo col, Object val)
    {
        var result = col.convert(val);
        assertNull(result);
    }

    void testConversionException(ColumnInfo col, Object val)
    {
        try
        {
            col.convert(val);
            fail();
        }
        catch (ConversionException x)
        {
            return;
        }
    }

    void testConvert(JdbcType type, Object expected, Object val)
    {
        var col = new BaseColumnInfo("~", null, type);
        testConvert(col.lock(), expected, val);
    }

    void testConvertsToNull(JdbcType type, Object val)
    {
        var col = new BaseColumnInfo("~", null, type);
        testConvertsToNull(col.lock(), val);
    }

    void testConversionException(JdbcType type, Object val)
    {
        var col = new BaseColumnInfo("~", null, type);
        testConversionException(col.lock(), val);
    }

    void testConvert(PropertyType pt, Object expected, @NotNull Object val)
    {
        var col = new BaseColumnInfo("~", null, pt.getJdbcType());
        col.setPropertyType(pt);
        testConvert(col.lock(), expected, val);
    }

    void testConvertsToNull(PropertyType pt, Object val)
    {
        var col = new BaseColumnInfo("~", null, pt.getJdbcType());
        col.setPropertyType(pt);
        testConvertsToNull(col.lock(), val);
    }

    void testConversionException(PropertyType pt, Object val)
    {
        var col = new BaseColumnInfo("~", null, pt.getJdbcType());
        col.setPropertyType(pt);
        testConversionException(col.lock(), val);
    }

    void testQuantity(Unit displayUnit, Quantity expected, Object value)
    {
        // UNDONE: setDisplayUnit is NYI???
        var col = new BaseColumnInfo("~", null, JdbcType.DOUBLE)
        {
            @Override
            public Unit getDisplayUnit()
            {
                return displayUnit;
            }

            @Override
            public KindOfQuantity getKindOfQuantity()
            {
                return null==displayUnit ? null : displayUnit.getKindOfQuantity();
            }
        };
        testConvert(col.lock(), expected, value);
    }


    /** This test is for the integrated ColumnInfo.convert() logic.
     * <p></p>
     * A lot of this testing is redundant with lower-level unit testing,
     * however, this still serves as a basic conversion smoke test.
     * <p></p>
     * In particular, the PropertyType conversions are pretty
     * redundant with ConvertHelper.convert() and JdbcType.convert()
     * (PropertyType predates JdbcType), but there are some differences
     * in implementation.  We should try to reconcile these differences.
     */
    @Test
    public void testColumnConvert() throws Exception
    {
        // see also ConvertHelper.testEmpty()

        // w/o propertyType
        for (JdbcType type : JdbcType.values())
        {
            switch (type)
            {
                case BIGINT ->
                {
                    testConvert(type, Long.valueOf(5), Integer.valueOf(5));
                    testConvert(type, Long.valueOf(5), "5");
                    testConvert(type, Long.valueOf(5), Double.valueOf(5.00000));
                    testConversionException(type, Double.valueOf(5.00001));
                    testConvert(type, Long.valueOf(5), new BigDecimal("5.000"));
                    testConversionException(type, new BigDecimal("5.001"));
                    testConversionException(type, "5g");
                    testConvertsToNull(type, "");
                    testConvertsToNull(type, null);
                }
                case BINARY, LONGVARBINARY, VARBINARY ->
                {
                }
                case BOOLEAN -> {}
                case CHAR,LONGVARCHAR,VARCHAR ->
                {
                    testConvertsToNull(type, null);
                    // NOTE StandardDataIterator optionally trims, but convert() does not.
                    // see SimpleTranslator.createConvertColumn()
                    testConvert(type, " no trim ", " no trim ");
                    // JdbcType does not convert empty string to null, ColumnInfo.convert() and PropertyType.conver() do
                    assertNull("", type.convert(""));
                    testConvertsToNull(type, "");
                    testConvertsToNull(type, null);
                }
                case DECIMAL -> {}
                case DOUBLE -> {}
                case INTEGER -> {}
                case REAL -> {}
                case SMALLINT, TINYINT -> {}
                case DATE ->
                {
                    testConvert(type, java.sql.Date.valueOf("2024-01-15"), "2024-01-15");
                    testConvert(type, java.sql.Date.valueOf("2024-01-15"), java.sql.Date.valueOf("2024-01-15"));
                    testConvertsToNull(type, null);
                    testConvertsToNull(type, "");
                }
                case TIME ->
                {
                    testConvert(type, java.sql.Time.valueOf("14:30:00"), "14:30:00");
                    testConvert(type, java.sql.Time.valueOf("14:30:00"), java.sql.Time.valueOf("14:30:00"));
                    testConvertsToNull(type, null);
                    testConvertsToNull(type, "");
                }
                case TIMESTAMP ->
                {
                    testConvert(type, java.sql.Timestamp.valueOf("2024-01-15 14:30:00"), "2024-01-15 14:30:00");
                    testConvert(type, java.sql.Timestamp.valueOf("2024-01-15 14:30:00"), java.sql.Timestamp.valueOf("2024-01-15 14:30:00"));
                    testConvertsToNull(type, null);
                    testConvertsToNull(type, "");
                }
                case GUID -> {}
                case ARRAY, NULL, OTHER -> { /* ignore */ }
                default -> fail("We missed a JdbcType: " + type.name());
            }
        }

        // testArray()

        // w/ propertyType
        for (var type : PropertyType.values())
        {
            switch (type)
            {
                case BOOLEAN -> {}
                case STRING ->
                {
                    testConvertsToNull(type, null);
                    testConvertsToNull(type, "");
                    testConvert(type, " no trim ", " no trim ");
                }
                case MULTI_LINE ->
                {
                    testConvertsToNull(type, null);
                    testConvertsToNull(type, "");
                    testConvert(type, " no trim ", " no trim ");
                }
                case MULTI_CHOICE -> {}
                case RESOURCE -> {}
                case INTEGER -> {}
                case BIGINT ->
                {
                    testConvert(type, Long.valueOf(5), Integer.valueOf(5));
                    testConvert(type, Long.valueOf(5), "5");
                    testConversionException(type, new BigDecimal("5.001"));
                    testConvertsToNull(type, null);
                    testConvertsToNull(type, "");
                    testConversionException(type, "5g");
                }
                case BINARY -> {}
                case FILE_LINK -> {}
                case ATTACHMENT -> {}
                case DATE_TIME ->
                {
                    testConvert(type, java.sql.Timestamp.valueOf("2024-01-15 14:30:00"), "2024-01-15 14:30:00");
                    testConvert(type, java.sql.Timestamp.valueOf("2024-01-15 14:30:00"), java.sql.Timestamp.valueOf("2024-01-15 14:30:00"));
                    testConvertsToNull(type, null);
                    testConvertsToNull(type, "");
                }
                case DATE ->
                {
                    testConvert(type, java.sql.Date.valueOf("2024-01-15"), "2024-01-15");
                    testConvert(type, java.sql.Date.valueOf("2024-01-15"), java.sql.Date.valueOf("2024-01-15"));
                    testConvertsToNull(type, null);
                    testConvertsToNull(type, "");
                }
                case TIME ->
                {
                    testConvert(type, java.sql.Time.valueOf("14:30:00"), "14:30:00");
                    testConvert(type, java.sql.Time.valueOf("14:30:00"), java.sql.Time.valueOf("14:30:00"));
                    testConvertsToNull(type, null);
                    testConvertsToNull(type, "");
                }
                case DOUBLE -> {}
                case FLOAT -> {}
                case DECIMAL -> {}
                case XML_TEXT -> {}
                default -> fail("We missed a PropertyType: " + type.name());
            }
        }

        // Quantity
        Unit unit = Unit.kg;
        testQuantity(unit, Quantity.of(5000,unit.getBase()), "5");
    }


    public void testLocked(MutableColumnInfo col)
    {
        col.setAlias("!");
        col.lock();
        try
        {
            col.setAlias("!");
            fail("not locked?");
        }
        catch (IllegalStateException x)
        {
            // success
        }
    }

    static class _ColumnInfo extends BaseColumnInfo
    {
        _ColumnInfo()
        {
            super("~", JdbcType.INTEGER);
        }

        @Override
        public SqlDialect getSqlDialect()
        {
            return CoreSchema.getInstance().getSqlDialect();
        }
    }

    @Test
    public void testLocked()
    {
        testLocked(new _ColumnInfo());
        testLocked(WrappedColumnInfo.wrap(new _ColumnInfo()));
    }
%>
