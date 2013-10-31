/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

    R visitTableError(UserSchema schema, String name, Exception e, Path path, P param);

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
