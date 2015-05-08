/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.ScrollableDataIterator;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.Selector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.TestSchema;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * User: matthewb
 * Date: May 16, 2011
 * Time: 2:03:45 PM
 *
 * SimpleTranslator starts with no output columns (except row number), you must call add() method to add columns.
 *
 * Note that get(n) may be called more than once for any column.  To simplify the implementation to avoid
 * duplicate errors and make AutoIncrement/Guid easier, we just copy the column data on next().
 *
 * NOTE: this also has the effect that it is possible for columns to 'depend' on columns that precede them
 * in the column list.  This can save some extra nesting of iterators, but it can look confusing so use comments.
 *
 *   e.g. Column [2] can use the result calculated by column [1] by calling SimpleTranslator.this.get(1)
 *
 * see AliasColumn for an example
 */
public class SimpleTranslator extends AbstractDataIterator implements DataIterator, ScrollableDataIterator
{
    DataIterator _data;
    protected final ArrayList<Pair<ColumnInfo, Callable>> _outputColumns = new ArrayList<Pair<ColumnInfo, Callable>>()
    {
        @Override
        public boolean add(Pair<ColumnInfo, Callable> columnInfoCallablePair)
        {
            assert null == _row;
            return super.add(columnInfoCallablePair);
        }
    };
    Object[] _row = null;
    Container _mvContainer;
    Map<String,String> _missingValues = Collections.emptyMap();
    Map<String,Integer> _inputNameMap = null;

    public SimpleTranslator(DataIterator source, DataIteratorContext context)
    {
        super(context);
        _data = source;
        _outputColumns.add(new Pair<ColumnInfo, Callable>(new ColumnInfo(source.getColumnInfo(0)), new PassthroughColumn(0)));
    }

    protected DataIterator getInput()
    {
        return _data;
    }

    public void setInput(DataIterator it)
    {
        _data = it;
    }

    public void setMvContainer(Container c)
    {
        _mvContainer = c;
        if (null == c)
            _missingValues = new HashMap<>();
        else
            _missingValues = MvUtil.getIndicatorsAndLabels(c);
    }

    protected boolean validMissingValue(String mv)
    {
        return _missingValues.containsKey(mv);
    }

    protected Object addConversionException(String fieldName, @Nullable Object value, @Nullable JdbcType target, Exception x)
    {
        String msg;
        if (null != value && null != target)
        {
            String fromType = (value instanceof String) ? "" : "(" + (value.getClass().getSimpleName() + ") ");
            msg = "Could not convert " + fromType + "'" + value + "' for field " + fieldName + ", should be of type " + target.getJavaClass().getSimpleName();
        }
        else if (null != x)
            msg = StringUtils.defaultString(x.getMessage(), x.toString());
        else
            msg = "Could not convert value";
        addFieldError(fieldName, msg);
        return null;
    }


    protected class PassthroughColumn implements Callable
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


    protected class AliasColumn extends SimpleConvertColumn
    {
        AliasColumn(String fieldName, int index)
        {
            super(fieldName, index, null);
        }

        AliasColumn(String fieldName, int index, JdbcType convert)
        {
            super(fieldName, index, convert);
        }

        @Override
        protected Object getSourceValue()
        {
            return SimpleTranslator.this.get(index);
        }
    }


    /** coalease, return the first column if non-null, else the second column */
    protected class CoalesceColumn implements Callable
    {
        final Callable _first;
        final Callable _second;

        CoalesceColumn(int first, Callable second)
        {
            _first = new PassthroughColumn(first);
            _second = second;
        }

        CoalesceColumn(Callable first, Callable second)
        {
            _first = first;
            _second = second;
        }

        @Override
        public Object call() throws Exception
        {
            Object v = _first.call();
            if (v instanceof String)
                v = StringUtils.isEmpty((String)v) ? null : v;
            if (null != v)
                return v;
            return _second.call();
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
            Object value = getSourceValue();
            try
            {
                return convert(value);
            }
            catch (ConversionException x)
            {
                return addConversionException(fieldName, value, type, x);
            }
        }

        protected Object convert(Object o)
        {
            return null==type ? o : type.convert(o);
        }

        protected Object getSourceValue()
        {
            return _data.get(index);
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


    public static class ConstantColumn implements Callable
    {
        final Object k;

        public ConstantColumn(Object k)
        {
            this.k = k;
        }

        @Override
        public Object call() throws Exception
        {
            return k;
        }
    }


    public static class AutoIncrementColumn implements Callable
    {
        private int _autoIncrement = -1;

        protected int getFirstValue()
        {
            return 1;
        }

        @Override
        public Object call() throws Exception
        {
            if (_autoIncrement == -1)
                _autoIncrement = getFirstValue();
            return _autoIncrement++;
        }
    }


    private class MissingValueConvertColumn extends SimpleConvertColumn
    {
        boolean supportsMissingValue = true;
        int indicator;

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
        public Object convert(Object value)
        {
            if (value instanceof MvFieldWrapper)
                return value;

            Object mv = 0==indicator ? null : _data.get(indicator);

            if (value instanceof String && StringUtils.isEmpty((String)value))
                value = null;
            if (null != mv && !(mv instanceof String))
                mv = String.valueOf(mv);
            if (StringUtils.isEmpty((String)mv))
                mv = null;

            if (supportsMissingValue && null == mv && null != value)
            {
                String s = value.toString();
                if (validMissingValue(s))
                {
                    mv = s;
                    value = null;
                }
            }

            if (null != value)
                value = innerConvert(value);
            
            if (supportsMissingValue && null != mv)
            {
                if (!validMissingValue((String)mv))
                {
                    getRowError().addFieldError(_data.getColumnInfo(index).getName(),"Value is not a valid missing value indicator: " + mv.toString());
                    return null;
                }

                return new MvFieldWrapper(MvUtil.getMvIndicators(_mvContainer), value, String.valueOf(mv));
            }

            return value;
        }

        Object innerConvert(Object value)
        {
            return type.convert(value);
        }
    }


    private class PropertyConvertColumn extends MissingValueConvertColumn
    {
        PropertyDescriptor pd;
        PropertyType pt;

        PropertyConvertColumn(String fieldName, int fromIndex, int mvIndex, PropertyDescriptor pd, PropertyType pt)
        {
            super(fieldName, fromIndex, mvIndex, pt.getJdbcType());
            this.pd = pd;
            this.pt = pt;
            this.supportsMissingValue = pd.isMvEnabled();
        }

        @Override
        Object innerConvert(Object value)
        {
            if (null != pt)
                value = pt.convert(value);
            return value;
        }
    }

    private class PropertyConvertAndTrimColumn extends PropertyConvertColumn
    {
        boolean trimRightOnly;

        PropertyConvertAndTrimColumn(String fieldName, int fromIndex, int mvIndex, PropertyDescriptor pd, PropertyType pt, boolean trimRightOnly)
        {
            super(fieldName, fromIndex, mvIndex, pd, pt);
            this.trimRightOnly = trimRightOnly;
        }

        @Override
        Object innerConvert(Object value)
        {
            value = super.innerConvert(value);
            if (null != value && value instanceof String)
            {
                if (trimRightOnly)
                    value = StringUtils.stripEnd((String) value, "\t\r\n ");
                else
                    value = StringUtils.trim((String) value);
            }
            return value;
        }
    }

    // CONSIDER: Add JdbcType or PropertyType for array types instead of handling conversion here.
    private class MultiValueConvertColumn extends SimpleConvertColumn
    {
        private final SimpleConvertColumn _c;

        MultiValueConvertColumn(SimpleConvertColumn c)
        {
            super(c.fieldName, c.index, c.type);
            _c = c;
        }

        @Override
        protected Object convert(Object o)
        {
            Collection<Object> values = new ArrayList<>();
            if (o instanceof Object[])
            {
                for (Object o1 : (Object[])o)
                    values.add(_c.convert(o1));
            }
            else if (o instanceof Collection)
            {
                for (Object o1 : (Collection)o)
                    values.add(_c.convert(o1));
            }
            else if (o instanceof JSONArray)
            {
                // Only supports array of simple values right now.
                for (Object o1 : ((JSONArray)o).toArray())
                    values.add(_c.convert(o1));
            }
            else if (o != null)
            {
                values.add(_c.convert(o));
            }

            return values;
        }

    }

    protected class RemapColumn implements Callable
    {
        //final int _index;
        final Callable _inputColumn;
        final Map<?, ?> _map;
        final boolean _strict;

        // strict == true means every incoming value must have an entry in the map
        // string == false means incoming values without a map entry will pass through
        public RemapColumn(final int index, Map<?, ?> map, boolean strict)
        {
            _inputColumn = new Callable(){
                @Override
                public Object call() throws Exception
                {
                    return _data.get(index);
                }
            };
            _map = map;
            _strict = strict;
        }

        public RemapColumn(Callable call, Map<?, ?> map, boolean strict)
        {
            _inputColumn = call;
            _map = map;
            _strict = strict;
        }

        @Override
        public Object call() throws Exception
        {
            Object k = _inputColumn.call();
            if (null == k)
                return null;
            Object v = _map.get(k);
            if (null != v || _map.containsKey(k))
                return v;
            if (!_strict)
                return k;
            throw new ConversionException("Could not translate value: " + String.valueOf(k));
        }
    }
    

    protected class NullColumn implements Callable
    {
        @Override
        public Object call() throws Exception
        {
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
                _ts =  new NowTimestamp(System.currentTimeMillis());
            return _ts;
        }
    }


    private class SharedTableLookupColumn implements Callable
    {
        final int _first;
        final Integer _second;
        Map<String, Integer> _lookupStringToRowIdMap;
        Map<Object, Object> _dataspaceTableIdMap;

        SharedTableLookupColumn(int first, Integer second, Map<String, Integer> lookupStringToRowIdMap,
                                @NotNull Map<Object, Object> dataspaceTableIdMap)
        {
            _first = first;
            _second = second;
            _lookupStringToRowIdMap = lookupStringToRowIdMap;
            _dataspaceTableIdMap = dataspaceTableIdMap;
        }

        @Override
        public Object call() throws Exception
        {
            Object value = _data.get(_first);

            // shared tables should be Integer->Integer
            Integer valueAsInt = null;
            if (value instanceof String)
                valueAsInt = Integer.parseInt((String)value);
            if (_dataspaceTableIdMap.containsKey(valueAsInt))
            {
                value = _dataspaceTableIdMap.get(valueAsInt);
            }
            else if (null != _second && !_lookupStringToRowIdMap.isEmpty())
            {
                String lookupString = (String)_data.get(_second);
                Integer mappedValue = _lookupStringToRowIdMap.get(lookupString);
                if (null != mappedValue)
                    value = mappedValue;
            }
            return value;
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
            addColumn(i);
    }


    public void removeColumn(int index)
    {
        _outputColumns.remove(index);
    }


    public int addColumn(ColumnInfo col, Callable call)
    {
        _outputColumns.add(new Pair<>(col, call));
        return _outputColumns.size()-1;
    }

    public int addColumn(int fromIndex)
    {
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        return addColumn(col, new PassthroughColumn(fromIndex));
    }

    public int addColumn(ColumnInfo from, int fromIndex)
    {
        ColumnInfo clone = new ColumnInfo(from);
        return addColumn(clone, new PassthroughColumn(fromIndex));
    }

    public int addColumn(String name, int fromIndex)
    {
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        return addColumn(col, new PassthroughColumn(fromIndex));
    }

    public int addConvertColumn(ColumnInfo col, int fromIndex, boolean mv)
    {
        SimpleConvertColumn c;
        if (mv)
            c = new MissingValueConvertColumn(col.getName(), fromIndex, col.getJdbcType());
        else
            c = new SimpleConvertColumn(col.getName(), fromIndex, col.getJdbcType());

        boolean multiValue = col.getFk() instanceof MultiValuedForeignKey;
        if (multiValue)
        {
            // convert input into Collection of jdbcType values
            c = new MultiValueConvertColumn(c);
        }

        return addColumn(col, c);
    }

    public int addConvertColumn(ColumnInfo col, int fromIndex, int mvIndex, boolean mv)
    {

        SimpleConvertColumn c;
        if (mv)
            c = new MissingValueConvertColumn(col.getName(), fromIndex, mvIndex, col.getJdbcType());
        else
            c = new SimpleConvertColumn(col.getName(), fromIndex, col.getJdbcType());

        boolean multiValue = col.getFk() instanceof MultiValuedForeignKey;
        if (multiValue)
        {
            // convert input into Collection of jdbcType values
            c = new MultiValueConvertColumn(c);
        }

        return addColumn(col, c);
    }

    public int addAliasColumn(String name, int aliasIndex)
    {
        ColumnInfo col = new ColumnInfo(_outputColumns.get(aliasIndex).getKey());
        col.setName(name);
        // don't want duplicate property ids usually
        col.setPropertyURI(null);
        return addColumn(col, new AliasColumn(name, aliasIndex));
    }

    public int addAliasColumn(String name, int aliasIndex, JdbcType toType)
    {
        ColumnInfo col = new ColumnInfo(_outputColumns.get(aliasIndex).getKey());
        col.setName(name);
        col.setJdbcType(toType);
        // don't want duplicate property ids usually
        col.setPropertyURI(null);
        return addColumn(col, new AliasColumn(name, aliasIndex, toType));
    }

    public int addConvertColumn(String name, int fromIndex, JdbcType toType, boolean mv)
    {
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        col.setJdbcType(toType);
        return addConvertColumn(col, fromIndex, mv);
    }

    public int addConvertColumn(String name, int fromIndex, int mvIndex, PropertyDescriptor pd, PropertyType pt)
    {
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        col.setJdbcType(pt.getJdbcType());

        boolean trimString = false;
        boolean trimStringRight = false;
        if (null != _context.getConfigParameters())
        {
            trimString = _context.getConfigParameters().get(QueryUpdateService.ConfigParameters.TrimString) == Boolean.TRUE;
            trimStringRight = _context.getConfigParameters().get(QueryUpdateService.ConfigParameters.TrimStringRight) == Boolean.TRUE;
        }
        if (PropertyType.STRING == pt && (trimString || trimStringRight))
            return addColumn(col, new PropertyConvertAndTrimColumn(name, fromIndex, mvIndex, pd, pt, !trimString));
        else
            return addColumn(col, new PropertyConvertColumn(name, fromIndex, mvIndex, pd, pt));
    }


    public int addConvertColumn(String name, int fromIndex, PropertyDescriptor pd, PropertyType pt)
    {
        return addConvertColumn(name, fromIndex, 0, pd, pt);
    }


    public int addCoaleseColumn(String name, int fromIndex, Callable second)
    {
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        col.setName(name);
        return addColumn(col, new CoalesceColumn(fromIndex, second));
    }


    public int addNullColumn(String name, JdbcType type)
    {
        ColumnInfo col = new ColumnInfo(name, type);
        return addColumn(col, new NullColumn());
    }


    public int addConstantColumn(String name, JdbcType type, Object val)
    {
        ColumnInfo col = new ColumnInfo(name, type);
        return addColumn(col, new ConstantColumn(val));
    }


    public int addTimestampColumn(String name)
    {
        ColumnInfo col = new ColumnInfo(name, JdbcType.TIMESTAMP);
        return addColumn(col, new TimestampColumn());
    }


    public int addSharedTableLookupColumn(int fromIndex, @Nullable FieldKey extraColumnFieldKey, @Nullable ForeignKey fk,
                                          @NotNull Map<Object, Object> dataspaceTableIdMap)
    {
        Integer extraColumnIndex = null;
        final Map<String, Integer> lookupStringToRowIdMap = new HashMap<>();
        if (null != extraColumnFieldKey)
        {
            assert (null != fk);
            String columnHeaderName = extraColumnFieldKey.toDisplayString();
            extraColumnIndex = getColumnNameMap().get(columnHeaderName);
            TableInfo tableInfo = fk.getLookupTableInfo();
            if (null != tableInfo)
            {
                Set<String> columnNames = new HashSet<>();
                final String lookupColumnName = extraColumnFieldKey.getName();
                final String lookupTablePkColumnName = tableInfo.getPkColumns().get(0).getName();     // Expect only 1
                columnNames.add(lookupColumnName);
                columnNames.add(lookupTablePkColumnName);
                new TableSelector(tableInfo, columnNames).forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
                {
                    @Override
                    public void exec(Map<String, Object> row) throws SQLException
                    {
                        Integer rowId = (Integer)row.get(lookupTablePkColumnName);
                        String name = (String)row.get(lookupColumnName);
                        lookupStringToRowIdMap.put(name, rowId);
                    }
                });
            }
        }
        ColumnInfo col = new ColumnInfo(_data.getColumnInfo(fromIndex));
        return addColumn(col, new SharedTableLookupColumn(fromIndex, extraColumnIndex, lookupStringToRowIdMap, dataspaceTableIdMap));
    }

    public static DataIterator wrapBuiltInColumns(DataIterator in , DataIteratorContext context, @Nullable Container c, @NotNull User user, @NotNull TableInfo target)
    {
        SimpleTranslator t;
        if (in instanceof SimpleTranslator)
            t = (SimpleTranslator)in;
        else
        {
            t = new SimpleTranslator(in, context);
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
        Container(When.insert, JdbcType.GUID),
//        Owner(When.insert, JdbcType.INTEGER),
        CreatedBy(When.insert, JdbcType.INTEGER),
        Created(When.insert, JdbcType.TIMESTAMP),
        ModifiedBy(When.both, JdbcType.INTEGER),
        Modified(When.both, JdbcType.TIMESTAMP),
        EntityId(When.insert, JdbcType.GUID);

        final When when;
        final JdbcType type;

        SpecialColumn(When when, JdbcType type)
        {
            this.when = when;
            this.type = type;
        }
    }


    /**
     * Provide values for common built-in columns.  Usually we do not allow the user to specify values for these columns,
     * so matching columns in the input are ignored.
     * @param allowPassThrough indicates that columns in the input iterator should not be ignored
     */
    public void addBuiltInColumns(@Nullable Container c, @NotNull User user, @NotNull TableInfo target, boolean allowPassThrough)
    {
        final String containerId = null == c ? null : c.getId();
        final Integer userId = null == user ? 0 : user.getUserId();

        Callable containerCallable = new ConstantColumn(containerId);
        Callable userCallable = new ConstantColumn(userId);
        Callable tsCallable = new TimestampColumn();
        Callable guidCallable = new Callable(){public Object call() {return GUID.makeGUID();}};

        Map<String, Integer> inputCols = getColumnNameMap();
        Map<String, Integer> outputCols = new CaseInsensitiveHashMap<>();
        for (int i=1 ; i<_outputColumns.size() ; i++)
            outputCols.put(_outputColumns.get(i).getKey().getName(), i);

        addBuiltinColumn(SpecialColumn.Container,  false, target, inputCols, outputCols, containerCallable);
//        addBuiltinColumn(SpecialColumn.Owner,      allowPassThrough, target, inputCols, outputCols, userCallable);
        addBuiltinColumn(SpecialColumn.CreatedBy,  allowPassThrough, target, inputCols, outputCols, userCallable);
        addBuiltinColumn(SpecialColumn.ModifiedBy, allowPassThrough, target, inputCols, outputCols, userCallable);
        addBuiltinColumn(SpecialColumn.Created,    allowPassThrough, target, inputCols, outputCols, tsCallable);
        addBuiltinColumn(SpecialColumn.Modified,   allowPassThrough, target, inputCols, outputCols, tsCallable);
        addBuiltinColumn(SpecialColumn.EntityId,   allowPassThrough, target, inputCols, outputCols, guidCallable);
    }


    private int addBuiltinColumn(SpecialColumn e, boolean allowPassThrough, TableInfo target, Map<String,Integer> inputCols, Map<String,Integer> outputCols, Callable c)
    {
        String name = e.name();
        ColumnInfo col = target.getColumn(name);
        if (null==col)
            return 0;
        if (col.getJdbcType() != e.type && col.getJdbcType().getJavaClass() != e.type.getJavaClass())
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
                _outputColumns.add(new Pair<>(new ColumnInfo(name, col.getJdbcType()), c));
                return _outputColumns.size()-1;
            }
        }
        // selected already
        else
        {
            if (!allowPassThrough)
                _outputColumns.set(indexOut, new Pair<>(new ColumnInfo(name, col.getJdbcType()), c));
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

        boolean hasNext = _data.next();
        if (!hasNext)
            return false;

        if (null == _row)
            _row = new Object[_outputColumns.size()];
        assert _row.length == _outputColumns.size();
        processNextInput();

        for (int i=0 ; i<_row.length ; ++i)
        {
            _row[i] = null;
            try
            {
                _row[i] = _outputColumns.get(i).getValue().call();
            }
            catch (ConversionException x)
            {
                // preferable to handle in call()
                _row[i] = addConversionException(_outputColumns.get(i).getKey().getName(), null, null, x);
            }
            catch (RuntimeException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                // undone source field name???
                addFieldError(_outputColumns.get(i).getKey().getName(), x.getMessage());
            }
        }
        checkShouldCancel();
        return true;
    }

    /**
     * Allow sublcasses to process the input data before the output column are called.
     */
    protected void processNextInput()
    {
    }

    @Override
    public Object get(int i)
    {
        return _row[i];
    }


    // use carefully!  Mostly for implementing classes used for addColumn(Callable)
    public Object getInputColumnValue(int i)
    {
        return _data.get(i);
    }


    @Override
    public void beforeFirst()
    {
        _row = null;
        ((ScrollableDataIterator)_data).beforeFirst();
    }


    @Override
    public boolean isScrollable()
    {
        return _data instanceof ScrollableDataIterator && ((ScrollableDataIterator)_data).isScrollable();
    }


    @Override
    public boolean isConstant(int i)
    {
        Callable c = _outputColumns.get(i).getValue();
        if (c instanceof ConstantColumn)
            return true;
        if (c instanceof PassthroughColumn)
            return _data.isConstant(((PassthroughColumn)c).index);
        if (c instanceof AliasColumn)
            return isConstant(((AliasColumn)c).index);
        if (c instanceof SimpleConvertColumn)
            return _data.isConstant(((SimpleConvertColumn)c).index);
        if (c instanceof TimestampColumn)
            return true;
        return false;
    }


    @Override
    public Object getConstantValue(int i)
    {
        Callable c = _outputColumns.get(i).getValue();
        if (c instanceof ConstantColumn)
            return ((ConstantColumn)c).k;
        if (c instanceof PassthroughColumn)
            return _data.getConstantValue(((PassthroughColumn)c).index);
        if (c instanceof AliasColumn)
            return getConstantValue(((AliasColumn)c).index);
        if (c instanceof SimpleConvertColumn)
        {
            SimpleConvertColumn scc = (SimpleConvertColumn)c;
            return scc.convert(_data.getConstantValue(scc.index));
        }
        if (c instanceof TimestampColumn)
            return new NowTimestamp(System.currentTimeMillis());
        throw new IllegalStateException("shouldn't call this method unless isConstant()==true");
    }


    @Override
    public void close() throws IOException
    {
        _data.close();
    }


    // this is a marker interface to hint that this value may be replaced by {ts now()}
    public static class NowTimestamp extends java.sql.Timestamp
    {
        NowTimestamp(long ms)
        {
            super(ms);
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
        StringTestIterator simpleData = new StringTestIterator
        (
            Arrays.asList("IntNotNull", "Text", "EntityId", "Int"),
            Arrays.asList(
                as("1", "one", GUID.makeGUID(), ""),
                as("2", "two", GUID.makeGUID(), "/N"),
                as("3", "three", GUID.makeGUID(), "3"),
                as("4", "four", "", "4")
            )
        );

        public TranslateTestCase()
        {
            simpleData.setScrollable(true);
        }


        @Test
        public void passthroughTest() throws Exception
        {
            DataIteratorContext context = new DataIteratorContext();
            simpleData.beforeFirst();
            SimpleTranslator t = new SimpleTranslator(simpleData, context);
            t.selectAll();
            assert(t.getColumnCount() == simpleData.getColumnCount());
            assertTrue(t.getColumnInfo(0).getJdbcType() == JdbcType.INTEGER);
            for (int i=1 ; i<=t.getColumnCount() ; i++)
                assertTrue(t.getColumnInfo(i).getJdbcType() == JdbcType.VARCHAR);
            for (int i=1 ; i<=4 ; i++)
            {
                assertTrue(t.next());
                assertEquals(t.get(0), i);
                assertEquals(t.get(1), String.valueOf(i));
            }
            assertFalse(t.next());
        }


        @Test
        public void testCoalesce() throws Exception
        {
            DataIteratorContext context = new DataIteratorContext();
            simpleData.beforeFirst();
            SimpleTranslator t = new SimpleTranslator(simpleData, context);
            int c = t.addCoaleseColumn("IntNotNull", 3, new GuidColumn());
            for (int i=1 ; i<=4 ; i++)
            {
                assertTrue(t.next());
                String guid = (String)t.get(c);
                assertFalse(StringUtils.isEmpty(guid));
            }
        }


        @Test
        public void convertTest() throws Exception
        {
            // w/o errors
            {
                DataIteratorContext context = new DataIteratorContext();
                simpleData.beforeFirst();
                SimpleTranslator t = new SimpleTranslator(simpleData, context);
                t.addConvertColumn("IntNotNull", 1, JdbcType.INTEGER, false);
                assertEquals(1, t.getColumnCount());
                assertEquals(JdbcType.INTEGER, t.getColumnInfo(0).getJdbcType());
                assertEquals(JdbcType.INTEGER, t.getColumnInfo(1).getJdbcType());
                for (int i=1 ; i<=4 ; i++)
                {
                    assertTrue(t.next());
                    assertEquals(i, t.get(0));
                    assertEquals(i, t.get(1));
                }
                assertFalse(t.next());
            }

            // w/ errors failfast==true
            {
                DataIteratorContext context = new DataIteratorContext();
                context.setFailFast(true);
                context.setVerbose(true);
                simpleData.beforeFirst();
                SimpleTranslator t = new SimpleTranslator(simpleData, context);
                t.addConvertColumn("Text", 2, JdbcType.INTEGER, false);
                assertEquals(t.getColumnCount(), 1);
                assertEquals(t.getColumnInfo(0).getJdbcType(), JdbcType.INTEGER);
                assertEquals(t.getColumnInfo(1).getJdbcType(), JdbcType.INTEGER);
                try
                {
                    assertFalse(t.next());
                }
                catch (BatchValidationException x)
                {
                }
                assertTrue(context._errors.hasErrors());
            }

            // w/ errors failfast==false
            {
                DataIteratorContext context = new DataIteratorContext();
                context.setFailFast(false);
                context.setVerbose(true);
                simpleData.beforeFirst();
                SimpleTranslator t = new SimpleTranslator(simpleData, context);
                t.addConvertColumn("Text", 2, JdbcType.INTEGER, false);
                assertEquals(t.getColumnCount(), 1);
                assertEquals(t.getColumnInfo(0).getJdbcType(), JdbcType.INTEGER);
                assertEquals(t.getColumnInfo(1).getJdbcType(), JdbcType.INTEGER);
                for (int i=1 ; i<=4 ; i++)
                {
                    assertTrue(t.next());
                    assertEquals(i, t.get(0));
                    assertNull(t.get(1));
                    assertTrue(context.getErrors().hasErrors());
                    assertEquals(i, context.getErrors().getRowErrors().size());
                }
                assertFalse(t.next());
                assertEquals(4, context.getErrors().getRowErrors().size());
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
            TableInfo t = TestSchema.getInstance().getTableInfoTestTable();
        }
    }
}
