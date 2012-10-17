package org.labkey.query.sql;

import org.antlr.runtime.tree.CommonTree;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.FieldKey;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-10-17
 * Time: 1:31 PM
 */
public class QIfDefined extends QExpr
{
    boolean isDefined = true;

    QIfDefined(CommonTree node)
    {
        super(QFieldKey.class);
        from(node);
    }

    @Override
    public FieldKey getFieldKey()
    {
        return ((QExpr)getFirstChild()).getFieldKey();
    }

    @Override
    public void appendSql(SqlBuilder builder)
    {
        if (isDefined)
            ((QExpr)getFirstChild()).appendSql(builder);
        else
            builder.append("NULL");
    }

    @Override
    public boolean isConstant()
    {
        return ((QExpr)getFirstChild()).isConstant();
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append("IFDEFINED(");
        getFirstChild().appendSource(builder);
        builder.append(")");
    }

    @Override
    protected boolean isValidChild(QNode n)
    {
        return n instanceof QField || n instanceof QIdentifier || n instanceof QDot;
    }

    @NotNull
    @Override
    public JdbcType getSqlType()
    {
        if (isDefined)
            return ((QExpr)getFirstChild()).getSqlType();
        else
            return JdbcType.OTHER;
    }
}
