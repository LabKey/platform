package org.labkey.api.data;

/**
 * User: tgaluhn
 * Date: 10/12/2015
 *
 * Simple extension to SqlSelector for places we use sql queries to retrieve database metadata
 * instead of relying on jdbc method calls. Allows query profiling/tracking to be aware
 * the query is metadata related and should, e.g., bypass sql logging.
 */
public class MetadataSqlSelector extends SqlSelector
{
    public MetadataSqlSelector(DbScope scope, SQLFragment sql)
    {
        super(scope, sql, QueryLogging.metadataQueryLogging());
    }

    public MetadataSqlSelector(DbScope scope, CharSequence sql)
    {
        super(scope, new SQLFragment(sql), QueryLogging.metadataQueryLogging());
    }
}
