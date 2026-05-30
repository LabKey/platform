/*
 * Copyright (c) 2025-2026 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.ontology.Unit;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.HtmlWriter;

import java.io.StringBufferInputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.SortHelpers.CASE_INSENSITIVE_UPPERCASE_FIRST;

public class MultiChoice
{
    public static final String ARRAY_MARKER = "[]";

    public static class DisplayColumn extends DataColumn
    {
        public DisplayColumn(ColumnInfo col)
        {
            super(col, false);
            setTextAlign("left"); //  GitHub Issue 933: Left align MV cells in grids
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            return getArrayValue(ctx);
        }

        public Array getArrayValue(RenderContext ctx)
        {
            Object v = super.getValue(ctx);
            if (!(v instanceof java.sql.Array array))
                return Array.from(new Object[]{v});
            return Array.from(array);
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }

        @Override
        protected String getStringValue(Object value, Unit unit, boolean disabledInput)
        {
            // Because MultiChoice.Array implements Collection ConvertUtils will return convert(Array.get(0).toString()))
            if (value instanceof Array)
                return value.toString();
            return super.getStringValue(value, unit, disabledInput);
        }

        @Override
        public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
        {
            MultiChoice.Array array;
            if (null == value)
                array = new MultiChoice.Array(new String[]{});
            else if (value instanceof MultiChoice.Array mca)
                array = mca;
            else
                array = _converter.convert(MultiChoice.Array.class, value);

            boolean disabledInput = isDisabledInput(ctx);
            String formFieldName = getFormFieldName(ctx);
            ColumnInfo boundColumn = getBoundColumn();
            IPropertyValidator textChoiceValidator = null==boundColumn ? null : PropertyService.get().getValidatorForColumn(boundColumn, PropertyValidatorType.TextChoice);

            if (textChoiceValidator != null)
            {
                setInputType("select.multiple");
                renderTextChoiceFormInput(out, ARRAY_MARKER+formFieldName, array, array, disabledInput, textChoiceValidator);
            }
            else
            {
                renderTextFormInput(out, formFieldName, array, array.toString(), disabledInput);
            }
        }

        @Override
        public void renderInputCell(RenderContext ctx, HtmlWriter out)
        {
            super.renderInputCell(ctx, out);
        }

        @Override
        public @NotNull HtmlString getFormattedHtml(RenderContext ctx)
        {
            Array array = (Array) getValue(ctx);

            if (null == array || array.isEmpty())
                return HtmlString.EMPTY_STRING;

            return HtmlString.of(
                DIV(array.stream().map(v -> SPAN(at(style,"border:solid 1px #DDDDDD; border-radius:3px; padding:4px;"), v))
                        .collect(new JoinRenderable(HtmlString.SP))));
        }

        @Override
        public String getTsvFormattedValue(RenderContext ctx)
        {
            Array values = getArrayValue(ctx);
            if (null != values && !values.isEmpty())
            {
                return PageFlowUtil.joinValuesToStringForExport(values);
            }
            return null;
        }

        @Override
        public Object getExcelCompatibleValue(RenderContext ctx)
        {
            return getTsvFormattedValue(ctx);
        }

        @Override
        public Object getExportCompatibleValue(RenderContext ctx)
        {
            return getTsvFormattedValue(ctx);
        }
    }


    /* could also use .flatMap() */
    static class JoinRenderable implements Collector<DOM.Renderable,ArrayList<DOM.Renderable>,List<DOM.Renderable>>
    {
        private final DOM.Renderable separator;

        JoinRenderable(DOM.Renderable separator)
        {
            this.separator = separator;
        }

        @Override
        public BiConsumer<ArrayList<DOM.Renderable>, DOM.Renderable> accumulator()
        {
            return (l, r) -> {
                if (null == r)
                    return;
                if (!l.isEmpty())
                    l.add(separator);
                l.add(r);};
        }

        @Override
        public Supplier<ArrayList<DOM.Renderable>> supplier()
        {
            return ArrayList::new;
        }

        @Override
        public BinaryOperator<ArrayList<DOM.Renderable>> combiner()
        {
            return (l1,l2) -> {l1.addAll(l2); return l1;};
        }

        @Override
        public Function<ArrayList<DOM.Renderable>, List<DOM.Renderable>> finisher()
        {
            return l->l;
        }

        @Override
        public Set<Characteristics> characteristics()
        {
            return Set.of();
        }
    }


    // LK impl to help with conversions
    public static class Array implements List<String>, java.sql.Array
    {
        public static final Array EMPTY = new Array(new String[0]);

        final String[] array;
        List<String> list = null;

        protected Array(Stream<Object> str)
        {
            TreeSet<String> setCaseSensitive = new TreeSet<>(CASE_INSENSITIVE_UPPERCASE_FIRST);
            str.filter(Objects::nonNull)
                    .map(s -> StringUtils.trimToNull(s.toString()))
                    .filter(Objects::nonNull)
                    .forEach(s ->
                    {
                        // GitHub Issue 942: Add error for duplicate values for MVTC fields
                        if (!setCaseSensitive.add(s))
                            throw new ConversionExceptionWithMessage("Duplicate value provided: " + s);
                    });
            array = setCaseSensitive.toArray(new String[0]);
        }

        protected Array(Object[] array)
        {
            this(Stream.of(array));
        }

        public String[] getStringArray()
        {
            return array;
        }

        private List<String> getList()
        {
            if (null == list)
                list = Collections.unmodifiableList(Arrays.asList(array));
            return list;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof Array arr))
                return false;
            return Arrays.equals(array, arr.array);
        }

        @Override
        public int hashCode()
        {
            return Arrays.hashCode(array);
        }

        @Override
        public String toString()
        {
            return PageFlowUtil.joinValuesToStringForExport(this);
        }

        public static Array from(Object @NotNull [] values)
        {
            return new Array(Arrays.stream(values));
        }

        public static Array from(@NotNull org.json.JSONArray array)
        {
            return new Array(StreamSupport.stream(array.spliterator(), false));
        }

        public static Array from(@NotNull String s)
        {
            if (isBlank(s))
                return EMPTY;
            if (s.startsWith("{") && s.endsWith("}"))
            {
                try
                {
                    return parsePgArray(s);
                }
                catch (ConversionException ignore)
                {
                }

            }
            List<String> split = PageFlowUtil.splitStringToValuesForImport(s);
            return from(split.toArray());
        }

        public static Array from(@NotNull java.sql.Array sqlArray)
        {
            try
            {
                // JDBC spec says java.sql.Array can return a primitive array!
                Object o = sqlArray.getArray();
                Object[] array;
                if (!o.getClass().getComponentType().isPrimitive())
                {
                    array = (Object[]) o;
                }
                else
                {
                    array = new Object[java.lang.reflect.Array.getLength(o)];
                    for (int i = 0; i < array.length; i++)
                        array[i] = java.lang.reflect.Array.get(o, i);
                }
                return new Array(array);
            }
            catch (SQLException x)
            {
                throw new RuntimeException(x);
            }
        }

        /**
         * Parse a PostgreSQL array text representation, e.g. {@code {a,b,"c d"}}.
         * <p>
         * PostgreSQL uses backslash escaping inside quoted elements ({@code \"} for a literal
         * double-quote, {@code \\} for a literal backslash), unlike CSV which doubles quotes.
         * Unquoted elements are trimmed of whitespace.
         */
        public static Array parsePgArray(@NotNull String s)
        {
            if (isBlank(s))
                return EMPTY;

            s = s.trim();
            if (!s.startsWith("{") || !s.endsWith("}"))
                throw new ConversionException("PostgreSQL array literal must be wrapped in {}: " + s);

            String inner = s.substring(1, s.length() - 1);
            if (inner.isEmpty())
                return EMPTY;

            List<Object> values = new ArrayList<>();
            int len = inner.length();
            int i = 0;

            while (i < len)
            {
                // skip leading whitespace before element
                while (i < len && Character.isWhitespace(inner.charAt(i)))
                    i++;

                if (i >= len)
                    break;

                if (inner.charAt(i) == '"')
                {
                    // quoted element — backslash escaping
                    i++; // skip opening quote
                    StringBuilder sb = new StringBuilder();
                    while (i < len)
                    {
                        char c = inner.charAt(i);
                        if (c == '\\' && i + 1 < len)
                        {
                            sb.append(inner.charAt(i + 1));
                            i += 2;
                        }
                        else if (c == '"')
                        {
                            i++; // skip closing quote
                            break;
                        }
                        else
                        {
                            sb.append(c);
                            i++;
                        }
                        if (i >= len)
                            throw new ConversionException("Unterminated quoted string in PostgreSQL array literal");
                    }
                    values.add(sb.toString());

                    // after closing quote, expect comma or end
                    while (i < len && Character.isWhitespace(inner.charAt(i)))
                        i++;
                    if (i < len)
                    {
                        if (inner.charAt(i) == ',')
                            i++; // consume delimiter
                        else
                            throw new ConversionException("Unexpected character after closing quote in PostgreSQL array literal at position " + i);
                    }
                }
                else
                {
                    // unquoted element — read until comma
                    int start = i;
                    while (i < len && inner.charAt(i) != ',')
                        i++;
                    String token = inner.substring(start, i).trim();
                    values.add(token);
                    if (i < len)
                        i++; // skip comma
                }
            }

            return from(values.toArray());
        }

        //
        //  implements List
        //

        @Override
        public boolean add(String s)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size()
        {
            return array.length;
        }

        @Override
        public boolean isEmpty()
        {
            return array.length == 0;
        }

        @Override
        public boolean contains(Object o)
        {
            return getList().contains(o);
        }

        @Override
        public @NotNull Iterator<String> iterator()
        {
            return getList().iterator();
        }

        @Override
        public Object @NotNull [] toArray()
        {
            return 0==array.length ? array : array.clone();
        }

        @Override
        public <T> T @NotNull [] toArray(T @NotNull [] a)
        {
            if (a.length == 0 && a.getClass().getComponentType().isAssignableFrom(String.class))
                return (T[])array.clone();
            return getList().toArray(a);
        }

        @Override
        public boolean remove(Object o)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c)
        {
            return getList().containsAll(c);
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends String> c)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(int index, @NotNull Collection<? extends String> c)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String get(int index)
        {
            return array[index];
        }

        @Override
        public String set(int index, String element)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(int index, String element)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String remove(int index)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int indexOf(Object o)
        {
            return getList().indexOf(o);
        }

        @Override
        public int lastIndexOf(Object o)
        {
            return getList().lastIndexOf(o);
        }

        @Override
        public @NotNull ListIterator<String> listIterator()
        {
            return getList().listIterator();
        }

        @Override
        public @NotNull ListIterator<String> listIterator(int index)
        {
            return getList().listIterator(index);
        }

        @Override
        public @NotNull List<String> subList(int fromIndex, int toIndex)
        {
            return getList().subList(fromIndex, toIndex);
        }


        //
        // implements Array
        //

        @Override
        public void free()
        {

        }

        @Override
        public String getBaseTypeName()
        {
            return "VARCHAR";
        }

        @Override
        public int getBaseType()
        {
            return Types.VARCHAR;
        }

        @Override
        public Object getArray()
        {
            return toArray(new String[size()]);
        }

        @Override
        public Object getArray(Map<String, Class<?>> map)
        {
            return toArray(new String[size()]);
        }

        @Override
        public Object getArray(long index, int count)
        {
            return subList((int) index, (int) index + count).toArray(new String[0]);
        }

        @Override
        public Object getArray(long index, int count, Map<String, Class<?>> map)
        {
            return subList((int) index, (int) index + count).toArray(new String[0]);
        }

        @Override
        public ResultSet getResultSet()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet getResultSet(Map<String, Class<?>> map)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet getResultSet(long index, int count)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet getResultSet(long index, int count, Map<String, Class<?>> map)
        {
            throw new UnsupportedOperationException();
        }
    }

    private static final Converter _converter = new Converter();
    static
    {
        ConvertUtils.register(_converter, Array.class);
    }


    public static class Converter implements org.apache.commons.beanutils.Converter, SimpleConvert
    {
        private Converter()
        {
        }

        public static Converter getInstance()
        {
            return _converter;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T convert(Class<T> aClass, Object o)
        {
            if (null == o)
                return (T) Array.EMPTY;
            if (o instanceof MultiChoice.Array arr)
                return (T)arr;
            if (o instanceof String s)
                return (T) Array.from(s);
            if (o.getClass().isArray())
                return (T) Array.from((Object[]) o);
            if (o instanceof org.json.JSONArray json)
                return (T) Array.from(json);
            if (o instanceof java.sql.Array arr)
                return (T) Array.from(arr);
            if (o instanceof List<?> list)
                return (T) new Array(((List<Object>)list).stream());
            return (T) Array.from(o.toString());
        }

        @Override
        final public Object convert(Object o)
        {
            return convert(MultiChoice.Array.class, o);
        }
    }



    public static class TestCase extends Assert
    {
        @Test
        public void testConvert() throws Exception
        {
            Array expected = Array.from(new String[]{"a,","b\"","c "});

            assertEquals(expected, _converter.convert(Array.class, expected));
            assertEquals(expected, _converter.convert(Array.class, "\"a,\",\"b\"\"\",\"c \""));
            assertEquals(expected, _converter.convert(Array.class, new String[]{"a,","b\"","c "}));
            assertEquals(expected, _converter.convert(Array.class, List.of("a,","b\"","c ")));
            assertEquals(expected, _converter.convert(Array.class, new JSONArray(List.of("a,","b\"","c "))));
            // test that result is ordered
            assertEquals(expected, _converter.convert(Array.class, "\"c \",\"b\"\"\",\"a,\""));

            // empty
            assertEquals(0, _converter.convert(Array.class, " ").size());
            assertEquals(0, _converter.convert(Array.class, "").size());
            assertEquals(0, _converter.convert(Array.class, null).size());
        }

        @Test
        public void testParsePgArray()
        {
            // simple unquoted values
            assertEquals(Array.from(new String[]{"a", "b", "c"}), Array.parsePgArray("{a,b,c}"));

            // quoted values with special chars: comma, escaped quote, escaped backslash
            assertEquals(Array.from(new String[]{"a,b", "c\"d", "e\\f"}), Array.parsePgArray("{\"a,b\",\"c\\\"d\",\"e\\\\f\"}"));

            // empty array
            assertEquals(Array.EMPTY, Array.parsePgArray("{}"));

            // blank/empty input
            assertEquals(Array.EMPTY, Array.parsePgArray(""));
            assertEquals(Array.EMPTY, Array.parsePgArray("  "));

            // quoted empty string — filtered by Array constructor (trimToNull)
            assertEquals(Array.from(new String[]{"a"}), Array.parsePgArray("{\"\",a}"));

            // whitespace in quoted elements — trimmed by Array constructor
            assertEquals(Array.from(new String[]{"a", "b"}), Array.parsePgArray("{\" a \",b}"));

            // whitespace around braces
            assertEquals(Array.from(new String[]{"x", "y"}), Array.parsePgArray("  {x,y}  "));

            // round-trip: values from testConvert
            Array expected = Array.from(new String[]{"a,", "b\"", "c "});
            assertEquals(expected, Array.parsePgArray("{\"a,\",\"b\\\"\",\"c \"}"));


            List<String> specialCharArrays = Array.from(new String[]{
                    "&^G'{\"И<2&)&]#~%:\uD83D\uDC7E*!안GaC;",
                    ",~-",
                    "<=0\\!41%d!By&]b",
                    "A)D'z:&",
                    "b$Dyf)D;C@",
                    "c_x-eИ",
                    "d[dF2cは=&G&1",
                    "e^\"#x"
            });
            String expectedSpecialCharStr = "{\"&^G'{\\\"И<2&)&]#~%:\uD83D\uDC7E*!안GaC;\",\"\\,~-\",\"<=0\\\\!41%d!By&]b\",\"A)D'z:&\",\"b$Dyf)D;C@\",\"c_x-eИ\",\"d[dF2cは=&G&1\",\"e^\\\"#x\"}";
            assertEquals(specialCharArrays, Array.parsePgArray(expectedSpecialCharStr));


            // error: missing braces
            try
            {
                Array.parsePgArray("a,b,c");
                fail("Expected ConversionException for missing braces");
            }
            catch (ConversionException ignored) {}

            // error: unterminated quote
            try
            {
                Array.parsePgArray("{\"abc}");
                fail("Expected ConversionException for unterminated quote");
            }
            catch (ConversionException ignored) {}
        }

        @Test
        public void testCSV() throws Exception
        {
            Array expected = Array.from(new String[]{"a,", "b\\", "c,d", "e\"f"});
            // toString() == "a,", b\, "c,d", "e""f"
            assertEquals("\"a,\", b\\, \"c,d\", \"e\"\"f\"", expected.toString());

            // csv/tsv with double double-quote escaping
            // add " around entire value, and double the "
            // (need to use some \" to avoid ending the """ """ block)
            String oneMultiValueColumn = """
                column
                ""\"a,"", b\\, ""c,d"", ""e""\""f""\"
                """;

            try (var csvLoader = new TabLoader.CsvFactory().createLoader(new StringBufferInputStream(oneMultiValueColumn), true))
            {
                var maps = csvLoader.load();
                assertEquals(1, maps.size());
                Map<String,Object> map = maps.getFirst();
                assertTrue(map.get("column") instanceof String);
                String value = (String) map.get("column");
                assertEquals(expected, Array.from(value));
            }
            try (var tsvLoader = new TabLoader.TsvFactory().createLoader(new StringBufferInputStream(oneMultiValueColumn), true))
            {
                var maps = tsvLoader.load();
                assertEquals(1, maps.size());
                Map<String,Object> map = maps.getFirst();
                assertTrue(map.get("column") instanceof String);
                String value = (String) map.get("column");
                assertEquals(expected, Array.from(value));
            }
        }
    }
}