package org.labkey.api.data;

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
                    .forEach(setCaseSensitive::add);
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
        public void free() throws SQLException
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
        public Object getArray() throws SQLException
        {
            return toArray(new String[size()]);
        }

        @Override
        public Object getArray(Map<String, Class<?>> map) throws SQLException
        {
            return toArray(new String[size()]);
        }

        @Override
        public Object getArray(long index, int count) throws SQLException
        {
            return subList((int) index, (int) index + count).toArray(new String[0]);
        }

        @Override
        public Object getArray(long index, int count, Map<String, Class<?>> map) throws SQLException
        {
            return subList((int) index, (int) index + count).toArray(new String[0]);
        }

        @Override
        public ResultSet getResultSet() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet getResultSet(long index, int count) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) throws SQLException
        {
            throw new UnsupportedOperationException();
        }
    }

    private static final Converter _converter = new Converter();
    static
    {
        ConvertUtils.register(_converter, Array.class);
    }


    public static class Converter implements org.apache.commons.beanutils.Converter, Function<Object,Object>
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
            if (o instanceof List<?> list)
                return (T) new Array(((List<Object>)list).stream());
            return (T) Array.from(o.toString());
        }

        @Override
        final public Object apply(Object o)
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
                Map<String,Object> map = maps.get(0);
                assertTrue(map.get("column") instanceof String);
                String value = (String) map.get("column");
                assertEquals(expected, Array.from(value));
            }
            try (var tsvLoader = new TabLoader.TsvFactory().createLoader(new StringBufferInputStream(oneMultiValueColumn), true))
            {
                var maps = tsvLoader.load();
                assertEquals(1, maps.size());
                Map<String,Object> map = maps.get(0);
                assertTrue(map.get("column") instanceof String);
                String value = (String) map.get("column");
                assertEquals(expected, Array.from(value));
            }
        }
    }
}