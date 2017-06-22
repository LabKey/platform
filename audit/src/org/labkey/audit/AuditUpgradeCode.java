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
package org.labkey.audit;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 11/4/2016.
 */
public class AuditUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(AuditUpgradeCode.class);

    /**
     * Invoked from 16.31-16.32 to address issue 28310
     * Repairs fields in the audit schema that were incorrectly re typed from int to string.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void repairAuditFieldTypes(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            // map of audit types to info needed for changing the column type
            Map<String, AuditRepairInfo> tableMap = new HashMap<>();

            // populate with information about tables needing repair
            tableMap.put("UserAuditEvent", new AuditRepairInfo("Audit-UserAuditDomain", Arrays.asList("\"user\""),
                    Collections.singletonList(new AuditIndex("userauditdomain_User", "\"user\""))));

            List<AuditIndex> groupIndices = new ArrayList<>();
            groupIndices.add(new AuditIndex("groupauditdomain_User", "\"user\""));
            groupIndices.add(new AuditIndex("groupauditdomain_Group", "\"group\""));
            tableMap.put("GroupAuditEvent", new AuditRepairInfo("Audit-GroupAuditDomain", Arrays.asList("\"user\"", "\"group\""), groupIndices));

            tableMap.put("Client API Actions", new AuditRepairInfo("Audit-ClientApiAuditDomain", Arrays.asList("int1", "int2", "int3"), Collections.emptyList()));
            tableMap.put("ExperimentAuditEvent", new AuditRepairInfo("Audit-ExperimentAuditDomain", Arrays.asList("rungroup"), Collections.emptyList()));
            tableMap.put("ListAuditEvent", new AuditRepairInfo("Audit-ListAuditDomain", Arrays.asList("listid"), Collections.emptyList()));
            tableMap.put("QueryExportAuditEvent", new AuditRepairInfo("Audit-QueryAuditDomain", Arrays.asList("datarowcount"), Collections.emptyList()));
            tableMap.put("AssayPublishAuditEvent", new AuditRepairInfo("Audit-AssayAuditDomain", Arrays.asList("protocol", "recordcount", "datasetid"),
                    Collections.singletonList(new AuditIndex("assayauditdomain_Protocol", "protocol"))));
            tableMap.put("DatasetAuditEvent", new AuditRepairInfo("Audit-DatasetAuditDomain", Arrays.asList("datasetid"), Collections.emptyList()));

            try (DbScope.Transaction transaction = AuditSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                Container domainContainer = ContainerManager.getSharedContainer();
                SqlDialect dialect = AuditSchema.getInstance().getSqlDialect();

                for (Map.Entry<String, AuditRepairInfo> entry : tableMap.entrySet())
                {
                    String eventName = entry.getKey();
                    AuditRepairInfo info = entry.getValue();

                    String domainURI = AbstractAuditDomainKind.getDomainURI(AbstractAuditTypeProvider.QUERY_SCHEMA_NAME, eventName, info.eventAndDomainPrefix, domainContainer, null);
                    Domain domain = PropertyService.get().getDomain(domainContainer, domainURI);

                    if (domain != null)
                    {
                        String schemaName = "audit";
                        String queryName = domain.getStorageTableName();
                        String indexPrefix = queryName.substring(0, queryName.indexOf("_"));

                        // drop any indexes first
                        for (AuditIndex index : info.indices)
                        {
                            String tableName = schemaName + "." + queryName;
                            String indexName = indexPrefix + "_" + index.name;
                            _log.info("Dropping index: " + indexName + " for audit table: " + tableName);

                            AuditSchema.getInstance().getSchema().dropIndexIfExists(queryName, indexName);
                        }

                        for (String colName : info.fields)
                        {
                            _log.info("Re typing column: " + colName + " for audit table: " + queryName);

                            // perform the retyping
                            SQLFragment sql = new SQLFragment("ALTER TABLE ").append(schemaName).append(".").append(queryName);

                            // need an explicit cast for postgres
                            if (dialect.isPostgreSQL())
                            {
                                sql.append(" ALTER COLUMN ").append(colName).append(" SET DATA TYPE INTEGER");
                                sql.append(" USING CAST(").append(colName).append(" AS INTEGER)");
                            }
                            else if (dialect.isSqlServer())
                            {
                                sql.append(" ALTER COLUMN ").append(colName).append(" INTEGER");
                            }
                            new SqlExecutor(AuditSchema.getInstance().getSchema()).execute(sql);
                        }

                        // re-add any indexes
                        for (AuditIndex index : info.indices)
                        {
                            String tableName = schemaName + "." + queryName;
                            String indexName = indexPrefix + "_" + index.name;
                            _log.info("Creating index: " + indexName + " for audit table: " + tableName);

                            SQLFragment sql = new SQLFragment("CREATE INDEX ").append(indexName).append(" ON ").append(tableName).
                                    append("(").append(index.column).append(")");

                            new SqlExecutor(AuditSchema.getInstance().getSchema()).execute(sql);
                        }
                    }
                    else
                        _log.error("Unable to retrieve the domain for audit event type : " + eventName);
                }
                transaction.commit();
            }
        }
    }

    static class AuditRepairInfo
    {
        String eventAndDomainPrefix;
        Collection<String> fields;
        Collection<AuditIndex> indices;

        public AuditRepairInfo(String eventAndDomainPrefix, Collection<String> fields, Collection<AuditIndex> indices)
        {
            this.eventAndDomainPrefix = eventAndDomainPrefix;
            this.fields = fields;
            this.indices = indices;
        }
    }

    static class AuditIndex
    {
        String name;
        String column;

        public AuditIndex(String name, String column)
        {
            this.name = name;
            this.column = column;
        }
    }
}
