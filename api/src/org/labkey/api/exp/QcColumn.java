/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
 * User: jgarms
 * Date: Jan 9, 2009
 */
public class QcColumn extends LookupColumn
{
    public static final String QC_INDICATOR_SUFFIX = "QCIndicator";

    private final PropertyDescriptor pd;

    public QcColumn(PropertyDescriptor pd, TableInfo tinfoParent, String parentLsidColumn)
    {
        this(pd, tinfoParent.getColumn(parentLsidColumn));
    }

    public QcColumn(PropertyDescriptor pd, ColumnInfo lsidColumn)
    {
        super(lsidColumn, OntologyManager.getTinfoObject().getColumn("ObjectURI"), OntologyManager.getTinfoObjectProperty().getColumn("QcValue"), false);
        this.pd = pd;

        String name = ColumnInfo.legalNameFromName(pd.getName() + QC_INDICATOR_SUFFIX);
        setName(name);
        setAlias(name);
        setCaption(name);
        setNullable(true);
        setIsHidden(true);
        setUserEditable(false);
    }

    public SQLFragment getValueSql()
    {
        SQLFragment sql = new SQLFragment("\n(SELECT QcValue");

        sql.append("\nFROM exp.ObjectProperty WHERE exp.ObjectProperty.PropertyId = " + pd.getPropertyId());
        sql.append("\nAND exp.ObjectProperty.ObjectId = " + getTableAlias() + ".ObjectId)");

        return sql;
    }

    public SQLFragment getValueSql(String tableAlias)
    {
        return getValueSql();
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

    public String getTableAlias()
    {
        if (getContainerId() == null)
            return super.getTableAlias();
        return super.getTableAlias() + "_C";
    }
}
