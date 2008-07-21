package org.labkey.query.sql;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jul 15, 2008
 * Time: 4:00:54 PM
 */
public class QDistinct extends QNode<QExpr>
{
    public void appendSource(SourceBuilder builder)
    {
        builder.append(" DISTINCT ");
    }
}