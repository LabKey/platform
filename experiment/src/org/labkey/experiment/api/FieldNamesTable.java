package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.query.ExpSchema;

public class FieldNamesTable extends BaseFieldNamesTable
{
    public FieldNamesTable(@NotNull ExpSchema userSchema, @Nullable ContainerFilter containerFilter)
    {
        super("FieldNames", userSchema, containerFilter);

        addColumn("AlphaNumeric", JdbcType.BOOLEAN).setDescription("Name contains only alphabetic and numeric characters");
        addColumn("SpecialCharacters", JdbcType.BOOLEAN).setDescription("Name contains any of these specific punctuation characters: / \"&\\$}~,.");
    }

    @Override
    protected void addColumnSQL(SQLFragment sql)
    {
        addBooleanPatternColumn(sql, "pd.Name NOT", "%[^A-Za-z0-9]%", "AlphaNumeric");
        addBooleanPatternColumn(sql, "pd.Name", "%[/ \"&\\\\$}~,.]%", "SpecialCharacters");
    }

    private void addBooleanPatternColumn(SQLFragment sql, String expression, String pattern, String name)
    {
        // Need the operator that supports character classes
        String operator = getSqlDialect().getCharClassLikeOperator();
        sql.append(", ");

        // Can't expose a boolean expression as a boolean column on SQL Server, so wrap with a CASE statement
        if (getSqlDialect().isSqlServer())
            sql.append("CAST(CASE WHEN ");

        sql.append(expression).append(" ").append(operator).append(" ?");

        if (getSqlDialect().isSqlServer())
            sql.append(" THEN 1 ELSE 0 END AS BIT)");

        sql.append(" AS ").append(name);
        sql.add(pattern);
    }
}
