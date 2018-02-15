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
package org.labkey.experiment;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.Collection;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/27/16
 */
public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = Logger.getLogger(ExperimentUpgradeCode.class);

    /** Called from exp-16.31-16.32.sql */
    public static void cleanupQuotedAliases(ModuleContext context) throws Exception
    {
        if (context.isNewInstall())
            return;

        DbSchema schema = ExperimentService.get().getSchema();
        try (DbScope.Transaction tx = schema.getScope().beginTransaction())
        {
            TableInfo aliasTable = ExperimentService.get().getTinfoAlias();
            TableInfo dataAliasMapTable = ExperimentService.get().getTinfoDataAliasMap();
            TableInfo materialAliasMapTable = ExperimentService.get().getTinfoMaterialAliasMap();

            // get map of alias name to rowId
            Map<String, Integer> aliases = new TableSelector(aliasTable, aliasTable.getColumns("name", "rowId"), null, null).getValueMap();

            // for each alias, if it is enclosed in single or double quotes
            // - remove the quotes
            // - if the unquoted alias already exists,
            //     - find any objects that have a reference to both the quoted and unquoted alias
            //         - remove the quoted alias reference
            //     - update all remaining references to the quoted alias to the unquoted alias
            //     - delete the quoted alias
            // - otherwise
            //     - change the quoted alias to the newly unquoted alias
            for (String quotedAliasName : aliases.keySet())
            {
                if ((quotedAliasName.startsWith("\"") && quotedAliasName.endsWith("\"")) ||
                    (quotedAliasName.startsWith("'") && quotedAliasName.endsWith("'")))
                {
                    Integer quotedAliasRowId = aliases.get(quotedAliasName);

                    // remove quotes
                    String unquotedAliasName = quotedAliasName.substring(1, quotedAliasName.length()-1);

                    // check if it already exists
                    Integer unquotedAliasRowId = aliases.get(unquotedAliasName);
                    if (unquotedAliasRowId != null)
                    {
                        fixupAliasReferences(dataAliasMapTable, quotedAliasRowId, unquotedAliasRowId);
                        fixupAliasReferences(materialAliasMapTable, quotedAliasRowId, unquotedAliasRowId);

                        // delete the quoted alias
                        Table.delete(aliasTable, new SimpleFilter("rowid", quotedAliasRowId));
                    }
                    else
                    {
                        // update the quoted alias name to the unquoted string
                        Table.update(null, aliasTable, CaseInsensitiveHashMap.of("name", unquotedAliasName), quotedAliasRowId);
                    }
                }
            }

            tx.commit();
        }
    }

    private static void fixupAliasReferences(TableInfo aliasMapTable, Integer quotedAliasRowId, Integer unquotedAliasRowId)
    {
        // find all references to the quoted alias
        Collection<Map<String, Object>> refs = new TableSelector(aliasMapTable, TableSelector.ALL_COLUMNS, new SimpleFilter("alias", quotedAliasRowId), null).getMapCollection();
        for (Map<String, Object> ref : refs)
        {
            String lsid = (String)ref.get("lsid");

            // check if the lsid has both quoted and unquoted aliases
            Collection<Map<String, Object>> aliasEntriesForLsid = new TableSelector(aliasMapTable, TableSelector.ALL_COLUMNS, new SimpleFilter("lsid", lsid), null).getMapCollection();
            for (Map<String, Object> aliasEntryForLsid : aliasEntriesForLsid)
            {
                if (quotedAliasRowId.equals(aliasEntryForLsid.get("alias")))
                    continue;

                if (unquotedAliasRowId.equals(aliasEntryForLsid.get("alias")))
                {
                    // lsid has both quoted and unquoted aliases.  remove the redundant quoted one.
                    Table.delete(aliasMapTable, new SimpleFilter("lsid", lsid).addCondition("alias", quotedAliasRowId));
                }
            }

            // changing remaining references of the quoted alias to the unquoted alias
            DbSchema schema = aliasMapTable.getSchema();
            SqlExecutor sqlex = new SqlExecutor(schema);
            SQLFragment sql = new SQLFragment("UPDATE ")
                    .append(aliasMapTable.toString())
                    .append(" SET alias=? WHERE alias=?")
                    .add(unquotedAliasRowId)
                    .add(quotedAliasRowId);
            sqlex.execute(sql);
        }
    }

    /** Called from exp-17.23-17.24.sql */
    public static void saveMvIndicatorStorageNames(ModuleContext context) throws Exception
    {
        if (context.isNewInstall())
            return;

        ContainerManager.getProjects().forEach(container -> {
            PropertyService.get().getDomains(container).forEach(domain -> {
                upgradeDomainForMvIndicators(domain, context);
            });
        });
    }

    private static void upgradeDomainForMvIndicators(Domain domain, ModuleContext context)
    {
        User user = context.getUpgradeUser();
        try
        {
            for (DomainProperty domainProp : domain.getProperties())
            {
                PropertyDescriptor pd = domainProp.getPropertyDescriptor();
                if (pd.isMvEnabled())
                {
                    ColumnInfo mvColumn = getMvIndicatorColumn(domain, pd);
                    if (null != mvColumn)
                    {
                        pd.setMvIndicatorStorageColumnName(mvColumn.getName());
                        Table.update(user, OntologyManager.getTinfoPropertyDescriptor(), pd, pd.getPropertyId());
                    }
                }
            }
        }
        catch (Exception e)
        {
            LOG.error("Upgrade for domain '" + domain.getName() + "' for project '" +
                              domain.getContainer() + "' failed: [" + e.getClass().getName() + "] " + e.getMessage());
        }
    }

    public static ColumnInfo getMvIndicatorColumn(Domain domain, PropertyDescriptor prop)
    {
        TableInfo storageTable = DbSchema.get(domain.getDomainKind().getStorageSchemaName(), DbSchemaType.Provisioned).getTable(domain.getStorageTableName());
        ColumnInfo mvColumn = storageTable.getColumn(prop.getStorageColumnName() + "_" + MvColumn.MV_INDICATOR_SUFFIX);
        if (null == mvColumn)
        {
            for(String mvColumnName : PropertyStorageSpec.getLegacyMvIndicatorStorageColumnNames(prop))
            {
                mvColumn = storageTable.getColumn(mvColumnName);
                if (null != mvColumn)
                    break;
            }
            if (null == mvColumn)
                LOG.error("No MV column found for '" + prop.getName() + "' in table '" + domain.getName() + "'");
        }
        return mvColumn;
    }

    /** Called from exp-17.30-17.31.sql */
    public static void rebuildAllEdges(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        ExperimentServiceImpl.get().rebuildAllEdges();
    }
}
