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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

import java.util.LinkedList;
import java.util.List;

public class ExpDataClassType implements AttachmentType
{
    private static final AttachmentType INSTANCE = new ExpDataClassType();

    private ExpDataClassType()
    {
    }

    public static AttachmentType get()
    {
        return INSTANCE;
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return getClass().getName();
    }

    @Override
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        TableInfo tableInfo = ExperimentService.get().getTinfoDataClass();

        // Get a dialect-specific expression that can extract an ObjectId from the LSID column and a WHERE clause to
        // filter the rows to LSIDs containing ObjectIds
        Pair<String, String> pair = Lsid.getSqlExpressionToExtractObjectId("LSID", tableInfo.getSqlDialect());
        String expressionToExtractObjectId = pair.first;
        String where = pair.second;

        List<String> selectStatements = new LinkedList<>();

        // Enumerate the rows in exp.DataClass
        new TableSelector(tableInfo, PageFlowUtil.set("Container", "LSID")).forEach(rs->{
            // Look up the associated domain for this ExpDataClass by Container and LSID
            Container c = ContainerManager.getForId(rs.getString("Container"));
            String lsid = rs.getString("LSID");
            Domain domain = PropertyService.get().getDomain(c, lsid);

            // Add a select for the ObjectIds in this ExpDataClass if the domain includes an attachment column. ExpDataClass attachments
            // use the LSID's ObjectId as the attachment parent EntityId, so we need to use a SQL expression to extract it.
            if (null != domain && domain.getProperties().stream().anyMatch(p -> p.getPropertyType() == PropertyType.ATTACHMENT))
                selectStatements.add("\n    SELECT " + expressionToExtractObjectId + " AS ID FROM expdataclass." + domain.getStorageTableName() + " WHERE " + where);
        });

        if (selectStatements.isEmpty())
            sql.append("1 = 0");  // No ExpDataClasses with attachment columns
        else
            sql.append(parentColumn).append(" IN (").append(StringUtils.join(selectStatements, "\n    UNION")).append(")");
    }
}

