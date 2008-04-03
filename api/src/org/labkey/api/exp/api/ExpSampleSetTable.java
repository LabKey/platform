package org.labkey.api.exp.api;

/**
 * User: jeckels
 * Date: Oct 17, 2007
 */
public interface ExpSampleSetTable extends ExpTable<ExpSampleSetTable.Column>
{
    enum Column
    {
        RowId,
        LSID,
        Name,
        Description,
        MaterialLSIDPrefix,
        Created,
        Modified,
        Container,
    }

    void populate(ExpSchema schema);

}
