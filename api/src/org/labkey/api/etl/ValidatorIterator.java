/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationError;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * User: matthewb
 * Date: 2011-05-19
 * Time: 1:24 PM
 *
 * This is a pass-through iterator, it does not change any of the data, it only create errors
 */
public class ValidatorIterator extends AbstractDataIterator implements DataIterator
{
    final DataIterator _data;
    final ValidatorContext validatorContext;
    final ArrayList<ArrayList<Validator>> _validators = new ArrayList<>();

    public ValidatorIterator(DataIterator data, DataIteratorContext context, Container c, User user)
    {
        super(context);
        _data = data;
        validatorContext = new ValidatorContext(c, user);
    }


    public static interface Validator
    {
        String validate();
    }


    /** marker interface for Validators that understand missing values, most do not */
    public static interface UnderstandsMissingValues
    {}


    abstract class ColumnValidator implements Validator
    {
        int index;

        public ColumnValidator(int i)
        {
            index = i;
        }

        public String validate()
        {
            Object o = _data.get(index);
            if (o instanceof MvFieldWrapper && !(this instanceof UnderstandsMissingValues))
                return null;
            return validate(o);
        }

        public abstract String validate(Object value);
    }


    /*
     * there seem to be two kinds of required
     * those that accept MissingValue and those that don't
     */
    class RequiredValidator extends ColumnValidator implements UnderstandsMissingValues
    {
        boolean allowMV = false;
        String propertyName = null;

        RequiredValidator(int index, boolean allowMissingValueIndicators)
        {
            super(index);
            this.allowMV = allowMissingValueIndicators;
        }

        RequiredValidator(int index, boolean allowMissingValueIndicators, String propertyName)
        {
            super(index);
            this.propertyName = propertyName;
            this.allowMV = allowMissingValueIndicators;
        }

        @Override
        public String validate(Object value)
        {
checkRequired:
            {
                if (null == value || (value instanceof String && ((String)value).length() == 0))
                    break checkRequired;

                if (!(value instanceof MvFieldWrapper))
                    return null;

                MvFieldWrapper mv = (MvFieldWrapper)value;
                if (null != mv.getValue())
                    return null;

                if (!mv.isEmpty() && allowMV)
                    return null;
            }

            // DatasetDefinition.importDatasetData:: errors.add("Row " + rowNumber + " does not contain required field " + col.getName() + ".");
            // OntologyManager.insertTabDelimited::  throw new ValidationException("Missing value for required property " + col.getName());
            return "Missing value for required property: " + StringUtils.defaultString(propertyName, _data.getColumnInfo(index).getName());
        }
    }


    class ValidatorKindWrapper extends ColumnValidator
    {
        final ValidatorKind kind;
        final IPropertyValidator propertyValidator;
        final PropertyDescriptor pd;
        final List<ValidationError> errors = new ArrayList<>(1);

        ValidatorKindWrapper(int index, PropertyDescriptor pd, IPropertyValidator propertyValidator)
        {
            super(index);
            this.propertyValidator = propertyValidator;
            this.kind = propertyValidator.getType();
            this.pd = pd;
        }

        @Override
        public String validate(Object value)
        {
            // Don't validate null values, #15683, #19352
            if (null == value)
                return null;
            if (kind.validate(propertyValidator, pd , value, errors, validatorContext))
                return null;
            if (errors.isEmpty())
                return null;
            String msg = errors.get(0).getMessage();
            errors.clear();
            return msg;
        }
    }


    class LengthValidator extends ColumnValidator
    {
        private final int scale;

        LengthValidator(int col, int scale)
        {
            super(col);
            this.scale = scale;
        }

        @Override
        public String validate(Object value)
        {
            if (value instanceof String)
            {
                String s = (String)value;
                if (s.length() > scale)
                    return "Value too long for column \"" +  _data.getColumnInfo(index).getName() + "\"; maximum length is " + scale;
            }

            return null;
        }
    }


    // validate that there are no in-coming duplicates for unique key
    // does not validate against DB, just the import data
    class DuplicateSingleKeyValidator extends ColumnValidator
    {
        final boolean caseInsensitive;
        Set _keys = null;

        DuplicateSingleKeyValidator(int col, boolean caseInsensitive)
        {
            super(col);
            this.caseInsensitive = caseInsensitive;
        }

        @Override
        public String validate(Object key)
        {
            if (null == _keys)
            {
                if (caseInsensitive && _data.getColumnInfo(index).getJdbcType().isText())
                    _keys = new CaseInsensitiveHashSet();
                else
                    _keys = new HashSet();
            }
            if (_keys.size() > 10000 || _keys.add(key))
                return null;
            return "Row " + _data.get(0) + ": " + "The key field \"" + _data.getColumnInfo(index).getName() + "\" cannot have duplicate values.  The duplicate is: \"" + key + "\"";
        }
    }


    // this is are postgres ranges, sql server supports a wide range
    static long minTimestamp =  DateUtil.parseISODateTime("1753-01-01");
    static long maxTimestamp = DateUtil.parseISODateTime("9999-12-31") + TimeUnit.DAYS.toMillis(1);

    class DateValidator extends ColumnValidator
    {
        DateValidator(int col)
        {
            super(col);
        }

        @Override
        public String validate(Object value)
        {
            if (!(value instanceof java.util.Date))
                return null;
            long t = ((java.util.Date)value).getTime();
            if (t >= minTimestamp && t < maxTimestamp)
                return null;
            return "Only dates between January 1, 1753 and December 31, 9999 are accepted.";
        }
    }


    private void addValidator(int i, Validator v)
    {
        while (_validators.size() <= i)
            _validators.add(new ArrayList<Validator>());
        _validators.get(i).add(v);
    }


    /*
     * Add validator methods
     */

    public void addRequired(int i, boolean allowMv)
    {
        RequiredValidator v = new RequiredValidator(i, allowMv);
        addValidator(i, v);
    }


    public void addPropertyValidator(int i, DomainProperty prop)
    {
        List<? extends IPropertyValidator> validators = prop.getValidators();
        for (IPropertyValidator pv : validators)
        {
            ValidatorKindWrapper v = new ValidatorKindWrapper(i, prop.getPropertyDescriptor(), pv);
            addValidator(i, v);
        }
    }


    public void addUniqueValidator(int i, boolean caseSensitive)
    {
        addValidator(i, new DuplicateSingleKeyValidator(i, caseSensitive));
    }


    public void addLengthValidator(int i, ColumnInfo col)
    {
        if (col == null)
            return;

        if (col.getJdbcType().isText() && col.getJdbcType() != JdbcType.GUID && col.getScale() > 0)
            addValidator(i, new LengthValidator(i, col.getScale()));
    }


    public void addDateValidator(int i)
    {
        addValidator(i, new DateValidator(i));
    }


    public boolean hasValidators()
    {
        for (ArrayList<Validator> a : _validators)
        {
            if (!a.isEmpty())
                return true;
        }
        return false;
    }


    /* DataIterator */

    @Override
    public int getColumnCount()
    {
        return _data.getColumnCount();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _data.getColumnInfo(i);
    }


    @Override
    public boolean next() throws BatchValidationException
    {
        boolean validRow = true;
        boolean hasValidationErrors = false;

        do
        {
            if (!_data.next())
                return false;

            // first the column validators
            for (int i=1 ; i<_validators.size() ; i++)
            {
                List<Validator> l = _validators.get(i);
                if (null == l) continue;
                for (Validator v : l)
                {
                    String msg = v.validate();
                    if (null != msg)
                    {
                        addFieldError(_data.getColumnInfo(i).getName(), msg);
                        validRow = false;
                        break;
                    }
                }
            }
            // row validators
            List<Validator> l = _validators.isEmpty() ? null : _validators.get(0);
            if (null != l)
            {
                for (Validator v : l)
                {
                    String msg = v.validate();
                    if (null != msg)
                    {
                        addRowError(msg);
                        validRow = false;
                        break;
                    }
                }
            }
            if (!validRow)
            {
                // we'll never return true once we hit a validation error
                hasValidationErrors = true;
                checkShouldCancel();
            }
        }
        while (hasValidationErrors);

        return true;
    }


    @Override
    public Object get(int i)
    {
        return _data.get(i);
    }

    @Override
    public void close() throws IOException
    {
        _data.close();
    }


    @Override
    public boolean isConstant(int i)
    {
        return _data.isConstant(i);
    }


    @Override
    public Object getConstantValue(int i)
    {
        return _data.getConstantValue(i);
    }


    /*
    * Tests
    */

    private static String[] as(String... arr)
    {
        return arr;
    }



    public static class ValidateTestCase extends Assert
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

        @Test
        public void validateTest() throws Exception
        {
        }
    }
}
