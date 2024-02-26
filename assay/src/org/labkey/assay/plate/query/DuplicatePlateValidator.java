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

import java.util.Map;

public class DuplicatePlateValidator extends WrapperDataIterator
{
    private final DataIteratorContext _context;
    private final Map<String, Integer> _nameMap;
    private final Container _container;
    private final User _user;

    public DuplicatePlateValidator(DataIterator di, DataIteratorContext context, Container container, User user)
    {
        super(DataIteratorUtil.wrapMap(di, false));

        _context = context;
        _container = container;
        _nameMap = DataIteratorUtil.createColumnNameMap(di);
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

        String name = String.valueOf(_delegate.get(_nameMap.get("name")));
        Integer plateSet = (Integer)_delegate.get(_nameMap.get("plateSet"));
        if (name != null & plateSet != null)
        {
            PlateSet ps = PlateManager.get().getPlateSet(_container, plateSet);
            if (PlateManager.get().isDuplicatePlate(_container, _user, name, ps))
                _context.getErrors().addRowError(new ValidationException("Plate with name : " + name + " already exists."));
        }
        return true;
    }
}
