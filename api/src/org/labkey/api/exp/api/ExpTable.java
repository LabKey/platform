package org.labkey.api.exp.api;

import org.labkey.api.data.*;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.exp.PropertyDescriptor;

import java.util.List;

abstract public interface ExpTable<C extends Enum> extends TableInfo
{
    static public final String COLUMN_DATAINPUT_DATAID = "exp.datainput.dataid";
    public void setContainer(Container container);
    public Container getContainer();


    public ColumnInfo addColumn(C column);
    public ColumnInfo addColumn(String alias, C column);
    public ColumnInfo getColumn(C column);
    public ColumnInfo createColumn(String alias, C column);
    public ColumnInfo addColumn(ColumnInfo column);
    // Adds a column so long as there is not already one of that name.
    public boolean safeAddColumn(ColumnInfo column);
    public void addMethod(String name, MethodInfo method);
    public void setTitleColumn(String titleColumn);

    /**
     * Add a column which can later have its ForeignKey set to be such that the column displays a property value.
     * The returned could will have the integer value of the ObjectId.
     */
    public ColumnInfo createPropertyColumn(String alias);

    public void addCondition(SQLFragment condition, String... columnNames);
    public void addRowIdCondition(SQLFragment rowidCondition);
    public void addLSIDCondition(SQLFragment lsidCondition);

    public void setDetailsURL(DetailsURL detailsURL);
    public void setDefaultVisibleColumns(Iterable<FieldKey> columns);

    public void setEditHelper(TableEditHelper helper);

    public ColumnInfo addPropertyColumns(String domainDescription, PropertyDescriptor[] pds, QuerySchema schema);
}
