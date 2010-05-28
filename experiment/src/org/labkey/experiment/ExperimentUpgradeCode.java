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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.experiment.api.ExpMaterialTableImpl;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 2:19:04 PM
 */
public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(ExperimentUpgradeCode.class);

    // Invoked at version 10.2
    public void version_10_2_upgrade(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall())
        {
            try
            {
                doVersion_10_2_upgrade();
            }
            catch (Exception e)
            {
                UnexpectedException.rethrow(e);
            }
        }
    }

    private void doVersion_10_2_upgrade() throws SQLException, NamingException, ServletException
    {
        DbSchema exp = DbSchema.createFromMetaData("exp");
        _log.info("Renaming SampleSet properties...");

        // Get the SampleSets with a single idCol of property 'Name'
        String sampleSetsToUpgrade =
                "SELECT ms.RowId from exp.MaterialSource ms, exp.PropertyDescriptor pd\n" +
                "WHERE\n" +
                "  ms.idCol2 IS NULL\n" +
                "  AND ms.idCol3 IS NULL\n" +
                "  AND ms.idCol1 = pd.PropertyUri\n" +
                "  AND pd.Name " + exp.getSqlDialect().getCaseInsensitiveLikeOperator() + " 'Name'";
        Integer[] ids = Table.executeArray(exp, sampleSetsToUpgrade, new Object[]{}, Integer.class);
        _log.info("Found " + ids.length + " sample sets with idCol1 of 'Name'");

        List<Integer> sampleSetIds = new ArrayList<Integer>();
        sampleSetIds.addAll(Arrays.asList(ids));

        
        // For the SampleSets above, ensure the Material's Name and the value in the objectproperty table are the same.
        // This shouldn't happen, but let's be paranoid.
        String ensureNameValues =
                "SELECT ms.RowId AS SampleSetRowId, m.RowId AS MaterialRowId, m.Name, op.StringValue\n" +
                "FROM exp.MaterialSource ms, exp.Material m, exp.PropertyDescriptor pd, exp.ObjectProperty op, exp.Object o\n" +
                "WHERE\n" +
                "  ms.RowId IN (" + StringUtils.join(sampleSetIds, ", ") + ")\n" +
                "  AND ms.idCol1 = pd.PropertyUri\n" +
                "  AND pd.PropertyId = op.PropertyId\n" +
                "  AND ms.Lsid = m.CpasType\n" +
                "  AND m.Lsid = o.ObjectUri\n" +
                "  AND o.ObjectId = op.ObjectId\n" +
                "  AND m.Name != op.StringValue";
        Map[] badMaterials = Table.executeQuery(exp, ensureNameValues, new Object[] { }, Map.class);
        for (Map material : badMaterials)
        {
            Integer sampleSetId = (Integer)material.get("SampleSetRowId");
            sampleSetIds.remove(sampleSetId);

            StringBuilder sb = new StringBuilder();
            sb.append("Expected material name to be the same as the idCol1 value:");
            sb.append(" sampleset rowid=").append(sampleSetId);
            sb.append(", material rowid=").append(material.get("MaterialRowId"));
            sb.append(", name=").append(material.get("name"));
            sb.append(", idCol1 value=").append(material.get("stringvalue"));
            _log.warn(sb.toString());
        }


        // For the Materials in the SampleSets above, delete the 'Name' values
        String deleteName =
                "DELETE FROM exp.objectproperty WHERE EXISTS (\n" +
                "  SELECT * FROM (\n" +
                "    SELECT op.ObjectId, op.PropertyId\n" +
                "    FROM exp.materialsource ms, exp.material m, exp.propertydescriptor pd, exp.objectproperty op, exp.object o\n" +
                "    WHERE\n" +
                "      ms.RowId IN (" + StringUtils.join(sampleSetIds, ", ") + ")\n" +
                "      AND ms.idCol1 = pd.propertyuri\n" +
                "      AND pd.propertyid = op.propertyid\n" +
                "      AND ms.lsid = m.cpastype\n" +
                "      AND m.lsid = o.objecturi\n" +
                "      AND o.objectid = op.objectid\n" +
                "  ) x\n" +
                "  WHERE x.ObjectId = exp.objectproperty.ObjectId AND x.PropertyId = exp.objectproperty.PropertyId\n" +
                ")";
        Table.execute(exp, deleteName, new Object[] { });

        
        // For the SampleSets above, remove the idCol1 property from the SampleSet's domain
        String deleteIdCol1 =
                "DELETE FROM exp.PropertyDomain WHERE EXISTS (\n" +
                "  SELECT * FROM (\n" +
                "    SELECT dp.PropertyId, dp.DomainId\n" +
                "    FROM exp.MaterialSource ms, exp.DomainDescriptor dd, exp.PropertyDomain dp, exp.PropertyDescriptor pd\n" +
                "    WHERE\n" +
                "      ms.RowId IN (" + StringUtils.join(sampleSetIds, ", ") + ")\n" +
                "      AND ms.Lsid = dd.DomainUri\n" +
                "      AND dd.DomainId = dp.DomainId\n" +
                "      AND ms.idCol1 = pd.PropertyUri\n" +
                "      AND pd.PropertyId = dp.PropertyId\n" +
                "  ) x\n" +
                "  WHERE x.PropertyId = exp.PropertyDomain.PropertyId and x.DomainId = exp.PropertyDomain.DomainId\n" +
                ")";
        Table.execute(exp, deleteIdCol1, new Object[] { });


        // For the SampleSets above, delete the idCol1 PropertyDescriptor
        String deleteIdCol1Property =
                "DELETE FROM exp.PropertyDescriptor\n" +
                "WHERE PropertyURI IN (\n" +
                "  SELECT ms.idCol1\n" +
                "  FROM exp.MaterialSource ms\n" +
                "  WHERE\n" +
                "    ms.RowId IN (" + StringUtils.join(sampleSetIds, ", ") + ")\n" +
                ")";
        Table.execute(exp, deleteIdCol1Property, new Object[] { });


        // For the SampleSets above, set the idCol1 property to 'Name'
        String updateIdCol1 =
                "UPDATE exp.materialsource SET idCol1 = '" + ExpMaterialTableImpl.Column.Name.name() + "'\n" +
                "WHERE exp.materialsource.rowId IN (" + StringUtils.join(sampleSetIds, ", ") + ")\n";
        Table.execute(exp, updateIdCol1, new Object[] { });


        // Finally, add a 'Property_' prefix to any properties in any SampleSet domain that collide with built-in ExpMaterialTableImpl columns
        List<String> reservedNames = new ArrayList<String>(ExpMaterialTable.Column.values().length);
        for (ExpMaterialTable.Column column : ExpMaterialTable.Column.values())
            reservedNames.add("'" + column.name().toLowerCase() + "'");
        reservedNames.add("'cpastype'");

        String updateProperties =
                "UPDATE exp.PropertyDescriptor\n" +
                "  SET Label = Name, Name = 'Property_' " + exp.getSqlDialect().getConcatenationOperator() + " Name\n" +
                "WHERE PropertyId IN (\n" +
                "  SELECT pd.PropertyId\n" +
                "    FROM exp.PropertyDescriptor pd, exp.PropertyDomain dp, exp.MaterialSource ms, exp.DomainDescriptor dd\n" +
                "    WHERE ms.Lsid=dd.DomainUri\n" +
                "    AND dd.DomainId=dp.DomainId\n" +
                "    AND pd.PropertyId=dp.PropertyId\n" +
                "    AND pd.Container=dd.Container\n" +
                "    AND lower(pd.Name) in (" + StringUtils.join(reservedNames, ", ") + ")\n" +
                ")";
        int updatedPropertyCount = Table.execute(exp, updateProperties, new Object[] { });
        _log.info("Renamed " + updatedPropertyCount + " SampleSet properties.");
    }

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
