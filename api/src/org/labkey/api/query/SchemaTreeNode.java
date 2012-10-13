package org.labkey.api.query;

/**
 * User: kevink
 * Date: 10/9/12
 */
public interface SchemaTreeNode
{
    public String getName();

    /**
     * Accept method used to implement the visitor pattern.
     *
     * @param <R> result type of this operation.
     * @param path The current path, including this node.
     * @param <P> type of additonal data.
     * @see SchemaTreeVisitor
     */
    public <R, P> R accept(SchemaTreeVisitor<R, P> visitor, SchemaTreeVisitor.Path path, P param);
}
