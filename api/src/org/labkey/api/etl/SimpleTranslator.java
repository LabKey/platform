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
import org.jetbrains.annotations.NotNull;
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
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 16, 2011
 * Time: 2:03:45 PM
 */
public class SimpleTranslator implements DataIterator
{
    final ValidationException _errors;
    final DataIterator _data;
    final ArrayList<Pair<ColumnInfo,Callable>> _outputColumns = new ArrayList<Pair<ColumnInfo,Callable>>();
    boolean _failFast = true;
    Map<String,String> _missingValues = Collections.emptyMap();
    Map<String,Integer> _inputNameMap = null;


    public SimpleTranslator(DataIterator source, ValidationException errors)
    {
        this._data = source;
        this._errors = errors;
        _outputColumns.add(new Pair(new ColumnInfo(source.getColumnInfo(0)), new PassthroughColumn(0)));
    }


    public void setMvContainer(Container c)
    {
        Map<String,String> _missingValues = MvUtil.getIndicatorsAndLabels(c);
    }


    public void setFailFast(boolean ff)
    {
        _failFast = ff;
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

        SimpleConvertColumn(int index, JdbcType to)
        {
            this.index = index;
            this.type = to;
        }

        @Override
        public Object call() throws Exception
        {
            Object o = _data.get(index);
            return type.convert(o);
        }
    }


    private class MissingValueConvertColumn extends SimpleConvertColumn
    {
        final int indicator;
        final Object missingValue;

        MissingValueConvertColumn(int index, int indexIndicator, Object missing, JdbcType to)
        {
            super(index, to);
            indicator = indexIndicator;
            this.missingValue = missing;
        }

        @Override
        public Object call() throws Exception
        {
            Object v = _data.get(index);
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
                    _errors.addFieldError(_data.getColumnInfo(index).getName(), "Column has value and missing-value indicator");
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
            _inputNameMap = new CaseInsensitiveHashMap<Integer>();
            for (int i=1 ; i<=_data.getColumnCount() ; i++)
                _inputNameMap.put(_data.getColumnInfo(i).getName(), i);
        }
        return _inputNameMap;
    }


    /** configure **/

    public void selectAll()
    {
        for (int i=1 ; i<=_data.getColumnCount() ; i++)
        {
            ColumnInfo ci = _data.getColumnInfo(i);
            _outputColumns.add(new Pair<ColumnInfo, Callable>(new ColumnInfo(ci), new PassthroughColumn(i)));
        }
    }


    public void addColumn(String name, int fromIndex)
    {
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        _outputColumns.add(new Pair<ColumnInfo, Callable>(col, new PassthroughColumn(fromIndex)));
    }


    public void convertColumn(String name, int fromIndex, JdbcType toType)
    {
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        col.setJdbcType(toType);
        _outputColumns.add(new Pair<ColumnInfo, Callable>(col, new SimpleConvertColumn(fromIndex, toType)));
    }


    /* container, {versinoColumn}, owner, createdby, created, modifiedby, modified */
    public void addBuiltinColumns(@NotNull Container c, @NotNull User user, @NotNull TableInfo target)
    {
        final String containerId = null==c ? null : c.getId();
        final Integer userId = null==user ? 0 : user.getUserId();

        Callable containerCallable = new Callable(){public Object call() {return containerId;}};
        Callable userCallable = new Callable(){public Object call() {return userId;}};
        Callable tsCallable = new TimestampColumn();

        // container
        if (null != target.getColumn("Container") && !getColumnNameMap().containsKey("Container"))
            _outputColumns.add(new Pair(new ColumnInfo("Container", JdbcType.VARCHAR), containerCallable));
        // user
        if (null != target.getColumn("Owner") && !getColumnNameMap().containsKey("Owner"))
            _outputColumns.add(new Pair(new ColumnInfo("Owner", JdbcType.INTEGER), userCallable));
        if (null != target.getColumn("CreatedBy") && !getColumnNameMap().containsKey("CreatedBy"))
            _outputColumns.add(new Pair(new ColumnInfo("CreatedBy", JdbcType.INTEGER), userCallable));
        if (null != target.getColumn("ModifiedBy") && !getColumnNameMap().containsKey("ModifiedBy"))
            _outputColumns.add(new Pair(new ColumnInfo("ModifiedBy", JdbcType.INTEGER), userCallable));
        // timestamp
        if (null != target.getColumn("Created") && !getColumnNameMap().containsKey("Created"))
            _outputColumns.add(new Pair(new ColumnInfo("Created", JdbcType.TIMESTAMP), tsCallable));
        if (null != target.getColumn("Modified") && !getColumnNameMap().containsKey("Modified"))
            _outputColumns.add(new Pair(new ColumnInfo("Modified", JdbcType.TIMESTAMP), tsCallable));
        ColumnInfo version = target.getVersionColumn();
        if (null != version && version.getJdbcType() == JdbcType.TIMESTAMP && !getColumnNameMap().containsKey(version.getColumnName()))
            _outputColumns.add(new Pair(new ColumnInfo(version), tsCallable));
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
    public boolean next() throws ValidationException
    {
        if (_failFast && _errors.hasErrors())
            return false;
        return _data.next();
    }

    @Override
    public Object get(int i)
    {
        try
        {
            return _outputColumns.get(i).getValue().call();
        }
        catch (ConversionException x)
        {
            _errors.addFieldError(_outputColumns.get(i).getKey().getName(), x.getMessage());
            return null;
        }
        catch (RuntimeException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            // undone source field name???
            _errors.addFieldError(_outputColumns.get(i).getKey().getName(), x.getMessage());
            return null;
        }
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
        String guid = GUID.makeGUID();
        StringTestIterator simpleData = new StringTestIterator
        (
            Arrays.asList("IntNotNull", "Text", "EntityId", "Int"),
            Arrays.asList(
                as("1", "one", GUID.makeGUID(), ""),
                as("2", "two", GUID.makeGUID(), "/N"),
                as("3", "three", GUID.makeGUID(), "3")
            )
        );

        @Test
        public void passthroughTest() throws Exception
        {
            ValidationException errors = new ValidationException();
            simpleData.reset();
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
                ValidationException errors = new ValidationException();
                simpleData.reset();
                SimpleTranslator t = new SimpleTranslator(simpleData, errors);
                t.convertColumn("IntNotNull", 1, JdbcType.INTEGER);
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
                ValidationException errors = new ValidationException();
                simpleData.reset();
                SimpleTranslator t = new SimpleTranslator(simpleData, errors);
                t.setFailFast(true);
                t.convertColumn("Text", 2, JdbcType.INTEGER);
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
                ValidationException errors = new ValidationException();
                simpleData.reset();
                SimpleTranslator t = new SimpleTranslator(simpleData, errors);
                t.setFailFast(false);
                t.convertColumn("Text", 2, JdbcType.INTEGER);
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
                assertEquals(errors.getErrors().size(), 3);
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
