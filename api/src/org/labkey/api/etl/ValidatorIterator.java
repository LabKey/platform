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

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.*;
import org.labkey.api.util.GUID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2011-05-19
 * Time: 1:24 PM
 *
 * This is a pass-through iterator, it does not change any of the data, it only create errors
 *
 * Should all columns be validated or just those that are asked for?
 */
public class ValidatorIterator implements DataIterator
{
    final ValidationException _errors;
    boolean failFast = true;
    final DataIterator _data;
    final ValidatorContext validatorContext;
    final ArrayList<ArrayList<Validator>> _validators = new ArrayList<ArrayList<Validator>>();


    ValidatorIterator(DataIterator data, ValidationException errors, Container c, User user)
    {
        _data = data;
        _errors = errors;
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
        }

        RequiredValidator(int index, boolean allowMissingValueIndicators, String propertyName)
        {
            super(index);
            this.propertyName = propertyName;
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
        final List<ValidationError> errors = new ArrayList<ValidationError>(1);

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
            if (kind.validate(propertyValidator, pd , value, errors, validatorContext))
                return null;
            if (errors.isEmpty())
                return null;
            String msg = errors.get(0).getMessage();
            errors.clear();
            return msg;
        }
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
    public boolean next() throws ValidationException
    {
        if (!_data.next())
            return false;

        boolean validRow = true;

        // first the column validators
        int count = _data.getColumnCount();
        for (int i=1 ; i<=count ; i++)
        {
            List<Validator> l = _validators.get(i);
            if (null == l) continue;
            for (Validator v : l)
            {
                String msg = v.validate();
                if (null != msg)
                {
                    _errors.addFieldError(_data.getColumnInfo(i).getName(), msg);
                    validRow = false;
                    break;
                }
            }
        }
        // row validators
        List<Validator> l = _validators.get(0);
        if (null != l)
        {
            for (Validator v : l)
            {
                String msg = v.validate();
                if (null != msg)
                {
                    _errors.setRowNumber((Integer)_data.get(0));
                    _errors.addGlobalError(msg);
                    validRow = false;
                    break;
                }
            }
        }

        return validRow || !failFast;
    }

    @Override
    public Object get(int i)
    {
        return _data.getColumnInfo(i);
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
