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
package org.labkey.study;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.UnexpectedException;
import org.labkey.study.assay.AssayManager;
import org.labkey.study.model.DataSetDefinition;

import javax.servlet.ServletContext;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 4:54:36 PM
 */
public class StudyUpgradeCode implements UpgradeCode
{
    // Invoked at version 8.38
    @SuppressWarnings({"UnusedDeclaration"})
    public static void upgradeMissingProtocols(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall() || moduleContext.getInstalledVersion() >= 8.38)
            return;
        Table.TableResultSet rs = null;
        DbSchema schema = StudySchema.getInstance().getSchema();
        DbScope scope = schema.getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        try
        {
            if (transactionOwner)
                scope.beginTransaction();

            String datasetSql = "SELECT sd.datasetid, sd.container\n" +
                    "FROM study.studydata sd\n" +
                    "WHERE sd.sourcelsid IS NOT NULL\n" +
                    "GROUP BY datasetid, container";

            rs = Table.executeQuery(schema, datasetSql, null);
            // container id to dataset id
            MultiMap<String, Integer> container2datasets = new MultiHashMap<String,Integer>();
            while (rs.next())
            {
                Map<String,Object> rowMap = rs.getRowMap();

                container2datasets.put((String)rowMap.get("container"), (Integer)rowMap.get("datasetid"));
            }
            rs.close();

            // Need to loop over all the containers, as we have a constraint that the same protocol can
            // only have one entry per container.
            for (Map.Entry<String, Collection<Integer>> entry : container2datasets.entrySet())
            {
                String container = entry.getKey();
                Map<Integer,Integer> protocol2dataset = new HashMap<Integer,Integer>(); // only one per container
                for (Integer datasetId : entry.getValue())
                {
                    SQLFragment protocolSql = new SQLFragment("SELECT MAX(p.rowid)\n" +
                            "FROM study.studydata sd, exp.experimentrun er, exp.protocol p\n" +
                            "WHERE sd.datasetid = ?\n" +
                            "AND sd.sourcelsid = er.lsid\n" +
                            "AND er.protocollsid = p.lsid\n" +
                            "AND sd.container = ?\n" +
                            "AND sd.sourcelsid IS NOT NULL", datasetId, container);
                    rs = Table.executeQuery(schema, protocolSql);
                    boolean foundRowId = rs.next();
                    Integer protocolId = foundRowId ? (Integer)rs.getObject(1) : null;
                    rs.close();
                    if (protocolId == null)
                        continue;

                    Integer previousDatasetId = protocol2dataset.get(protocolId);
                    if (previousDatasetId == null || previousDatasetId < datasetId)
                        protocol2dataset.put(protocolId, datasetId);
                }
                // Update the datasets
                for (Map.Entry<Integer,Integer> protocolAndDatasetId : protocol2dataset.entrySet())
                {
                    Integer protocolId = protocolAndDatasetId.getKey();
                    Integer datasetId = protocolAndDatasetId.getValue();
                    // first check that there isn't already a dataset with that protocol in there
                    SQLFragment checkSql = new SQLFragment(
                            "SELECT d.datasetid\n" +
                            "FROM study.dataset d\n" +
                            "WHERE protocolid = ?\n" +
                            "and container = ?", protocolId, container);
                    rs = Table.executeQuery(schema, checkSql);
                    boolean needsUpgrade = !rs.next();
                    rs.close();
                    if (needsUpgrade)
                    {
                        SQLFragment updateSql = new SQLFragment("UPDATE study.dataset SET protocolId = ?\n" +
                                "WHERE\n" +
                                "protocolId IS NULL\n" +
                                "AND\n" +
                                "container = ?\n" +
                                "AND\n" +
                                "datasetid = ?",
                                protocolId, container, datasetId);

                        Table.execute(schema, updateSql);
                    }
                }
            }
            if (transactionOwner)
                scope.commitTransaction();
        }
        catch (SQLException se)
        {
            throw UnexpectedException.wrap(se);
        }
        finally
        {
            if (rs != null) try {rs.close();} catch (SQLException se) {}
            if (transactionOwner)
                scope.closeConnection();
        }
    }


    /* called at 10.20->10.21 */
    public void materializeDatasets(ModuleContext moduleContext)
    {
        try
        {
            DataSetDefinition.upgradeAll();
        }
        catch (SQLException se)
        {
            throw UnexpectedException.wrap(se);
        }
    }

    /** called at 10.30->10.31 */
    public void materializeAssayResults(final ModuleContext moduleContext)
    {
        // This needs to happen later, after all of the AssayProviders have been registered
        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                upgradeAssayResults(moduleContext.getUpgradeUser(), ContainerManager.getRoot());
            }
        });
    }

    /** Recurse through the container tree, upgrading any assay protocols that live there */ 
    private void upgradeAssayResults(User user, Container c)
    {
        try
        {
            for (ExpProtocol protocol : ExperimentService.get().getExpProtocols(c))
            {
                AssayProvider provider = AssayManager.get().getProvider(protocol);
                if (provider != null)
                {
                    // Upgrade is AssayProvider dependent
                    provider.materializeAssayResults(user, protocol);
                }
            }

            // Recurse through the children
            for (Container child : c.getChildren())
            {
                upgradeAssayResults(user, child);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
