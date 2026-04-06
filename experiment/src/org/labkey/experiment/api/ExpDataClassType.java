/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.experiment.api;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.DataClassUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.logging.LogHelper;

import java.util.LinkedList;
import java.util.List;

public class ExpDataClassType implements AttachmentParentType
{
    private static final AttachmentParentType INSTANCE = new ExpDataClassType();
    private static final Logger LOG = LogHelper.getLogger(ExpDataClassType.class, "Issues selecting entityIds");

    private ExpDataClassType()
    {
    }

    public static AttachmentParentType get()
    {
        return INSTANCE;
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return "DataClass";
    }

    @Override
    public @NotNull SQLFragment getSelectEntityIdAndDescriptionSql()
    {
        TableInfo tableInfo = ExperimentService.get().getTinfoDataClass();
        SqlDialect dialect = tableInfo.getSqlDialect();

        // Get a dialect-specific expression that can extract an ObjectId from the LSID column and a WHERE clause to
        // filter the rows to LSIDs containing ObjectIds
        Pair<SQLFragment, SQLFragment> pair = Lsid.getSqlExpressionToExtractObjectId(new SQLFragment("LSID"), tableInfo.getSqlDialect());
        SQLFragment expressionToExtractObjectId = pair.first;
        SQLFragment where = pair.second;

        List<SQLFragment> selectStatements = new LinkedList<>();

        // Enumerate the rows in exp.DataClass
        new TableSelector(tableInfo, PageFlowUtil.set("Container", "LSID")).forEach(rs->{
            // Look up the associated domain for this ExpDataClass by Container and LSID
            Container c = ContainerManager.getForId(rs.getString("Container"));
            String lsid = rs.getString("LSID");
            Domain domain = PropertyService.get().getDomain(c, lsid);

            if (null != domain)
            {
                // Enumerate columns on the data class TableInfo since it includes the vocabulary domain columns.
                // For example, Compound has a built-in Structure2D attachment column supplied by a vocabulary domain.
                TableInfo dataClassTable = new DataClassUserSchema(c, User.getSearchUser()).getTable(domain.getName());

                if (dataClassTable == null)
                {
                    LOG.warn("DataClass table not found for {}", domain.getName());
                }
                else if (dataClassTable.getColumns().stream().anyMatch(col -> col.getPropertyType() == PropertyType.ATTACHMENT))
                {
                    // Add a select for the ObjectIds in this ExpDataClass if the table includes an attachment column.
                    // ExpDataClass attachments use the LSID's ObjectId as the attachment parent EntityId, so we need
                    // to use a SQL expression to extract it.
                    selectStatements.add(
                        new SQLFragment("\n    SELECT ")
                            .append(expressionToExtractObjectId)
                            .append(" AS EntityId, ")
                            .append(dialect.concatenate(
                                new SQLFragment("?", domain.getName()),
                                new SQLFragment("':'"),
                                new SQLFragment("Name")
                            ))
                            .append(" AS Description FROM expdataclass.")
                            .append(domain.getStorageTableName())
                            .append(" ds JOIN ")
                            .append(ExperimentService.get().getTinfoData())
                            .append(" d ON d.rowId = ds.rowid")
                            .append(" WHERE ").append(where)
                    );
                }
            }
        });

        return selectStatements.isEmpty() ?
            NO_ROWS : // No ExpDataClasses with attachment columns
            SQLFragment.join(selectStatements, new SQLFragment("\n    UNION"));
    }
}

