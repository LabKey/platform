/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

package org.labkey.api.dataiterator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.HashMap;
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


public class StandardDataIteratorBuilder implements DataIteratorBuilder
{
    final DataIteratorBuilder _inputBuilder;
    final TableInfo _target;
    boolean _useImportAliases = false;
    DataIteratorContext _context;
    final Container _c;
    final User _user;

    ValidatorIterator _it;


    public static StandardDataIteratorBuilder forInsert(TableInfo target, @NotNull DataIteratorBuilder in, @Nullable Container c, @NotNull User user, DataIteratorContext context)
    {
        return new StandardDataIteratorBuilder(target, in, c, user, context);
    }


    public static StandardDataIteratorBuilder forUpdate(TableInfo target, @NotNull DataIteratorBuilder in, @Nullable Container c, @NotNull User user, DataIteratorContext context)
    {
        throw new UnsupportedOperationException();
    }


    protected StandardDataIteratorBuilder(TableInfo target, @NotNull DataIteratorBuilder in, @Nullable Container c, @NotNull User user, DataIteratorContext context)
    {
        if (!(target instanceof UpdateableTableInfo))
            throw new IllegalArgumentException("Must implement UpdateableTableInfo: " + target.getName() + " (" + target.getClass().getSimpleName() + ")");
        _inputBuilder = in;
        _target = target;
        _c = c;
        _user = user;
        _context = context;
        _useImportAliases = context.getInsertOption().useImportAliases;
    }


    public BatchValidationException getErrors()
    {
        return _context.getErrors();
    }

    private static class TranslateHelper
    {
        TranslateHelper(ColumnInfo col, DomainProperty dp)
        {
            this.target = col;
            this.dp = dp;
        }
        int indexFrom = 0;
        int indexMv = 0;
        ColumnInfo target=null;
        DomainProperty dp=null;
    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        if (null != _it)
            return _it;

        Domain d = _target.getDomain();

        Map<String, DomainProperty> propertiesMap = new HashMap<>();
        if (null != d)
        {
            for (DomainProperty dp : d.getProperties())
                propertiesMap.put(dp.getPropertyURI(), dp);
        }

        DataIterator input = _inputBuilder.getDataIterator(context);

        if (null == input)
        {
            if (context.getErrors().hasErrors())
                return null;
            // going to crash anyway, might as well do it now
            throw new NullPointerException("null dataiterator returned");
        }

        //
        // pass through all the source columns
        // associate each with a target column if possible and handle convert, validate
        //
        // NOTE: although some columns may be matched by propertyURI, I assume that create/modified etc are bound by name
        //

        input = SimpleTranslator.wrapBuiltInColumns(input, context, _c, _user, _target);

        Map<String,Integer> sourceColumnsMap = DataIteratorUtil.createColumnAndPropertyMap(input);


        /*
         * NOTE: shouldn't really need DomainProperty here,
         * but not all information is available on the ColumnInfo
         * notably we need PropertyValidators
         *
         * Anyway match up the columns and property descriptors and keep them in a set of TranslateHeleprs
         */
        List<ColumnInfo> cols = _target.getColumns();
        Map<FieldKey, TranslateHelper> unusedCols = new HashMap<>(cols.size() * 2);
        Map<String, TranslateHelper> translateHelperMap = new CaseInsensitiveHashMap<>(cols.size()*4);
        for (ColumnInfo col : cols)
        {
            if (col.isMvIndicatorColumn() || col.isRawValueColumn())
                continue;
            DomainProperty dp = propertiesMap.get(col.getPropertyURI());
            TranslateHelper p = new TranslateHelper(col,dp);
            String uri = col.getPropertyURI();
            if (null != uri)
                 translateHelperMap.put(getTranslateHelperKey(col), p);
            unusedCols.put(col.getFieldKey(), p);
        }


        //
        // match up the columns, validate that there is no more than one source column that matches the target column
        //
        ValidationException setupError = new ValidationException();
        ArrayList<ColumnInfo> matches = DataIteratorUtil.matchColumns(input, _target, _useImportAliases, setupError);

        ArrayList<TranslateHelper> targetCols = new ArrayList<>(input.getColumnCount()+1);
        for (int i=1 ; i<=input.getColumnCount() ; i++)
        {
            ColumnInfo targetCol = matches.get(i);
            TranslateHelper to = null;
            if (null != targetCol)
                to = translateHelperMap.get(getTranslateHelperKey(targetCol));

            if (null != to)
            {
                if (!unusedCols.containsKey(to.target.getFieldKey()))
                    setupError.addGlobalError("Two columns mapped to target column: " + to.target.getName());
                unusedCols.remove(to.target.getFieldKey());
                to.indexFrom = i;
                Integer indexMv = null==to.target.getMvColumnName() ? null : sourceColumnsMap.get(to.target.getMvColumnName().getName());
                to.indexMv = null==indexMv ? 0 : indexMv.intValue();
                targetCols.add(to);
            }
            else
            {
                // pass through unrecognized columns (may be internal column like "_key")
                to = new TranslateHelper(null, null);
                to.indexFrom = i;
                targetCols.add(to);
            }
        }

        //
        // check for unbound columns that are required
        //
        for (TranslateHelper pair : unusedCols.values())
        {
            if (isRequiredForInsert(pair.target, pair.dp))
                setupError.addGlobalError("Data does not contain required field: " + pair.target.getName());
        }


        //
        //  CONVERT and VALIDATE iterators
        //
        // set up a SimpleTranslator for conversion and missing-value handling
        //

        SimpleTranslator convert = new SimpleTranslator(input, context);
        convert.setDebugName("StandardDIB convert");
        convert.setMvContainer(_c);
        ValidatorIterator validate = new ValidatorIterator(LoggingDataIterator.wrap(convert), context, _c, _user);
        validate.setDebugName("StandardDIB validate");

        for (TranslateHelper pair : targetCols)
        {
            boolean isAttachment = (null != pair.dp && pair.dp.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT);
            boolean supportsMV = (null != pair.target && null != pair.target.getMvColumnName()) || (null != pair.dp && pair.dp.isMvEnabled());
            int indexConvert;

            if (null == pair.target || isAttachment)
                indexConvert = convert.addColumn(pair.indexFrom);
            else if (null == pair.dp)
                indexConvert = convert.addConvertColumn(pair.target, pair.indexFrom, pair.indexMv, supportsMV);
            else
                indexConvert = convert.addConvertColumn(pair.target, pair.indexFrom, pair.indexMv, pair.dp.getPropertyDescriptor(), pair.dp.getPropertyDescriptor().getPropertyType());

            List<ColumnValidator> validators = ColumnValidators.create(pair.target, pair.dp, context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.PreserveEmptyString));
            validate.addValidators(indexConvert, validators);
        }

        DataIterator last = validate.hasValidators() ? validate : convert;
        return LoggingDataIterator.wrap(ErrorIterator.wrap(last, context, false, setupError));
    }

    private String getTranslateHelperKey(ColumnInfo col)
    {
        return col.getPropertyURI() + ":" + col.getName().toLowerCase();
    }

    boolean isRequiredForInsert(@NotNull ColumnInfo col, @Nullable DomainProperty dp)
    {
        return col.isRequiredForInsert(dp);
    }
}
