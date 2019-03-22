/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Models a database constraint on a particular table, such as a primary key or UNIQUE constraint.
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
        UNIQUE
        {
            @Override
            public String getAbbrev()
            {
                return "UQ";
            }

            @Override
            public String toString()
            {
                return "UNIQUE";
            }
        },
        PRIMARYKEY
        {
            @Override
            public String getAbbrev()
            {
                return "pk";
            }

            @Override
            public String toString()
            {
                return "PRIMARY KEY";
            }
        };

        public abstract String getAbbrev();
    }
}
