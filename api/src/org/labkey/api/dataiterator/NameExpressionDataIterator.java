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
import org.labkey.api.data.NameGeneratorState;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class NameExpressionDataIterator extends WrapperDataIterator
{
    private final DataIteratorContext _context;
    private final Map<String, Pair<NameGenerator, NameGeneratorState>> _nameGeneratorMap = new HashMap<>();
    private final Map<String, String> _newNames = new HashMap<>();
    private final Integer _nameCol;
    private final String _nameColName;
    private final Integer _expressionCol;
    private final TableInfo _parentTable;
    private final Container _container;
    private final Function<String, Long> _getNonConflictCountFn;
    private final String _counterSeqPrefix;
    private boolean _allowUserSpecifiedNames = true;        // whether manual names specification is allowed or only name expression generation
    private final List<Supplier<Map<String, Object>>> _extraPropsFns = new ArrayList<>();
    private final Map<String, String> _importAliases;

    public NameExpressionDataIterator(DataIterator di, DataIteratorContext context, @Nullable TableInfo parentTable, @Nullable Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix, @Nullable Map<String, String> importAliases)
    {
        this(di, context, parentTable, container, getNonConflictCountFn, counterSeqPrefix, importAliases, "name", "nameExpression");
    }

    public NameExpressionDataIterator(DataIterator di, DataIteratorContext context, @Nullable TableInfo parentTable, @Nullable Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix, @Nullable Map<String, String> importAliases, String nameColName, String expressionColName)
    {
        super(DataIteratorUtil.wrapMap(di, false));
        _context = context;
        _parentTable = parentTable;
        _importAliases = importAliases;

        Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
        _nameColName = nameColName;
        _nameCol = map.get(nameColName);
        _expressionCol = map.get(expressionColName);
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

    public NameExpressionDataIterator addExtraPropsFn(Supplier<Map<String, Object>> extraProps)
    {
        _extraPropsFns.add(extraProps);
        return this;
    }

    MapDataIterator getInput()
    {
        return (MapDataIterator) _delegate;
    }

    private BatchValidationException getErrors()
    {
        return _context.getErrors();
    }

    private void addNameGenerator(String nameExpression)
    {
        NameGenerator nameGen = new NameGenerator(nameExpression, _parentTable, false, _importAliases, _container, _getNonConflictCountFn, _counterSeqPrefix);
        NameGeneratorState state = nameGen.createState(false, _nameColName);
        _nameGeneratorMap.put(nameExpression, Pair.of(nameGen, state));
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        // Clear cache of generated names
        _newNames.clear();
        boolean next = super.next();
        if (!next)
            syncCounterSeqs();
        return next;
    }

    public void syncCounterSeqs()
    {
        for (Map.Entry<String, Pair<NameGenerator, NameGeneratorState>> nameGenerator: _nameGeneratorMap.entrySet())
        {
            NameGeneratorState state = nameGenerator.getValue().second;
            state.cleanUp(); // explicitly call state.cleanUp so DB sequence gets cleaned up in transaction
        }
    }

    @Override
    public Supplier<Object> getSupplier(int i)
    {
        if (i == _nameCol)
            return () -> get(_nameCol);
        return _delegate.getSupplier(i);
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
                    Pair<NameGenerator, NameGeneratorState> nameGenPair = _nameGeneratorMap.get(nameExpression);
                    _newNames.put(nameExpression, nameGenPair.first.generateName(nameGenPair.second, currentRow, null, null, _extraPropsFns));
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
