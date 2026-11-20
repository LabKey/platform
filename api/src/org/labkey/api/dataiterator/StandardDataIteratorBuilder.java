/*
 * Copyright (c) 2011-2026 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.ontology.OntologyService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.HashMap;
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

    /* do the standard column matching logic, reporting matched columns as their target columns, but no coercion or validation */
    public static DataIteratorBuilder forColumnMatching(TableInfo target, @NotNull DataIteratorBuilder in, @Nullable Container c, @NotNull User user)
    {
        StandardDataIteratorBuilder ret = new StandardDataIteratorBuilder(target, in, c, user);
        ret._convertTypes = ret._validate = ret._builtInColumns = false;
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

    @Override
    public DataIterator getDataIterator(DataIteratorContext context)
    {
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

        //
        //  MATCH and CONVERT data iterators
        //
        // match up the columns, validate that there is no more than one source column that matches the target column,
        // then convert types and handle missing values
        //
        ValidationException setupError = new ValidationException();
        MatchMakerDataIterator match = new MatchMakerDataIterator(input, _target, context.getInsertOption().useImportAliases, _c, setupError);
        match.setDebugName("StandardDIB match");
        DataIterator convert = match;
        if (_convertTypes)
        {
            ConvertDataIterator convertTypes = new ConvertDataIterator(match, context, _c);
            convertTypes.setDebugName("StandardDIB convert");
            convert = convertTypes;
        }

        //
        // check for unbound columns that are required
        //
        List<Set<String>> additionalRequiredInsertColumns = _target.getAdditionalRequiredInsertColumns();
        if (_validate && !context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.SkipRequiredFieldValidation) && !context.getInsertOption().updateOnly)
        {
            for (MatchMakerDataIterator.TargetColumn unmatched : match.getUnmatchedColumns())
            {
                if (isRequiredForInsert(unmatched.getTarget(), unmatched.getDp()))
                    setupError.addGlobalError("Data does not contain required field: " + unmatched.getTarget().getName());
            }

            if (!context.getConfigParameterBoolean(ExperimentService.QueryOptions.DeferRequiredLineageValidation))
            {
                if (!additionalRequiredInsertColumns.isEmpty())
                {
                    Set<String> allColumns = new CaseInsensitiveHashSet(DataIteratorUtil.createColumnNameMap(input).keySet());
                    for (Set<String> requiredColumnSet : additionalRequiredInsertColumns)
                    {
                        if (Collections.disjoint(allColumns, new CaseInsensitiveHashSet(requiredColumnSet)))
                            setupError.addGlobalError("Data does not contain required field: " + requiredColumnSet.iterator().next());
                    }
                }
            }
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
                for (Set<String> requiredFields : additionalRequiredInsertColumns)
                    additionalRequiredCols.addAll(requiredFields);
            }
            Map<Integer, List<ColumnValidator>> validators = new HashMap<>();
            for (int index = 1; index <= validateInput.getColumnCount(); index++)
            {
                ColumnInfo col = validateInput.getColumnInfo(index);
                MatchMakerDataIterator.TargetColumn tc = match.getTargetColumn(col);
                if (null == tc)
                {
                    if (additionalRequiredCols.contains(col.getColumnName()))
                    {
                        var reqd = ColumnValidators.createRequiredValidator(col, null, context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.PreserveEmptyString));
                        if (null != reqd)
                            validators.put(index, List.of(reqd));
                    }
                    continue;
                }
                validators.put(index, ColumnValidators.create(tc.getTarget(), tc.getDp(), context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.PreserveEmptyString)));
            }
            ValidatorIterator validate = getValidatorIterator(validateInput, context, match, _c, _user, validators);
            if (validate.hasValidators())
                last = validate;
        }

        if (context.getInsertOption().updateOnly)
            match.getUnmatchedColumns().forEach(tc -> context.getDontUpdateColumnNames().add(tc.getTarget().getFieldKey().getName()));

        if (context.isBackgroundJob())
            last = new QueryImportJobStatusCheckDataIterator(last, context, 1000);

        return LoggingDataIterator.wrap(ErrorIterator.wrap(last, context, false, setupError));
    }

    protected ValidatorIterator getValidatorIterator(DataIterator validateInput, DataIteratorContext context, MatchMakerDataIterator match, Container c, User user, Map<Integer, List<ColumnValidator>> validators)
    {
        ValidatorIterator validate = new ValidatorIterator(LoggingDataIterator.wrap(validateInput), context, c, user, validators);
        validate.setDebugName("StandardDIB validate");
        return validate;
    }

    boolean isRequiredForInsert(@NotNull ColumnInfo col, @Nullable DomainProperty dp)
    {
        if (dontRequire.contains(col.getName()))
            return false;
        return col.isRequiredForInsert(dp);
    }
}
