package org.labkey.api.exp.api;

import java.util.List;
import java.sql.SQLException;

import org.labkey.api.security.User;

public class ProtocolImplementation
{
    final protected String _name;
    public ProtocolImplementation(String name)
    {
        _name = name;
    }

    public String getName()
    {
        return _name;
    }

    /**
     * Called when samples in a sample set have one or more properties modified.  Also called when new samples are
     * created (uploaded).  This is not called when samples are deleted.
     * @param protocol whose {@link org.labkey.api.exp.property.ExperimentProperty#SampleSetLSID} property
     * is the sampleset that these samples came from.
     * @param materials materials that were modified.
     */
    public void onSamplesChanged(User user, ExpProtocol protocol, ExpMaterial[] materials) throws SQLException
    {

    }

    public boolean deleteRunWhenInputDeleted()
    {
        return false;
    }
}
