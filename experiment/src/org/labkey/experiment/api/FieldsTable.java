/*
 * Copyright (c) 2025-2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.query.ExpSchema;

public class FieldsTable extends BaseFieldsTable
{
    public FieldsTable(@NotNull ExpSchema userSchema, @Nullable ContainerFilter containerFilter)
    {
        super(ExpSchema.TableType.Fields.name(), userSchema, containerFilter);
        setDescription("Shows one row for each administrator-defined field in the selected folder(s). Rows are shown in " +
            "a folder or project only if the user has administrator permissions in that folder.");

        addColumn("AlphaNumeric", JdbcType.BOOLEAN).setDescription("Name contains only alphabetic and numeric characters plus underscore");
        addColumn("SpecialCharacters", JdbcType.BOOLEAN).setDescription("Name contains any of these specific punctuation characters: / \"&\\$}~,.");
        addColumn("FieldNameLength", JdbcType.INTEGER).setDescription("Number of characters in the field name");
        addColumn("StorageColumnNameMatch", JdbcType.BOOLEAN).setDescription("Whether the storage column in the database matches the field name");
    }

    @Override
    protected void addColumnSQL(SQLFragment sql)
    {
        // Note that within a range expression, LIKE wildcards (such as underscore) don't need to be escaped
        addBooleanPatternColumn(sql, "pd.Name NOT", "%[^A-Za-z0-9_]%", "AlphaNumeric\n");
        addBooleanPatternColumn(sql, "pd.Name", "%[/ \"&\\\\$}~,.]%", "SpecialCharacters\n");
        sql.append(", ").
                append(getSqlDialect().getVarcharLengthFunction()).
                append("(pd.Name) AS FieldNameLength\n");

        // Identify fields at risk for issue 52666 as ones that would use a provisioned table name that doesn't match their
        // user-facing name, and that are attached to a domain that has a provisioned table
        SQLFragment mismatchSql = new SQLFragment("(pd.StorageColumnName IS NULL OR pd.Name = pd.StorageColumnName) OR NOT EXISTS (SELECT dd.DomainId FROM ");
        mismatchSql.append(OntologyManager.getTinfoDomainDescriptor(), "dd");
        mismatchSql.append(" INNER JOIN ");
        mismatchSql.append(OntologyManager.getTinfoPropertyDomain(), "propDomain");
        mismatchSql.append(" ON dd.DomainId = propDomain.DomainId AND propDomain.PropertyId = pd.PropertyId AND dd.StorageTableName IS NOT NULL)");

        sql.append(", ").
                append(getSqlDialect().wrapBooleanExpression(mismatchSql)).
                append(" AS StorageColumnNameMatch\n");
    }

    private void addBooleanPatternColumn(SQLFragment sql, String expression, String pattern, String name)
    {
        SQLFragment booleanExpression = new SQLFragment(expression)
            .append(" ")
            .append(getSqlDialect().getCharClassLikeOperator()) // Need the operator that supports character classes
            .append(" ?");

        sql.append(", ")
            .append(getSqlDialect().wrapBooleanExpression(booleanExpression))
            .append(" AS ")
            .append(name)
            .add(pattern);
    }
}
