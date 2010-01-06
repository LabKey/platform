package org.labkey.api.exp.query;

import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;

/**
 * User: jeckels
 * Date: Jan 4, 2010
 */
public interface ExpDataInputTable extends ExpInputTable<ExpDataInputTable.Column>
{
    enum Column
    {
        Data,
        TargetProtocolApplication,
        Role,
    }
}
