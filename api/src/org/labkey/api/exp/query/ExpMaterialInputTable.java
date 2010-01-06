package org.labkey.api.exp.query;

/**
 * Table for material usages in runs
 * User: jeckels
 * Date: Jan 5, 2010
 */
public interface ExpMaterialInputTable extends ExpInputTable<ExpMaterialInputTable.Column>
{
    enum Column
    {
        Material,
        TargetProtocolApplication,
        Role,
    }
}
