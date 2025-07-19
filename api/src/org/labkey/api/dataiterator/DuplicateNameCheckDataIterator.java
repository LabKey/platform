package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DuplicateNameCheckDataIterator extends WrapperDataIterator
{
    final static String NAME_FIELD = "name";
    private final DataIteratorContext _context;
    private final Integer _nameCol;
    private final Integer _lsidCol;
    private final TableInfo _tableInfo;
    private final boolean _isUpdateUsingLsid;
    final CachingDataIterator _unwrapped;
    int lastPrefetchRowNumber = -1;

    public DuplicateNameCheckDataIterator(DataIterator di, DataIteratorContext context, boolean isUpdateUsingLsid, TableInfo tableInfo)
    {
        super(di);
        this._unwrapped = (CachingDataIterator)di;
        _context = context;
        _tableInfo = tableInfo;
        _isUpdateUsingLsid = isUpdateUsingLsid;
        Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
        _nameCol = map.get(NAME_FIELD);
        _lsidCol = map.get("lsid");
    }

    protected void checkNames() throws BatchValidationException
    {
        Integer rowNumber = (Integer)_delegate.get(0);
        if (rowNumber <= lastPrefetchRowNumber)
            return;

        String duplicateName = null;
        int rowsToFetch = 50;
        Set<String> names = new HashSet<>();
        Map<String, String> nameLsidMap = new LinkedHashMap<>();
        do
        {
            lastPrefetchRowNumber = (Integer) _delegate.get(0);

           if (_nameCol == null)
               continue;

            Object nameObj = get(_nameCol);
            if (nameObj == null)
                continue;

            String newName = String.valueOf(nameObj);
            if (StringUtils.isEmpty(newName))
                continue;

            Map<String, Object> existingValues = getExistingRecord();
            if (existingValues != null  && !existingValues.isEmpty() && existingValues.get(NAME_FIELD).equals(newName))
                continue;

            if (names.contains(newName) && duplicateName == null)
                duplicateName = newName; // reject exact case match
            else
                names.add(newName);

            if (_isUpdateUsingLsid && _lsidCol != null)
            {
                Object lsidObj = get(_lsidCol);
                if (lsidObj != null)
                {
                    String lsid = String.valueOf(lsidObj);
                    if (!StringUtils.isEmpty(lsid))
                        nameLsidMap.put(newName, lsid);
                }
            }
        }
        while (--rowsToFetch > 0 && _delegate.next());

        if (!names.isEmpty() && duplicateName == null)
        {
            if (!_context.getInsertOption().allowUpdate) // insert new
            {
                duplicateName = ExperimentService.get().getDuplicateNewOrExistingNames(names, _tableInfo, false);
            }
            else if (_isUpdateUsingLsid && _lsidCol != null) // update using rowId is not yet supported for DIB
            {
                Set<String> newOrExistingNamesLc = new HashSet<>();
                for (String name : nameLsidMap.keySet())
                {
                    String newOrExistingNameLc = name.toLowerCase();
                    if (newOrExistingNamesLc.contains(newOrExistingNameLc))
                    {
                        duplicateName = name;
                        break;
                    }
                    newOrExistingNamesLc.add(newOrExistingNameLc);
                }

                for (String name : nameLsidMap.keySet())
                {
                    if (!ExperimentService.get().canRename(nameLsidMap.get(name), name, _tableInfo))
                    {
                        duplicateName = name;
                        break;
                    }
                }
            }
            else if (_context.getInsertOption().mergeRows) // merge
            {
                duplicateName = ExperimentService.get().getDuplicateNewOrExistingNames(names, _tableInfo, true);
            }
        }

        if (duplicateName != null)
        {
            String error = String.format("The name '%s' already exists.", duplicateName);
            if (_context.getInsertOption().mergeRows)
                error = String.format("The name '%s' could not be resolved. Please check the casing of the provided name.", duplicateName);
            _context.getErrors().addRowError(new ValidationException(error));
        }


        // backup to where we started so caller can iterate through them one at a time
        _unwrapped.reset(); // unwrapped _delegate
        _delegate.next();
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        // NOTE: we have to call mark() before we call next() if we want the 'next' row to be cached
        _unwrapped.mark();  // unwrapped _delegate
        boolean ret = super.next();

        if (ret)
            checkNames();

        return ret;
    }

}
