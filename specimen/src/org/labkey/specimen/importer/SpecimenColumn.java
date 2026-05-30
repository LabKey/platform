/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen.importer;

import org.labkey.api.util.TimeOnlyDate;

import java.util.Collection;
import java.util.Collections;

public class SpecimenColumn extends ImportableColumn
{
    private final TargetTable _targetTable;

    private String _fkTable;
    private String _joinType;
    private String _fkColumn;
    private String _aggregateEventFunction;
    private boolean _isKeyColumn = false;

    public SpecimenColumn(String tsvColumnName, Collection<String> tsvColumnAliases, String dbColumnName, String databaseType, TargetTable eventColumn, boolean unique)
    {
        super(tsvColumnName, tsvColumnAliases, dbColumnName, databaseType, unique, false);
        _targetTable = eventColumn;
    }

    public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn, boolean unique)
    {
        this(tsvColumnName, Collections.emptyList(), dbColumnName, databaseType, eventColumn, unique);
    }

    public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, boolean isKeyColumn, TargetTable eventColumn, boolean unique)
    {
        this(tsvColumnName, dbColumnName, databaseType, eventColumn, unique);
        _isKeyColumn = isKeyColumn;
    }

    public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, boolean isKeyColumn, TargetTable eventColumn)
    {
        this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
        _isKeyColumn = isKeyColumn;
    }

    public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn)
    {
        this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
    }

    public SpecimenColumn(String tsvColumnName, Collection<String> tsvColumnAliases, String dbColumnName, String databaseType, TargetTable eventColumn)
    {
        this(tsvColumnName, tsvColumnAliases, dbColumnName, databaseType, eventColumn, false);
    }

    public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType, TargetTable eventColumn, String aggregateEventFunction)
    {
        this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
        _aggregateEventFunction = aggregateEventFunction;
    }

    public SpecimenColumn(String tsvColumnName, String dbColumnName, String databaseType,
                          TargetTable eventColumn, String fkTable, String fkColumn, String joinType)
    {
        this(tsvColumnName, dbColumnName, databaseType, eventColumn, false);
        _fkColumn = fkColumn;
        _fkTable = fkTable;
        _joinType = joinType;
    }

    public TargetTable getTargetTable()
    {
        return _targetTable;
    }

    public String getFkColumn()
    {
        return _fkColumn;
    }

    public String getFkTable()
    {
        return _fkTable;
    }

    public String getJoinType()
    {
        return _joinType;
    }

    public String getDbType()
    {
        return _dbType;
    }

    public String getAggregateEventFunction()
    {
        return _aggregateEventFunction;
    }

    public boolean isKeyColumn()
    {
        return _isKeyColumn;
    }

    public String getFkTableAlias()
    {
        return getDbColumnName() + "Lookup";
    }

    public boolean isDateType()
    {
        return getDbType() != null && (getDbType().equals("DATETIME") || getDbType().equals("TIMESTAMP")) && !getJavaClass().equals(TimeOnlyDate.class);
    }
}
