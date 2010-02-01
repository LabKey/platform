/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.experiment;

import org.apache.log4j.Logger;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.ExceptionUtil;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 2:19:04 PM
 */
public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(ExperimentUpgradeCode.class);

    // Invoked at version 8.23
    @SuppressWarnings({"ThrowableInstanceNeverThrown", "UnusedDeclaration"})
    public void version132Upgrade(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall())
        {
            try
            {
                doVersion_132Update();
            }
            catch (Exception e)
            {
                String msg = "Error running doVersion_132Update on ExperimentModule, upgrade from version " + String.valueOf(moduleContext.getInstalledVersion());
                _log.error(msg, e);
                ExceptionUtil.getErrorRenderer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e, null, false, false);
            }
        }
    }

    private void doVersion_132Update() throws SQLException, NamingException, ServletException
    {
        DbSchema tmpSchema = DbSchema.createFromMetaData("exp");
        doProjectColumnUpdate(tmpSchema, "exp.PropertyDescriptor");
        doProjectColumnUpdate(tmpSchema, "exp.DomainDescriptor");
        alterDescriptorTables(tmpSchema);
    }

    private void doProjectColumnUpdate(DbSchema tmpSchema, String descriptorTable) throws SQLException
    {
        String sql = "SELECT DISTINCT(Container) FROM " + descriptorTable + " WHERE Project IS NULL ";
        String[] cids = Table.executeArray(tmpSchema, sql, new Object[]{}, String.class);
        String projectId;
        String newContainerId;
        String rootId = ContainerManager.getRoot().getId();
        for (String cid : cids)
        {
            newContainerId = cid;
            if (cid.equals(rootId) || cid.equals("00000000-0000-0000-0000-000000000000"))
                newContainerId = ContainerManager.getSharedContainer().getId();
            projectId = ContainerManager.getForId(newContainerId).getProject().getId();
            setDescriptorProject(tmpSchema, cid, projectId, newContainerId, descriptorTable);
        }
    }

    private void setDescriptorProject(DbSchema tmpSchema, String containerId, String projectId, String newContainerId, String descriptorTable) throws SQLException
    {
        String sql = " UPDATE " + descriptorTable + " SET Project = ?, Container = ? WHERE Container = ? ";
        Table.execute(tmpSchema, sql, new Object[]{projectId, newContainerId, containerId});
    }

    private void alterDescriptorTables(DbSchema tmpSchema) throws SQLException
    {
        String indexOption = " ";
        String keywordNotNull = " ENTITYID ";

        if (tmpSchema.getSqlDialect().isSqlServer())
            indexOption = " CLUSTERED ";

        if (tmpSchema.getSqlDialect().isPostgreSQL())
            keywordNotNull = " SET ";

        String sql = " ALTER TABLE exp.PropertyDescriptor ALTER COLUMN Project " + keywordNotNull + " NOT NULL ;";
        Table.execute(tmpSchema, sql, new Object[]{});

        try
        {
            sql = " ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE " + indexOption + " (Project, PropertyURI);" ;
            Table.execute(tmpSchema, sql, new Object[]{});
        }
        catch (SQLException ex)
        {
            // 42P07 DUPLICATE TABLE (pgsql)
            // 42S11 Index already exists (mssql)
            // S0001 sql server bug?
            if (!"42P07".equals(ex.getSQLState()) && !"42S11".equals(ex.getSQLState()) && !"S0001".equals(ex.getSQLState()))
                throw ex;
        }
        sql = " ALTER TABLE exp.DomainDescriptor ALTER COLUMN Project " + keywordNotNull + " NOT NULL ;";
        Table.execute(tmpSchema, sql, new Object[]{});

        try
        {
            sql = " ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainDescriptor UNIQUE " + indexOption + " (Project, DomainURI);"  ;
            Table.execute(tmpSchema, sql, new Object[]{});
        }
        catch (SQLException ex)
        {
            if (!"42P07".equals(ex.getSQLState()) && !"42S11".equals(ex.getSQLState()) && !"S0001".equals(ex.getSQLState()))
                throw ex;
        }
    }
}
