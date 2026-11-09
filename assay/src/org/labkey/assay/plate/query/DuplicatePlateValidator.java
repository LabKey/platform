/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.assay.plate.query;

import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.data.Container;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.plate.PlateManager;

import java.util.function.Supplier;

import static org.labkey.api.util.IntegerUtils.asLong;

public class DuplicatePlateValidator extends WrapperDataIterator
{
    private final DataIteratorContext _context;
    private final Supplier<?> _nameSupplier;
    private final Supplier<?> _plateSetSupplier;
    private final Container _container;
    private final User _user;

    public DuplicatePlateValidator(DataIterator di, DataIteratorContext context, Container container, User user)
    {
        super(DataIteratorUtil.wrapMap(di, false));

        _context = context;
        _container = container;
        var nameMap = DataIteratorUtil.createColumnNameMap(di);
        _nameSupplier = _delegate.getSupplier(nameMap.get("name"));
        _plateSetSupplier = _delegate.getSupplier(nameMap.get("plateSet"));
        _user = user;
    }

    MapDataIterator getInput()
    {
        return (MapDataIterator) _delegate;
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        boolean hasNext = super.next();
        if (!hasNext)
            return false;

        String name = String.valueOf(_nameSupplier.get());
        Long plateSet = asLong(_plateSetSupplier.get());
        if (name != null & plateSet != null)
        {
            PlateSet ps = PlateManager.get().getPlateSet(_container, plateSet);
            if (PlateManager.get().isDuplicatePlateName(_container, _user, name, ps))
                _context.getErrors().addRowError(new ValidationException("Plate with name : " + name + " already exists."));
        }
        return true;
    }
}
