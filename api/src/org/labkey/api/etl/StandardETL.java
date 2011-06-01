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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper for code that does not use QueryUpdateService
 *
 *      -- convert basic types
 *      -- handle missing values for property columns
 *      -- required and property validation
 *      -- built-in columns
 *
 *  TODO
 *      -- handle missing values for non-property columns
 */


public class StandardETL implements DataIteratorBuilder, Runnable
{
    final DataIteratorBuilder _inputBuilder;
    final TableInfo _target;
    BatchValidationException _errors;
    final Container _c;
    final User _user;
    boolean _failFast = true;
    int _rowCount = 0;

    ValidatorIterator _it;

    public StandardETL(TableInfo target, @NotNull DataIteratorBuilder in, @Nullable Container c, @NotNull User user)
    {
        if (!(target instanceof UpdateableTableInfo))
            throw new IllegalArgumentException("Must implement UpdateableTableInfo");
        _inputBuilder = in;
        _target = target;
        _c = c;
        _user = user;
    }


    public StandardETL(TableInfo target, @NotNull DataIterator it, @Nullable Container c, @NotNull User user)
    {
        if (!(target instanceof UpdateableTableInfo))
            throw new IllegalArgumentException("Must implement UpdateableTableInfo");
        _inputBuilder = new DataIteratorBuilder.Wrapper(it);
        _target = target;
        _c = c;
        _user = user;
    }


    @Override
    public void run()
    {
        if (null == _errors)
            _errors = new BatchValidationException();
        DataIterator data = getDataIterator(_errors);
        _rowCount = ((UpdateableTableInfo)_target).persistRows(data, _errors);
    }


    public int getRowCount()
    {
        return _rowCount;
    }


    public BatchValidationException getErrors()
    {
        return _errors;
    }


    @Override
    public DataIterator getDataIterator(BatchValidationException errors)
    {
        if (null != _it)
            return _it;

        DbScope scope = _target.getSchema().getScope();
        Domain d = _target.getDomain();

        assert scope.isTransactionActive();

        Map<String, DomainProperty> propertiesMap = new HashMap<String, DomainProperty>();
        if (null != d)
        {
            for (DomainProperty dp : d.getProperties())
                propertiesMap.put(dp.getPropertyURI(), dp);
        }

        DataIterator input = _inputBuilder.getDataIterator(errors);

        //
        // pass through all the source columns
        // associate each with a target column if possible and handle convert, validate
        //
        // NOTE: although some columns may be matched by propertyURI, I assumie that create/modified etc are bound by name
        //

        input = SimpleTranslator.wrapBuiltInColumns(input, errors, _c, _user, _target);

        /*
         * NOTE: sbouldn't really need DomainProperty here,
         * but not all information is available on the ColumnInfo
         * notably we need PropertyValidators
         * notably we need PropertyValidators
         */
        List<ColumnInfo> cols = _target.getColumns();
        Map<String, Pair<ColumnInfo,DomainProperty>> targetMap = new CaseInsensitiveHashMap<Pair<ColumnInfo,DomainProperty>>(cols.size()*4);
        for (ColumnInfo col : cols)
        {
            if (col.isMvIndicatorColumn() || col.isRawValueColumn()) //TODO col.isNotUpdatableForSomeReasonSoContinue()
                continue;
            DomainProperty dp = propertiesMap.get(col.getPropertyURI());
            Pair<ColumnInfo,DomainProperty> p = new Pair<ColumnInfo,DomainProperty>(col,dp);
            String name = col.getName();
            targetMap.put(name, p);
            String uri = col.getPropertyURI();
            if (null != uri && !targetMap.containsKey(uri))
                targetMap.put(uri, p);
            for (String alias : col.getImportAliasSet())
                if (!targetMap.containsKey(alias))
                    targetMap.put(alias, p);
        }

        //
        // match up the columns, validate that there is no more than one source column that matches the target column
        //
        ValidationException setupError = new ValidationException();
        IdentityHashMap<Pair,Object> used = new IdentityHashMap();

        ArrayList<Pair<ColumnInfo,DomainProperty>> targetCols = new ArrayList<Pair<ColumnInfo, DomainProperty>>(input.getColumnCount()+1);
        targetCols.add(new Pair(null,null));
        for (int i=1 ; i<=input.getColumnCount() ; i++)
        {
            ColumnInfo from = input.getColumnInfo(i);
            Pair<ColumnInfo,DomainProperty> to = null;
            if (null == to && null != from.getPropertyURI())
                to = targetMap.get(from.getPropertyURI());
            if (null == to)
                to = targetMap.get(from.getName());

            if (null != to)
            {
                if (used.containsKey(to))
                    setupError.addGlobalError("Two columns mapped to target column: " + to.getKey().getName());
                used.put(to,null);
                targetCols.add(to);
            }
            else
                targetCols.add(new Pair(null,null));
        }


        // TODO : required columns that were not found

        if (setupError.hasErrors())
            errors.addRowError(setupError);

        //
        //  CONVERT and VALIDATE iterators
        //
        // set up a SimpleTranslator for conversion and missing-value handling
        //

        SimpleTranslator convert = new SimpleTranslator(input, errors);
        convert.setFailFast(_failFast);
        convert.setMvContainer(_c);
        ValidatorIterator validate = new ValidatorIterator(convert, errors, _c, _user);

        for (int i=1 ; i<= input.getColumnCount() ; i++)
        {
            Pair<ColumnInfo, DomainProperty> pair = targetCols.get(i);
            ColumnInfo col = pair.getKey();
            DomainProperty dp = pair.getValue();
            if (null == col)
            {
                convert.addColumn(input.getColumnInfo(i).getName(), i);
                continue;
            }
            boolean supportsMV = null != col.getMvColumnName() || (null != dp && dp.isMvEnabled());
            if (null == dp)
                convert.addConvertColumn(col, i, supportsMV);
            else
                convert.addConvertColumn(col.getName(), i, dp.getPropertyDescriptor(), dp.getPropertyDescriptor().getPropertyType());

            if (!col.isNullable())
                validate.addRequired(i, false);
            else if (null != dp && dp.isRequired())
                validate.addRequired(i, true);

            if (null != dp)
                validate.addPropertyValidator(i, dp.getPropertyDescriptor());
        }

        return LoggingDataIterator.wrap(validate);
    }
}
