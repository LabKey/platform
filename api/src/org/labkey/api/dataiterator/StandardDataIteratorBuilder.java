/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.data.validator.RequiredValidator;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.ontology.OntologyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    final Container _c;
    final User _user;
    final CaseInsensitiveHashSet dontRequire = new CaseInsensitiveHashSet();

    // major components of standard processing, default we do all of these
    boolean _convertTypes = true;
    boolean _builtInColumns = true;
    boolean _validate = true;

    public static StandardDataIteratorBuilder forInsert(TableInfo target, @NotNull DataIteratorBuilder in, @Nullable Container c, @NotNull User user, DataIteratorContext context)
    {
        return new StandardDataIteratorBuilder(target, in, c, user);
    }

    public static StandardDataIteratorBuilder forInsert(TableInfo target, @NotNull DataIteratorBuilder in, @Nullable Container c, @NotNull User user)
    {
        return new StandardDataIteratorBuilder(target, in, c, user);
    }

    /* do the standard column matching logic, but no coercion or validation */
    public static DataIteratorBuilder forColumnMatching(TableInfo target, @NotNull DataIteratorBuilder in, @Nullable Container c, @NotNull User user)
    {
        StandardDataIteratorBuilder ret = new StandardDataIteratorBuilder(target, in, c, user);
        ret._convertTypes = ret._validate = ret._builtInColumns = false;
        ret._useImportAliases = true;
        return ret;
    }

    protected StandardDataIteratorBuilder(TableInfo target, @NotNull DataIteratorBuilder in, @Nullable Container c, @NotNull User user)
    {
        _inputBuilder = in;
        _target = target;
        _c = c;
        _user = user;
    }

    /*
     * This is a way to indicate that SDIB should ignore the required constraint on a column.
     * This can be used if the required column will be provided by a later step in the DI.
     */
    public void addDoNotRequireColumn(String name)
    {
        dontRequire.add(name);
    }

    public static class TranslateHelper
    {
        TranslateHelper(ColumnInfo col, DomainProperty dp)
        {
            this.target = col;
            this.dp = dp;
        }
        int indexFrom = 0;
        int indexMv = SimpleTranslator.NO_MV_INDEX;
        ColumnInfo target;
        DomainProperty dp;

        public ColumnInfo getTarget()
        {
            return target;
        }

        public DomainProperty getDp()
        {
            return dp;
        }

    }

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
        _useImportAliases = context.getInsertOption().useImportAliases;

        Domain d = _target.getDomain();

        Map<String, DomainProperty> propertiesMap = new HashMap<>();
        if (null != d)
        {
            for (DomainProperty dp : d.getProperties())
                propertiesMap.put(dp.getPropertyURI(), dp);
        }

        DataIteratorBuilder dib = _inputBuilder;

        // Add translator/validator for ontology import features
        if (null != OntologyService.get())
            dib = OntologyService.get().getConceptLookupDataIteratorBuilder(dib, _target);

        DataIterator input = dib.getDataIterator(context);

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

        if (_builtInColumns)
            input = SimpleTranslator.wrapBuiltInColumns(input, context, _c, _user, _target);

        Map<String,Integer> sourceColumnsMap = DataIteratorUtil.createColumnAndPropertyMap(input);


        /*
         * NOTE: shouldn't really need DomainProperty here,
         * but not all information is available on the ColumnInfo
         * notably we need PropertyValidators
         *
         * Anyway match up the columns and property descriptors and keep them in a set of TranslateHelpers
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
        //  CONVERT data iterator
        //
        // set up a SimpleTranslator for conversion and missing-value handling
        //

        //
        // match up the columns, validate that there is no more than one source column that matches the target column
        //
        ValidationException setupError = new ValidationException();
        ArrayList<ColumnInfo> matches = DataIteratorUtil.matchColumns(input, _target, _useImportAliases, setupError, _c);

        ArrayList<TranslateHelper> convertTargetCols = new ArrayList<>(input.getColumnCount()+1);
        for (int i=1 ; i<=input.getColumnCount() ; i++)
        {
            ColumnInfo targetCol = matches.get(i);
            TranslateHelper to = null;
            if (null != targetCol)
                to = translateHelperMap.get(getTranslateHelperKey(targetCol));

            if (null != to && _convertTypes)
            {
                if (!unusedCols.containsKey(to.target.getFieldKey()))
                    setupError.addGlobalError("Two columns mapped to target column " + to.target.getName() + ". Check the column names and import aliases for your data.");
                unusedCols.remove(to.target.getFieldKey());
                to.indexFrom = i;
                Integer indexMv = null==to.target.getMvColumnName() ? null : sourceColumnsMap.get(to.target.getMvColumnName().getName());
                to.indexMv = null==indexMv ? SimpleTranslator.NO_MV_INDEX : indexMv.intValue();
                convertTargetCols.add(to);
            }
            else
            {
                // pass through unrecognized columns (may be internal column like "_key")
                to = new TranslateHelper(null, null);
                to.indexFrom = i;
                convertTargetCols.add(to);
            }
        }

        SimpleTranslator convert = new SimpleTranslator(input, context);
        convert.setDebugName("StandardDIB convert");
        convert.setMvContainer(_c);

        //
        // check for unbound columns that are required
        //
        Map<String, String> additionalRequiredColumns = _target.getAdditionalRequiredInsertColumns();
        if (_validate && !context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.SkipRequiredFieldValidation) && !context.getInsertOption().updateOnly)
        {
            for (TranslateHelper pair : unusedCols.values())
            {
                if (isRequiredForInsert(pair.target, pair.dp))
                    setupError.addGlobalError("Data does not contain required field: " + pair.target.getName());
            }

            if (!context.getConfigParameterBoolean(ExperimentService.QueryOptions.DeferRequiredLineageValidation))
            {
                if (!additionalRequiredColumns.isEmpty())
                {
                    Set<String> allColumns = new CaseInsensitiveHashSet(convert.getColumnNameMap().keySet());
                    for (Map.Entry<String, String> entry : additionalRequiredColumns.entrySet())
                    {
                        if (!allColumns.contains(entry.getKey()) && !allColumns.contains(entry.getValue()))
                        {
                            setupError.addGlobalError("Data does not contain required field: " + entry.getValue());
                        }
                    }
                }
            }
        }

        for (TranslateHelper pair : convertTargetCols)
        {
            PropertyDescriptor pd = pair.dp == null ? null : pair.dp.getPropertyDescriptor();
            PropertyType pt = pd == null ? null : pd.getPropertyType();
            boolean isAttachment = pt == PropertyType.ATTACHMENT || pt == PropertyType.FILE_LINK;

            if (null == pair.target || isAttachment)
                convert.addColumn(pair.indexFrom);
            else
                convert.addConvertColumn(pair.target, pair.indexFrom, pair.indexMv, pd, pt, pair.target.getRemapMissingBehavior());
        }

        //
        // CONCEPT mapping data iterator
        //

        DataIterator validateInput = convert;
        if (null != OntologyService.get())
            validateInput = OntologyService.get().getConceptLookupDataIteratorBuilder(convert, _target).getDataIterator(context);


        //
        // VALIDATE data iterator
        //

        DataIterator last = validateInput;

        if (_validate)
        {
            Set<String> additionalRequiredCols = new CaseInsensitiveHashSet();
            if (!context.getConfigParameterBoolean(ExperimentService.QueryOptions.DeferRequiredLineageValidation))
            {
                additionalRequiredCols.addAll(additionalRequiredColumns.keySet());
                additionalRequiredCols.addAll(additionalRequiredColumns.values());
            }
            ValidatorIterator validate = getValidatorIterator(validateInput, context, translateHelperMap, _c, _user);

            for (int index = 1; index <= validateInput.getColumnCount(); index++)
            {
                ColumnInfo col = validateInput.getColumnInfo(index);
                TranslateHelper pair = translateHelperMap.get(getTranslateHelperKey(col));
                if (null == pair)
                {
                    if (additionalRequiredCols.contains(col.getColumnName()))
                    {
                        List<ColumnValidator> validators = new ArrayList<>();
                        validators.add(new RequiredValidator(col.getColumnName(), false, context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.PreserveEmptyString)));
                        validate.addValidators(index, validators);
                    }
                    continue;
                }
                List<ColumnValidator> validators = ColumnValidators.create(pair.target, pair.dp, context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.PreserveEmptyString));
                validate.addValidators(index, validators);
            }
            if (validate.hasValidators())
                last = validate;
        }

        if (context.getInsertOption().updateOnly && !unusedCols.isEmpty())
        {
            Set<String> unusedColNames = new HashSet<>();
            for (FieldKey fieldKey : unusedCols.keySet())
            {
                unusedColNames.add(fieldKey.getName());
            }
            context.getDontUpdateColumnNames().addAll(unusedColNames);
        }

        if (context.isBackgroundJob())
            last = new QueryImportJobStatusCheckDataIterator(last, context, 1000);

        return LoggingDataIterator.wrap(ErrorIterator.wrap(last, context, false, setupError));
    }

    protected ValidatorIterator getValidatorIterator(DataIterator validateInput, DataIteratorContext context, Map<String, TranslateHelper> translateHelperMap, Container c, User user)
    {
        ValidatorIterator validate = new ValidatorIterator(LoggingDataIterator.wrap(validateInput), context, c, user);
        validate.setDebugName("StandardDIB validate");
        return validate;
    }

    protected String getTranslateHelperKey(ColumnInfo col)
    {
        return col.getPropertyURI() + ":" + col.getName().toLowerCase();
    }

    boolean isRequiredForInsert(@NotNull ColumnInfo col, @Nullable DomainProperty dp)
    {
        if (dontRequire.contains(col.getName()))
            return false;
        return col.isRequiredForInsert(dp);
    }
}
