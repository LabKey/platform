package org.labkey.di.pipeline;

import org.labkey.etl.xml.EtlType;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class ETLDescriptor
{
    private String _name;

    public ETLDescriptor(EtlType etlType)
    {
        _name = etlType.getName();
    }

    public String getName()
    {
        return _name;
    }

    @Override
    public String toString()
    {
        return "ETLDescriptor: " + _name;
    }
}
