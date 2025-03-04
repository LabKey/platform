package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.query.ExpSchema;

public class FieldsTable extends BaseFieldsTable
{
    public FieldsTable(@NotNull ExpSchema userSchema, @Nullable ContainerFilter containerFilter)
    {
        super("Fields", userSchema, containerFilter);
        setDescription("Shows one row for each administrator-defined field in the selected folder(s). Rows are shown in " +
            "a folder or project only if the user has administrator permissions in that folder.");

        addColumn("AlphaNumeric", JdbcType.BOOLEAN).setDescription("Name contains only alphabetic and numeric characters plus underscore");
        addColumn("SpecialCharacters", JdbcType.BOOLEAN).setDescription("Name contains any of these specific punctuation characters: / \"&\\$}~,.");
    }

    @Override
    protected void addColumnSQL(SQLFragment sql)
    {
        // Note that within a range expression, LIKE wildcards (such as underscore) don't need to be escaped
        addBooleanPatternColumn(sql, "pd.Name NOT", "%[^A-Za-z0-9_]%", "AlphaNumeric");
        addBooleanPatternColumn(sql, "pd.Name", "%[/ \"&\\\\$}~,.]%", "SpecialCharacters");
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
