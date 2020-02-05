package org.labkey.api.data;


import java.util.List;

/* This is a small subset of TableInfo that gives only basic information about a table, this might be provided by xml or a foreignkey.
 * Importantly, it does not require generating any column lists or ColumnInfo objects.
 */
public interface TableDescription
{
    boolean isPublic();

    String getPublicName();

    // CONSIDER replace with (or add) getPublicSchemaKey()
    /** @return The public (queryable) schema name in SchemaKey encoding. */
    String getPublicSchemaName();

    String getName();

    /** @return the default display value for this table if it's the target of a foreign key */
    String getTitleColumn();

    List<String> getPkColumnNames();
}
