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
package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.NameGeneratorState;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;

import java.util.Map;

public class NamePlusIdDataIterator extends WrapperDataIterator
{
    private final DataIteratorContext _context;
    private final Integer _nameCol;
    private final Integer _idCol;
    private final NameGenerator _nameGenerator;
    private final NameGeneratorState _state;
    private String _generatedName;

    public NamePlusIdDataIterator(DataIterator di, DataIteratorContext context, TableInfo parentTable, Container container,
                                     String nameColumn, String idColumn, String nameExpression)
    {
        super(DataIteratorUtil.wrapMap(di, false));

        _context = context;
        Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
        _nameCol = map.get(nameColumn);
        _idCol = map.get(idColumn);

        _nameGenerator = new NameGenerator(nameExpression, parentTable, false, container, null, null);
        _state = _nameGenerator.createState(false);
    }

    MapDataIterator getInput()
    {
        return (MapDataIterator) _delegate;
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        boolean next = super.next();
        if (next)
        {
            try
            {
                Map<String, Object> currentRow = new CaseInsensitiveHashMap<>(getInput().getMap());
                // remove the name field so we don't use it
                currentRow.put("name", null);
                _generatedName = _nameGenerator.generateName(_state, currentRow);
                _state.cleanUp();
            }
            catch (NameGenerator.NameGenerationException e)
            {
                _context.getErrors().addRowError(new ValidationException(e.getMessage()));
            }
        }
        return next;
    }

    @Override
    public Object get(int i)
    {
        if (i == _nameCol)
        {
            Object curName = super.get(_nameCol);
            if (curName instanceof String)
                curName = StringUtils.isEmpty((String) curName) ? null : curName;

            if (curName != null)
                return curName;
            else
                return _generatedName;
        }
        else if (i == _idCol)
        {
            return _generatedName;
        }
        return super.get(i);
    }
}
