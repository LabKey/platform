package org.labkey.api.data;

import org.labkey.api.exp.PropertyDescriptor;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: newton
 * Date: Aug 19, 2010
 * Time: 5:14:04 PM
 */
public class Change
{
    ChangeType type;
    String tableName;
    Collection<PropertyStorageSpec> columns = new ArrayList<PropertyStorageSpec>();

    public ChangeType getType()
    {
        return type;
    }

    public void setType(ChangeType type)
    {
        this.type = type;
    }

    public String getTableName()
    {
        return tableName;
    }

    public void setTableName(String tableName)
    {
        this.tableName = tableName;
    }

    public Collection<PropertyStorageSpec> getColumns()
    {
        return columns;
    }

    public void addColumn(PropertyStorageSpec prop)
    {
        columns.add(prop);
    }

    public void addColumn(PropertyDescriptor prop)
    {
        addColumn(new PropertyStorageSpec(prop));
    }
    
    public enum ChangeType
    {
        CreateTable,
        DropTable,
        AddColumns,
        DropColumns
    }


}
