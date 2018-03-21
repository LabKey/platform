package org.labkey.api.data;

import org.labkey.api.query.SchemaKey;

import java.util.Set;

public interface HasResolvedTables
{
    /**
     * Return the resolved tables for this table or query. The SchemaKey will be in the format of schema/table name
     */
    Set<SchemaKey> getResolvedTables();
}
