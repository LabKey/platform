/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.experiment;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;

/**
 * WrapperDataIterator that operate on ExpObjects and need to track sample/data class information.
 */
public class ExpDataTypeDataIterator extends WrapperDataIterator
{
    protected final DataIteratorContext _context;
    protected final Container _container;
    protected final User _user;

    private final boolean _isSample;
    private final ExpDataClass _dataClass;
    private final ExpSampleType _sampleType;

    protected ExpDataTypeDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, ExpObject dataType, boolean isSample)
    {
        super(di);
        _context = context;
        _container = container;
        _user = user;
        _isSample = isSample;

        if (isSample)
        {
            _sampleType = (ExpSampleType) dataType;
            _dataClass = null;
        }
        else
        {
            _sampleType = null;
            _dataClass = (ExpDataClass) dataType;
        }
    }

    protected BatchValidationException getErrors()
    {
        return _context.getErrors();
    }

    protected boolean isSample()
    {
        return _isSample;
    }

    protected ExpObject getExpObjectByLsid(String lsid)
    {
        ExpObject obj;
        if (isSample())
            obj = ExperimentService.get().getExpMaterial(lsid);
        else
            obj = ExperimentService.get().getExpData(lsid);
        return obj;
    }

    protected ExpObject getExpObjectByName(String name)
    {
        ExpObject obj;
        if (isSample())
            obj = getSampleType().getSample(_container, name);
        else
            obj = getDataClass().getData(_container, name);
        return obj;
    }

    protected @NotNull ExpSampleType getSampleType()
    {
        if (_sampleType == null)
            throw new IllegalStateException("Requested a sample type when the iterator is not for samples. Check isSample() first.");
        return _sampleType;
    }

    protected @NotNull ExpDataClass getDataClass()
    {
        if (_dataClass == null)
            throw new IllegalStateException("Requested a data class when the iterator is not for data. Check isSample() first.");
        return _dataClass;
    }
}
