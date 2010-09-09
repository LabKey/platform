/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.apache.log4j.Logger;
import org.labkey.api.data.Change;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;

/**
 * Created by IntelliJ IDEA.
 * User: newton
 * Date: Aug 11, 2010
 * Time: 2:52:33 PM
 */

public class ProvisioningService
{
    private static final Logger log = Logger.getLogger(ProvisioningService.class);


    public static void create(Domain domain) throws SQLException
    {

        Change change = new Change();
        change.setType(Change.ChangeType.CreateTable);
        change.setTableName(nameTable(domain));
        for (DomainProperty property : domain.getProperties())
        {
            change.addColumn(property.getPropertyDescriptor());
        }

        for (PropertyStorageSpec spec : domain.getDomainKind().getBaseProperties())
        {
            change.addColumn(spec);
        }

        String sql = DbScope.getLabkeyScope().getSqlDialect().getChangeStatement(change);
        Connection con = DbScope.getLabkeyScope().getConnection();
        con.prepareStatement(sql).execute();
        con.close();
    }

    public static void drop(Domain domain)
    {
        Change change = new Change();
        change.setType(Change.ChangeType.DropTable);
        change.setTableName(nameTable(domain));
        String sql = DbScope.getLabkeyScope().getSqlDialect().getChangeStatement(change);
        Connection con = null;
        try
        {
            con = DbScope.getLabkeyScope().getConnection();
            con.prepareStatement(sql).execute();
            con.close();
        }
        catch (SQLException e)
        {
            log.warn("Could not drop table for domain " + domain.getName(), e);
        }

    }

    public static void addProperties(Domain domain, Collection<DomainProperty> properties) throws SQLException
    {
        Change change = new Change();
        change.setType(Change.ChangeType.AddColumns);
        change.setTableName(nameTable(domain));
        for (DomainProperty prop : properties)
        {
           change.addColumn(prop.getPropertyDescriptor());
        }
        String sql = DbScope.getLabkeyScope().getSqlDialect().getChangeStatement(change);
        Connection con = DbScope.getLabkeyScope().getConnection();
        con.prepareStatement(sql).execute();
        con.close();
    }

    public static void dropProperties(Domain domain, Collection<DomainProperty> properties) throws SQLException
    {
        Change change = new Change();
        change.setType(Change.ChangeType.DropColumns);
        change.setTableName(nameTable(domain));
        for (DomainProperty prop : properties)
        {
           change.addColumn(prop.getPropertyDescriptor());
        }
        String sql = DbScope.getLabkeyScope().getSqlDialect().getChangeStatement(change);
        Connection con = DbScope.getLabkeyScope().getConnection();
        con.prepareStatement(sql).execute();
    }

    private static String nameTable(Domain domain)
    {
        return "foo" + domain.getName(); // TODO guarantee legal unique name for table
    }

}
