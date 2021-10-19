/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class NameExpressionDataIterator extends WrapperDataIterator
{
    private final DataIteratorContext _context;
    private Map<String, Pair<NameGenerator, NameGenerator.State>> _nameGeneratorMap = new HashMap<>();
    private Map<String, String> _newNames = new HashMap<>();
    private final Integer _nameCol;
    private Integer _expressionCol;
    private TableInfo _parentTable;
    private Container _container;
    private Function<String, Long> _getNonConflictCountFn;
    private String _counterSeqPrefix;
    private boolean _allowUserSpecifiedNames = true;        // whether manual names specification is allowed or only name expression generation

    public NameExpressionDataIterator(DataIterator di, DataIteratorContext context, @Nullable TableInfo parentTable, @Nullable Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix)
    {
        super(DataIteratorUtil.wrapMap(di, false));
        _context = context;
        _parentTable = parentTable;

        Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
        _nameCol = map.get("name");
        _expressionCol = map.get("nameExpression");
        assert _nameCol != null;
        assert _expressionCol != null;

        _container = container;
        _getNonConflictCountFn = getNonConflictCountFn;
        _counterSeqPrefix = counterSeqPrefix;

    }

    public NameExpressionDataIterator setAllowUserSpecifiedNames(boolean allowUserSpecifiedNames)
    {
        _allowUserSpecifiedNames = allowUserSpecifiedNames;
        return this;
    }

    MapDataIterator getInput()
    {
        return (MapDataIterator)_delegate;
    }

    private BatchValidationException getErrors()
    {
        return _context.getErrors();
    }

    private void addNameGenerator(String nameExpression)
    {
        NameGenerator nameGen = new NameGenerator(nameExpression, _parentTable, false, _container, _getNonConflictCountFn, _counterSeqPrefix);
        NameGenerator.State state = nameGen.createState(false);
        _nameGeneratorMap.put(nameExpression, Pair.of(nameGen, state));
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        // Clear cache of generated names
        _newNames.clear();
        return super.next();
    }

    @Override
    public Object get(int i)
    {
        if (i == _nameCol)
        {
            Object curName = super.get(_nameCol);
            if (curName instanceof String)
                curName = StringUtils.isEmpty((String)curName) ? null : curName;

            if (curName != null)
            {
                if (!_allowUserSpecifiedNames)
                    getErrors().addRowError(new ValidationException("Manual entry of names has been disabled for this folder. Only naming-pattern-generated names are allowed."));
                return curName;
            }

            Map<String, Object> currentRow = getInput().getMap();

            try
            {
                String nameExpression = (String) super.get(_expressionCol);
                if (!_nameGeneratorMap.containsKey(nameExpression))
                {
                    addNameGenerator(nameExpression);
                }

                if (_newNames.get(nameExpression) == null)
                {
                    Pair<NameGenerator, NameGenerator.State> nameGenPair = _nameGeneratorMap.get(nameExpression);
                    _newNames.put(nameExpression, nameGenPair.first.generateName(nameGenPair.second, currentRow));
                }
                String newName = _newNames.get(nameExpression);
                if (!StringUtils.isEmpty(newName))
                    return newName;
            }
            catch (NameGenerator.NameGenerationException e)
            {
                getErrors().addRowError(new ValidationException(e.getMessage()));
            }
        }

        return super.get(i);
    }

}
