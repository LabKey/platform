package org.labkey.study.importer;

import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.study.SpecimenImportStrategy;

import java.io.IOException;
import java.util.Map;

/**
* User: adam
* Date: 5/19/13
* Time: 4:48 PM
*/
class StandardSpecimenImportStrategy implements SpecimenImportStrategy
{
    private final Container _c;

    public StandardSpecimenImportStrategy(Container c)
    {
        _c = c;
    }

    @Override
    public org.labkey.api.util.Filter<Map<String, Object>> getImportFilter()
    {
        return null;
    }

    @Override
    public Filter getDeleteFilter()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Filter getInsertFilter()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void close() throws IOException
    {
    }
}
