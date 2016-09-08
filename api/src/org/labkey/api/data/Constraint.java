package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Created by Marty on 7/22/2016.
 */
public class Constraint
{
    private String name;
    private CONSTRAINT_TYPES type;
    private boolean cluster;
    private Collection<String> columns = new LinkedHashSet<>();

    public Constraint(@NotNull String tableName, @NotNull CONSTRAINT_TYPES type, boolean cluster, @Nullable Collection<String> columns)
    {
        this.type = type;
        this.columns = columns;
        this.cluster = cluster;

        switch(type)
        {
            case UNIQUE:
                this.name = type.getAbbrev() + "_" + tableName +
                        ((columns!=null && !columns.isEmpty())?("_" + StringUtils.join(columns, "_")):"");
                break;
            case PRIMARYKEY:
                this.name = tableName + "_" + type.getAbbrev();
                break;
        }
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

    public boolean isCluster()
    {
        return cluster;
    }

    public void setCluster(boolean cluster)
    {
        this.cluster = cluster;
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

        public String getAbbrev()
        {
            switch (this)
            {
                case UNIQUE:
                    return "UQ";
                case PRIMARYKEY:
                    return "pk";
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
