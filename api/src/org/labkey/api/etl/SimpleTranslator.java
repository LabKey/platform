/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.api.etl;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 16, 2011
 * Time: 2:03:45 PM
 *
 * SimpleTranslator starts with no output columns (except row number), you must call add() method to add columns.
 */
public class SimpleTranslator extends AbstractDataIterator implements DataIterator
{
    final DataIterator _data;
    protected final ArrayList<Pair<ColumnInfo,Callable>> _outputColumns = new ArrayList<Pair<ColumnInfo,Callable>>();
    boolean _failFast = true;
    Map<String,String> _missingValues = Collections.emptyMap();
    Map<String,Integer> _inputNameMap = null;
    boolean _verbose = false;    // allow more than one error per field
    Set<String> errorFields = new HashSet<String>();

    public SimpleTranslator(DataIterator source, BatchValidationException errors)
    {
        super(errors);
        this._data = source;
        _outputColumns.add(new Pair(new ColumnInfo(source.getColumnInfo(0)), new PassthroughColumn(0)));
    }

    protected DataIterator getInput()
    {
        return _data;
    }

    public void setMvContainer(Container c)
    {
        Map<String,String> _missingValues = MvUtil.getIndicatorsAndLabels(c);
    }


    public void setFailFast(boolean ff)
    {
        _failFast = ff;
    }


    public void setVerbose(boolean v)
    {
        _verbose = v;
    }


    protected void addFieldError(String field, String msg)
    {
        if (errorFields.add(field) || _verbose)
            getRowError().addFieldError(field, msg);
    }

    protected void addConversionException(String fieldName, Object value, JdbcType target, Exception x)
    {
        String msg;
        if (null != value && null != target)
            msg = "Could not convert '" + value + "' for field " + fieldName + ", should be of type " + target.getJavaClass().getSimpleName();
        else if (null != x)
            msg = StringUtils.defaultString(x.getMessage(), x.toString());
        else
            msg = "Could not convert value";
        addFieldError(fieldName, msg);
    }

    private class PassthroughColumn implements Callable
    {
        final int index;

        PassthroughColumn(int index)
        {
            this.index = index;
        }

        @Override
        public Object call() throws Exception
        {
            return _data.get(index);
        }
    }


    private class SimpleConvertColumn implements Callable
    {
        final int index;
        final JdbcType type;
        final String fieldName;

        SimpleConvertColumn(String fieldName, int indexFrom, JdbcType to)
        {
            this.fieldName = fieldName;
            this.index = indexFrom;
            this.type = to;
        }

        @Override
        final public Object call() throws Exception
        {
            Object value = _data.get(index);
            try
            {
                return convert(value);
            }
            catch (ConversionException x)
            {
                addConversionException(fieldName, value, type, x);
            }
            return null;
        }

        protected Object convert(Object o)
        {
            return type.convert(o);
        }
    }


    public static class GuidColumn implements Callable
    {
        @Override
        public Object call() throws Exception
        {
            return GUID.makeGUID();
        }
    }


    public static class AutoIncrementColumn implements Callable
    {
        private int _autoIncrement = -1;

        protected int getFirstValue()
        {
            return 0;
        }

        @Override
        public Object call() throws Exception
        {
            if (_autoIncrement == -1)
                _autoIncrement = getFirstValue();
            return ++_autoIncrement;
        }
    }


    private class MissingValueConvertColumn extends SimpleConvertColumn
    {
        final int indicator;

        MissingValueConvertColumn(String fieldName, int index,JdbcType to)
        {
            super(fieldName, index, to);
            indicator = 0;
        }

        MissingValueConvertColumn(String fieldName, int index, int indexIndicator, JdbcType to)
        {
            super(fieldName, index, to);
            indicator = indexIndicator;
        }

        @Override
        public Object convert(Object v)
        {
            Object mv = 0==indicator ? null : _data.get(indicator);

            if (v instanceof String && StringUtils.isEmpty((String)v))
                v = null;
            if (null != mv && !(mv instanceof String))
                mv = String.valueOf(mv);
            if (null != mv && ((String)mv).length() == 0)
                mv = null;

            if (null != mv)
            {
                if (v != null && !v.equals(mv))
                    addFieldError(_data.getColumnInfo(index).getName(), "Column has value and missing-value indicator");
                return new MvFieldWrapper(v, String.valueOf(mv));
            }

            if (v instanceof String)
            {
                if (_missingValues.containsKey(v))
                    return new MvFieldWrapper(null, (String)v);
            }

            return type.convert(v);
        }
    }


    private class PropertyConvertColumn extends SimpleConvertColumn
    {
        PropertyDescriptor pd;
        PropertyType pt;

        PropertyConvertColumn(String fieldName, int fromIndex, PropertyDescriptor pd, PropertyType pt)
        {
            super(fieldName, fromIndex, pt.getJdbcType());
            this.pd = pd;
            this.pt = pt;
        }

        @Override
        public Object convert(Object value)
        {
            if (value instanceof MvFieldWrapper)
                return value;

            if (pd.isMvEnabled() && null != value)
            {
                if (MvUtil.isMvIndicator(value.toString(), pd.getContainer()))
                    return new MvFieldWrapper(null, value.toString());
            }

            if (null != value && null != pt)
                value = pt.convert(value);

            return value;
        }
    }


    private class RemapColumn implements Callable
    {
        final int _index;
        final Map<Object,Object> _map;
        final boolean _strict;

        RemapColumn(int index, Map<Object,Object> map, boolean strict)
        {
            _index = index;
            _map = map;
            _strict = strict;
        }

        @Override
        public Object call() throws Exception
        {
            Object k = _data.get(_index);
            if (null == k)
                return null;
            Object v = _map.get(k);
            if (null != v || !_strict || _map.containsKey(k))
                return v;
            addFieldError(_data.getColumnInfo(_index).getName(), "Couldn't not transalte value: " + String.valueOf(k));
            return null;
        }
    }
    

    /* use same value for all rows, set value on first usage */
    Timestamp _ts = null;

    private class TimestampColumn implements Callable
    {
        @Override
        public Object call() throws Exception
        {
            if (null == _ts)
                _ts =  new Timestamp(System.currentTimeMillis());
            return _ts;
        }
    }


    Map<String,Integer> getColumnNameMap()
    {
        if (null == _inputNameMap)
        {
            _inputNameMap = DataIteratorUtil.createColumnNameMap(_data);
        }
        return _inputNameMap;
    }


    /*
     * CONFIGURE methods
     */

    public void selectAll()
    {
        for (int i=1 ; i<=_data.getColumnCount() ; i++)
        {
            ColumnInfo ci = _data.getColumnInfo(i);
            _outputColumns.add(new Pair<ColumnInfo, Callable>(new ColumnInfo(ci), new PassthroughColumn(i)));
        }
    }


    public void removeColumn(int index)
    {
        _outputColumns.remove(index);
    }


    public int addColumn(ColumnInfo col, Callable call)
    {
        _outputColumns.add(new Pair<ColumnInfo, Callable>(col, call));
        return _outputColumns.size()-1;
    }


    public int addColumn(String name, int fromIndex)
    {
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        return addColumn(col, new PassthroughColumn(fromIndex));
    }

    public int addConvertColumn(ColumnInfo col, int fromIndex, boolean mv)
    {
        if (mv)
            return addColumn(col, new SimpleConvertColumn(col.getName(), fromIndex, col.getJdbcType()));
        else
            return addColumn(col, new MissingValueConvertColumn(col.getName(), fromIndex, col.getJdbcType()));
    }

    public int addConvertColumn(String name, int fromIndex, JdbcType toType, boolean mv)
    {
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        col.setJdbcType(toType);
        return addConvertColumn(col, fromIndex, mv);
    }

    public int addConvertColumn(String name, int fromIndex, PropertyDescriptor pd, PropertyType pt)
    {
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        col.setJdbcType(pt.getJdbcType());
        return addColumn(col, new PropertyConvertColumn(name, fromIndex, pd, pt));
    }


    public static DataIterator wrapBuiltInColumns(DataIterator in , BatchValidationException errors, @Nullable Container c, @NotNull User user, @NotNull TableInfo target)
    {
        SimpleTranslator t;
        if (in instanceof SimpleTranslator)
            t = (SimpleTranslator)in;
        else
        {
            t = new SimpleTranslator(in, errors);
            t.selectAll();
        }
        t.addBuiltInColumns(c, user, target, false);
        return t;
    }


    enum When
    {
        insert,
        update,
        both
    }

    enum SpecialColumn
    {
        Container(When.insert),
        Owner(When.insert),
        CreatedBy(When.insert),
        Created(When.insert),
        ModifiedBy(When.both),
        Modified(When.both);

        SpecialColumn(When w)
        {
        }
    }


    /**
     * Provide values for common built-in columns.  Usually we do not allow the user to specify values for these columns,
     * so matching columns in the input are ignored.
     * @param allowPassThrough indicates that columns in the input iterator should not be ignored
     */
    public void addBuiltInColumns(@Nullable Container c, @NotNull User user, @NotNull TableInfo target, boolean allowPassThrough)
    {
        final String containerId = null==c ? null : c.getId();
        final Integer userId = null==user ? 0 : user.getUserId();

        Callable containerCallable = new Callable(){public Object call() {return containerId;}};
        Callable userCallable = new Callable(){public Object call() {return userId;}};
        Callable tsCallable = new TimestampColumn();

        Map<String, Integer> inputCols = getColumnNameMap();
        Map<String, Integer> outputCols = new CaseInsensitiveHashMap<Integer>();
        for (int i=1 ; i<_outputColumns.size() ; i++)
            outputCols.put(_outputColumns.get(i).getKey().getName(), i);

        addBuiltinColumn(SpecialColumn.Container,  allowPassThrough, target, inputCols, outputCols, containerCallable);
        addBuiltinColumn(SpecialColumn.Owner,      allowPassThrough, target, inputCols, outputCols, userCallable);
        addBuiltinColumn(SpecialColumn.CreatedBy,  allowPassThrough, target, inputCols, outputCols, userCallable);
        addBuiltinColumn(SpecialColumn.ModifiedBy, allowPassThrough, target, inputCols, outputCols, userCallable);
        addBuiltinColumn(SpecialColumn.Created,    allowPassThrough, target, inputCols, outputCols, tsCallable);
        addBuiltinColumn(SpecialColumn.Modified,   allowPassThrough, target, inputCols, outputCols, tsCallable);
    }


    private int addBuiltinColumn(SpecialColumn e, boolean allowPassThrough, TableInfo target, Map<String,Integer> inputCols, Map<String,Integer> outputCols, Callable c)
    {
        String name = e.name();
        ColumnInfo col = target.getColumn(name);
        if (null==col)
            return 0;

        Integer indexOut = outputCols.get(name);
        Integer indexIn = inputCols.get(name);

        // not selected already
        if (null == indexOut)
        {
            if (allowPassThrough && null != indexIn)
                return addColumn(name, indexIn);
            else
            {
                _outputColumns.add(new Pair(new ColumnInfo(name, col.getJdbcType()), c));
                return _outputColumns.size()-1;
            }
        }
        // selected already
        else
        {
            if (!allowPassThrough)
                _outputColumns.set(indexOut, new Pair(new ColumnInfo(name, col.getJdbcType()), c));
            return indexOut;
        }
    }
    

    /** implementation **/

    @Override
    public int getColumnCount()
    {
        return _outputColumns.size()-1;
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _outputColumns.get(i).getKey();
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        _rowError = null;
        if (_failFast && _errors.hasErrors())
            return false;
        return _data.next();
    }

    @Override
    public Object get(int i)
    {
        Callable c = _outputColumns.get(i).getValue();

        try
        {
            return c.call();
        }
        catch (ConversionException x)
        {
            // preferable to handle in call()
            addConversionException(_outputColumns.get(i).getKey().getName(), null, null, x);
            return null;
        }
        catch (RuntimeException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            // undone source field name???
            addFieldError(_outputColumns.get(i).getKey().getName(), x.getMessage());
            return null;
        }
    }

    @Override
    public void beforeFirst()
    {
        _data.beforeFirst();
    }

    @Override
    public boolean isScrollable()
    {
        return _data.isScrollable();
    }

    @Override
    public void close() throws IOException
    {
        _data.close();
    }

    /*
    * Tests
    */



    private static String[] as(String... arr)
    {
        return arr;
    }

    public static class TranslateTestCase extends Assert
    {
        StringTestIterator simpleData = new StringTestIterator
        (
            Arrays.asList("IntNotNull", "Text", "EntityId", "Int"),
            Arrays.asList(
                as("1", "one", GUID.makeGUID(), ""),
                as("2", "two", GUID.makeGUID(), "/N"),
                as("3", "three", GUID.makeGUID(), "3")
            )
        );

        public TranslateTestCase()
        {
            simpleData.setScrollable(true);
        }


        @Test
        public void passthroughTest() throws Exception
        {
            BatchValidationException errors = new BatchValidationException();
            simpleData.beforeFirst();
            SimpleTranslator t = new SimpleTranslator(simpleData, errors);
            t.selectAll();
            assert(t.getColumnCount() == simpleData.getColumnCount());
            assertTrue(t.getColumnInfo(0).getJdbcType() == JdbcType.INTEGER);
            for (int i=1 ; i<=t.getColumnCount() ; i++)
                assertTrue(t.getColumnInfo(i).getJdbcType() == JdbcType.VARCHAR);
            for (int i=1 ; i<=3 ; i++)
            {
                assertTrue(t.next());
                assertEquals(t.get(0), i);
                assertEquals(t.get(1), String.valueOf(i));
            }
            assertFalse(t.next());
        }


        @Test
        public void convertTest() throws Exception
        {
            // w/o errors
            {
                BatchValidationException errors = new BatchValidationException();
                simpleData.beforeFirst();
                SimpleTranslator t = new SimpleTranslator(simpleData, errors);
                t.addConvertColumn("IntNotNull", 1, JdbcType.INTEGER, false);
                assertEquals(1, t.getColumnCount());
                assertEquals(JdbcType.INTEGER, t.getColumnInfo(0).getJdbcType());
                assertEquals(JdbcType.INTEGER, t.getColumnInfo(1).getJdbcType());
                for (int i=1 ; i<=3 ; i++)
                {
                    assertTrue(t.next());
                    assertEquals(i, t.get(0));
                    assertEquals(i, t.get(1));
                }
                assertFalse(t.next());
            }

            // w/ errors failfast==true
            {
                BatchValidationException errors = new BatchValidationException();
                simpleData.beforeFirst();
                SimpleTranslator t = new SimpleTranslator(simpleData, errors);
                t.setFailFast(true);
                t.addConvertColumn("Text", 2, JdbcType.INTEGER, false);
                assertEquals(t.getColumnCount(), 1);
                assertEquals(t.getColumnInfo(0).getJdbcType(), JdbcType.INTEGER);
                assertEquals(t.getColumnInfo(1).getJdbcType(), JdbcType.INTEGER);
                assertTrue(t.next());
                assertEquals(1, t.get(0));
                assertNull(t.get(1));
                assertFalse(t.next());
                assertTrue(errors.hasErrors());
            }

            // w/ errors failfast==false
            {
                BatchValidationException errors = new BatchValidationException();
                simpleData.beforeFirst();
                SimpleTranslator t = new SimpleTranslator(simpleData, errors);
                t.setFailFast(false);
                t.setVerbose(true);
                t.addConvertColumn("Text", 2, JdbcType.INTEGER, false);
                assertEquals(t.getColumnCount(), 1);
                assertEquals(t.getColumnInfo(0).getJdbcType(), JdbcType.INTEGER);
                assertEquals(t.getColumnInfo(1).getJdbcType(), JdbcType.INTEGER);
                for (int i=1 ; i<=3 ; i++)
                {
                    assertTrue(t.next());
                    assertEquals(i, t.get(0));
                    assertNull(t.get(1));
                    assertTrue(errors.hasErrors());
                }
                assertFalse(t.next());
                assertEquals(3, errors.getRowErrors().size());
            }

            // missing values
            {
            }
        }


        @Test
        public void missingTest()
        {

        }

        @Test
        public void builtinColumns()
        {
            TableInfo t = DbSchema.get("test").getTable("testtable");
        }
    }
}
