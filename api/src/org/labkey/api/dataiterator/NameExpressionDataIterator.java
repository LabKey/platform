package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;

import java.util.Map;

public class NameExpressionDataIterator extends WrapperDataIterator
{
    private final DataIteratorContext _context;
    private final NameGenerator _nameGen;
    private final Integer _nameCol;

    private NameGenerator.State _state;

    public NameExpressionDataIterator(DataIterator di, DataIteratorContext context, String nameExpression, @Nullable TableInfo parentTable)
    {
        super(DataIteratorUtil.wrapMap(di, false));
        _context = context;
        _nameGen = new NameGenerator(nameExpression, parentTable, false);
        _state = _nameGen.createState(false, false);

        Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
        _nameCol = map.get("name");
        assert _nameCol != null;
    }

    MapDataIterator getInput()
    {
        return (MapDataIterator)_delegate;
    }

    private BatchValidationException getErrors()
    {
        return _context.getErrors();
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
                return curName;

            Map<String, Object> currentRow = getInput().getMap();

            try
            {
                String newName = _nameGen.generateName(_state, currentRow);
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
