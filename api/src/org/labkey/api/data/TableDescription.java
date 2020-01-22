package org.labkey.api.data;


import java.util.List;

/* This is a small subset of TableInfo that gives only basic information about a table, this might be provided by xml or a foreignkey.
 * Importantly, it does not require generating any column lists or ColumnInfo objects.
 */
public interface TableDescription
{
    boolean isPublic();

    String getPublicName();

    // TODO replace with (or add) getPublicSchemaKey()
    String getPublicSchemaName();

    String getName();

    /* TODO this is called by JsonWriter, but is this actually useful? */
    String getSchemaName();  // same as ((TableInfo)this).getSchema().getName()

    String getTitleColumn();

    List<String> getPkColumnNames();
}
