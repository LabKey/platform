package org.labkey.api.query;

import org.labkey.api.data.TableInfo;

/**
 * Visit a schema tree.
 */
public interface SchemaTreeVisitor<R, P>
{
    R visitDefaultSchema(DefaultSchema schema, Path path, P param);

    //R visitFolderSchema(FolderSchema schema, Path path, P param);

    R visitUserSchema(UserSchema schema, Path path, P param);

    R visitTable(TableInfo table, Path path, P param);

    /** Provides a path back up to root that can be used while visiting nodes. */
    public final class Path
    {
        public final Path parent;
        public final SchemaTreeNode node;
        public final SchemaKey schemaPath;

        public Path(Path parent, SchemaTreeNode node)
        {
            this.parent = parent;
            this.node = node;
            this.schemaPath = new SchemaKey(parent != null ? parent.schemaPath : null, node.getName());
        }
    }
}
