package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Created by Marty on 7/22/2016.
 */
public class Constraint
{
    private String name;
    private CONSTRAINT_TYPES type;
    private Collection<String> columns = new LinkedHashSet<String>();

    public Constraint() {}

    public Constraint(String name, CONSTRAINT_TYPES type, Collection<String> columns)
    {
        this.name = name;
        this.type = type;
        this.columns = columns;
    }

    public Constraint(String schemaName, String tableName, CONSTRAINT_TYPES type, Collection<String> columns)
    {
        this.type = type;
        this.columns = columns;
        this.name = type.getAbbrev() + "_" + schemaName + "_" + tableName + "_" + StringUtils.join(columns, "_");
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public CONSTRAINT_TYPES getType()
    {
        return type;
    }

    public void setType(CONSTRAINT_TYPES type)
    {
        this.type = type;
    }

    public Collection<String> getColumns()
    {
        return columns;
    }

    public void setColumns(Collection<String> columns)
    {
        this.columns = columns;
    }

    public void addColumn(String col)
    {
        this.columns.add(col);
    }

    public enum CONSTRAINT_TYPES
    {
        UNIQUE,
        PRIMARYKEY;

        String getAbbrev()
        {
            switch (this)
            {
                case UNIQUE:
                    return "UQ";
                case PRIMARYKEY:
                    return "PK";
            }

            return "";
        }

        @Override
        public String toString()
        {
            switch (this)
            {
                case UNIQUE:
                    return "UNIQUE";
                case PRIMARYKEY:
                    return "PRIMARY KEY";
            }

            return "";
        }


    }
}
