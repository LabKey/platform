/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.exp;

import org.labkey.api.data.*;


/**
 * Missing Value (MV) indicator column. Used to annotate another column, which might otherwise want to disallow null
 * values, as being legitimately unavailable for some reason.
 *
 * User: jgarms
 * Date: Jan 9, 2009
 */
public class MvColumn extends LookupColumn
{
    public static final String MV_INDICATOR_SUFFIX = "MVIndicator";

    private final PropertyDescriptor pd;

    public MvColumn(PropertyDescriptor pd, TableInfo tinfoParent, String parentLsidColumn)
    {
        this(pd, tinfoParent.getColumn(parentLsidColumn));
    }

    public MvColumn(PropertyDescriptor pd, ColumnInfo lsidColumn)
    {
        super(lsidColumn, OntologyManager.getTinfoObject().getColumn("ObjectURI"), OntologyManager.getTinfoObjectProperty().getColumn("MvIndicator"));
        this.pd = pd;

        String name = ColumnInfo.legalNameFromName(pd.getName() + MV_INDICATOR_SUFFIX);
        setName(name);
        setAlias(name);
        setLabel(name);
        setNullable(true);
        setHidden(true);
        setUserEditable(false);
        setMvIndicatorColumn(true);
    }

    public SQLFragment getValueSql(String tableAlias)
    {
        SQLFragment sql = new SQLFragment("\n(SELECT MvIndicator");

        sql.append("\nFROM exp.ObjectProperty WHERE exp.ObjectProperty.PropertyId = " + pd.getPropertyId());
        sql.append("\nAND exp.ObjectProperty.ObjectId = " + getTableAlias(tableAlias) + ".ObjectId)");

        return sql;
    }

    public PropertyDescriptor getPropertyDescriptor()
    {
        return pd;
    }

    public String getPropertyURI()
    {
        return getPropertyDescriptor().getPropertyURI();
    }

    private String getContainerId()
    {
        Container c = pd.getContainer();
        if (c != null)
            return c.getId();
        return null;
    }

    public SQLFragment getJoinCondition(String tableAliasName)
    {
        SQLFragment strJoinNoContainer = super.getJoinCondition(tableAliasName);
        String containerId = getContainerId();
        if (containerId == null)
        {
            return strJoinNoContainer;
        }

        strJoinNoContainer.append(" AND " + tableAliasName + ".Container = '" + containerId + "'");
        return strJoinNoContainer;
    }

    public String getTableAlias(String baseAlias)
    {
        if (getContainerId() == null)
            return super.getTableAlias(baseAlias);
        return super.getTableAlias(baseAlias) + "_C";
    }
}
