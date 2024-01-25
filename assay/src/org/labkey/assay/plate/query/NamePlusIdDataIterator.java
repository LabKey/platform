package org.labkey.assay.plate.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;

import java.util.Map;

public class NamePlusIdDataIterator extends WrapperDataIterator
{
    private final DataIteratorContext _context;
    private final Integer _nameCol;
    private final Integer _idCol;
    private final NameGenerator _nameGenerator;
    private final NameGenerator.State _state;
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
