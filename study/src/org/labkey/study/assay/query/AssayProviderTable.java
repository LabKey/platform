/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.study.assay.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayService;

/**
 * User: kevink
 * Date: 8/5/14
 */
public class AssayProviderTable extends VirtualTable<AssaySchema>
{
    public AssayProviderTable(@NotNull AssaySchema schema)
    {
        super(schema.getDbSchema(), AssaySchema.ASSAY_PROVIDERS_TABLE_NAME, schema);

        setDescription("Contains one row per registered assay provider.");

        ExprColumn nameCol = new ExprColumn(this, "Name", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".Name"), JdbcType.VARCHAR);
        nameCol.setKeyField(true);
        addColumn(nameCol);
        setTitleColumn(nameCol.getName());

        ExprColumn descCol = new ExprColumn(this, "Description", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".Description"), JdbcType.VARCHAR);
        addColumn(descCol);

        ExprColumn moduleCol = new ExprColumn(this, "Module", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".Module"), JdbcType.VARCHAR);
        addColumn(moduleCol);

        ExprColumn runLSIDPrefixCol = new ExprColumn(this, "RunLSIDPrefix", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".RunLSIDPrefix"), JdbcType.INTEGER);
        runLSIDPrefixCol.setHidden(true);
        addColumn(runLSIDPrefixCol);
    }

    @Override @NotNull
    public SQLFragment getFromSQL()
    {
        SQLFragment sql = new SQLFragment();
        String separator = "";

        for (AssayProvider provider : AssayService.get().getAssayProviders())
        {
            sql.append(separator);
            separator = " UNION ";
            sql.append("SELECT ? AS Name, ? AS Description, ? AS Module, ? AS RunLSIDPrefix");
            sql.add(provider.getName());
            // Trim to first sentence to avoid overly long Nab descriptions.
            String desc = provider.getDescription();
            int dot = desc.indexOf(". ");
            if (dot > -1)
                desc = desc.substring(0, dot+1);
            sql.add(desc);
            sql.add(provider.getDeclaringModule().getName());
            sql.add(provider.getRunLSIDPrefix());
        }

        return sql;
    }



    @Override
    public String getPublicSchemaName()
    {
        return AssaySchema.NAME;
    }
}
