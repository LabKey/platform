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

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.data.validator.PropertyValidator;
import org.labkey.api.data.validator.RowValidator;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * User: matthewb
 * Date: 2011-05-19
 *
 * This is a pass-through iterator, it does not change any of the data, it only create errors
 */
public class ValidatorIterator extends AbstractDataIterator implements DataIterator
{
    final DataIterator _data;
    final ValidatorContext validatorContext;
    final ArrayList<RowValidator> _rowValidators = new ArrayList<>();
    final ArrayList<ArrayList<ColumnValidator>> _validators = new ArrayList<>();

    public ValidatorIterator(DataIterator data, DataIteratorContext context, Container c, User user)
    {
        super(context);
        _data = data;
        validatorContext = new ValidatorContext(c, user);
    }

    public void addValidators(int i, List<? extends ColumnValidator> vs)
    {
        if (vs != null)
        {
            while (_validators.size() <= i)
                _validators.add(new ArrayList<>());
            _validators.get(i).addAll(vs);
        }
    }

    public void addValidator(int i, ColumnValidator v)
    {
        if (v != null)
        {
            while (_validators.size() <= i)
                _validators.add(new ArrayList<>());
            _validators.get(i).add(v);
        }
    }


    /*
     * Add validator methods
     */

    public void addRequired(int i, @Nullable ColumnInfo col, @Nullable DomainProperty dp)
    {
        addValidator(i, ColumnValidators.createRequiredValidator(col, dp, _context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.PreserveEmptyString)));
    }


    public void addPropertyValidator(int i, DomainProperty prop)
    {
        addValidators(i, ColumnValidators.createPropertyValidators(prop));
    }


    public void addUniqueValidator(int i, boolean caseSensitive)
    {
        ColumnInfo col = _data.getColumnInfo(i);
        addValidator(i, ColumnValidators.createUniqueValidator(col, caseSensitive));
    }


    public void addLengthValidator(int i)
    {
        ColumnInfo col = _data.getColumnInfo(i);
        addValidator(i, ColumnValidators.createLengthValidator(col, null));
    }


    public void addDateValidator(int i)
    {
        ColumnInfo col = _data.getColumnInfo(i);
        addValidator(i, ColumnValidators.createDateValidator(col, null));
    }


    public boolean hasValidators()
    {
        if (!_rowValidators.isEmpty())
            return true;

        for (ArrayList<ColumnValidator> a : _validators)
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

            int rowNum = _data.get(0) instanceof Integer ? ((Integer)_data.get(0)) : -1;

            // first the column validators
            for (int i=1 ; i<_validators.size() ; i++)
            {
                List<ColumnValidator> l = _validators.get(i);
                if (null == l) continue;
                for (ColumnValidator v : l)
                {
                    Object value = _data.get(i);
                    String msg;
                    // CONSIDER: add validatorContext to ColumnValidator.validate() always
                    if (v instanceof PropertyValidator)
                        msg = v.validate(rowNum, value, validatorContext);
                    else
                        msg = v.validate(rowNum, value);
                    if (null != msg)
                    {
                        addFieldError(_data.getColumnInfo(i).getName(), msg);
                        validRow = false;
                        break;
                    }
                }
            }

            // row validators
            if (!_rowValidators.isEmpty())
            {
                for (RowValidator v : _rowValidators)
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
    public Supplier<Object> getSupplier(int i)
    {
        return _data.getSupplier(i);
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

    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        sb.append(this.getClass().getName() + "\n");
        if (null != _data)
            _data.debugLogInfo(sb);
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
