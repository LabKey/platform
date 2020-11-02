package org.labkey.query.sql;

import org.antlr.runtime.tree.CommonTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.FieldKey;

public class QResolveTableColumn extends QIfDefined
{
    FieldKey _fieldKey = null;
    QueryRelation.RelationColumn _column = null;

    public QResolveTableColumn(CommonTree node)
    {
        super(node);
    }

    @Override
    protected boolean isValidChild(QNode n)
    {
        return n instanceof QDot || n instanceof QExprList;
    }

    /**
     * Helper for finding a column in a particular Table
     */
    private QueryRelation.RelationColumn resolveColumn(QueryRelation table, @Nullable String name, @Nullable String concept, QNode location)
    {
        if (null == name && null == concept)
            return null;

        if (null != name)
        {
            QueryRelation.RelationColumn col = table.getColumn(name);
            if (null == col || null == concept)
                return col;
            return concept.equals(col.getPrincipalConceptCode()) ? col : null;
        }

        QueryRelation.RelationColumn found = null;
        int count = 0;
        for (QueryRelation.RelationColumn col : table.getAllColumns().values())
        {
            if (concept.equals(col.getPrincipalConceptCode()))
            {
                count++;
                found = col;
            }
        }
        if (count > 1)
        {
            table.reportWarning("Column is ambigious: " + concept, location);
            return null;
        }

        /* have to call getColumn() so table knows column is in use */
        return table.getColumn(found.getFieldKey().getName());
    }


    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        throw new IllegalStateException();
    }

    @Override
    public boolean isConstant()
    {
        return false;
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        getFirstChild().appendSource(builder);
        builder.append("(");
        builder.pushPrefix("");

        for (QNode n : getLastChild().children())
        {
            QExpr child = (QExpr)n;
            child.appendSource(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        builder.append(")");
    }

    @Override
    public FieldKey getFieldKey()
    {
        assert null != select;

        if (null != _fieldKey || !isDefined)
            return _fieldKey;

        QDot dot = (QDot) getFirstChild();
        assert dot.getFieldKey().size() == 2;
        FieldKey tableName = dot.getFieldKey().getTable();
        QueryRelation table = select.getTable(tableName);
        if (null == table)
        {
            select.parseError("Could not resolve table: " + tableName.toDisplayString(), this);
            isDefined = false;
            return null;
        }

        // get columnName
        String columnName = null;
        QExprList exprList = (QExprList)getLastChild();
        QNode nameArg = null==exprList ? null : exprList.getLastChild();
        if (nameArg instanceof QString)
        {
             columnName = ((QString)nameArg).getValue();
        }
        else
        {
            isDefined = false;
            return null;
        }
        QueryRelation.RelationColumn c = resolveColumn(table, columnName, null, null);
        if (null == c)
        {
            isDefined = false;
            return null;
        }
        _fieldKey = tableName.append(c.getFieldKey().getName());
        _column = c;
        return _fieldKey;
    }

    @Override
    public @NotNull JdbcType getJdbcType()
    {
        return null!=_column ? JdbcType.VARCHAR : _column.getJdbcType();
    }
}