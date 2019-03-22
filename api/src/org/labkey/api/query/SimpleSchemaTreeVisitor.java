/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
 * A schema tree visitor.
 * There are two approaches to extending the SimpleSchemaTreeVisitor:
 * <ul>
 *   <li>
 *   Imperitively: Subclasses choose R as Void and mutate state in either the parameter P or in the subclass itself.
 *   Subclasses are responsible for overriding visitXXX() as appropriate, mutating state, and walking the schema tree calling visit().
 *   </li>
 *
 *   <li>
 *   Functionally: Subclasses choose P as Void and override reduce to combine the results into a single R.
 *   Subclasses are responsible for overriding visitXXX() as appropriate, overriding reduce(), and walking the schema tree calling visit() or visitAndReduce().
 *   </li>
 * </ul>
 *
 * The Path parameter is created while visiting a node and includes the current node.
 * For convenience, the SchemTreeWalker provides a default (and expensive) implementation that will walk the entire schema tree.
 *
 * @example
 *
 * Collect all FieldKeys (functionally with reduce):
 * <pre>
 * SimpleSchemaTreeVisitor<Set<FieldKey>, Void> visitor = new SimpleSchemaTreeVisitor<Set<FieldKey>, Void>()
 * {
 *     @Override
 *     public Set<FieldKey> reduce(Set<FieldKey> r1, Set<FieldKey> r2)
 *     {
 *         Set<FieldKey> names = new TreeSet<FieldKey>(FieldKey.CASE_INSENSITIVE_ORDER);
 *         if (r1 != null) names.addAll(r1);
 *         if (r2 != null) names.addAll(r2);
 *         return names;
 *     }
 *
 *     @Override
 *     public Set<FieldKey> visitUserSchema(UserSchema schema, Path path, Void param)
 *     {
 *         Set<FieldKey> r = Collections.singleton(path.schemaPath);
 *         return visitAndReduce(schema.getSchemas(), path, param, r);
 *     }
 * };
 *
 * Set&lt;FieldKey> names = visitor.visitTop(schema, null);
 * </pre>
 *
 * @example
 * Translate the schema tree into a JSONObject (imperitively by mutating parameter state):
 * <pre>
 * SimpleSchemaTreeVisitor visitor = new SimpleSchemaTreeVisitor<Void, JSONObject>()
 * {
 *     @Override
 *     public Void visitUserSchema(UserSchema schema, Path path, JSONObject json)
 *     {
 *         JSONObject schemaProps = new JSONObject();
 *         schemaProps.put("name", schema.getName());
 *
 *         JSONObject children = new JSONObject();
 *         visitAndReduce(schema.getSchemas(), path, children, null);
 *         schemaProps.put("schemas", children);
 *
 *         // Add node's schemaProps to the parent's json.
 *         json.put(schema.getName(), schemaProps);
 *         return null;
 *     }
 * };
 *
 * JSONObject json = new JSONObect();
 * visitor.visitTop(schema, json);
 * </pre>
 *
 */
public class SimpleSchemaTreeVisitor<R, P> implements SchemaTreeVisitor<R, P>
{
    protected final boolean _includeHidden;
    protected final R _defaultValue;

    protected SimpleSchemaTreeVisitor(boolean includeHidden)
    {
        _includeHidden = includeHidden;
        _defaultValue = null;
    }

    protected SimpleSchemaTreeVisitor(boolean includeHidden, R defaultValue)
    {
        _includeHidden = includeHidden;
        _defaultValue = defaultValue;
    }

    /**
     * Subclasses may choose to override this to provide a default value for all nodes.
     */
    protected R defaultAction(SchemaTreeNode node, Path parent, P param)
    {
        return _defaultValue;
    }

    /**
     * Subclasses may choose to override this to provide a default value for all nodes.
     */
    protected R defaultErrorAction(SchemaTreeNode parentNode, String child, Exception e, Path parent, P param)
    {
        return _defaultValue;
    }

    /**
     * Subclasses may choose to override to combine two results together.
     * By default, the result from the most recently visited node is returned.
     */
    public R reduce(R r1, R r2) {
        return r1;
    }

    /** Convenience method to visit top-level node. */
    public final R visitTop(SchemaTreeNode node, P param)
    {
        return visit(node, null, param);
    }

    /** Visit a single node. */
    public final R visit(SchemaTreeNode node, Path path, P param)
    {
        if (node == null)
            return null;

        return node.accept(this, new Path(path, node), param);
    }

    protected final R visitAndReduce(SchemaTreeNode node, Path path, P p, R r) {
        return reduce(visit(node, path, p), r);
    }

    /** Convenience method to visit a top-level nodes. */
    public final R visitTop(Iterable<? extends SchemaTreeNode> nodes, P param)
    {
        return visit(nodes, null, param);
    }

    /** Visit a list of nodes. */
    public final R visit(Iterable<? extends SchemaTreeNode> nodes, Path path, P param)
    {
        R r = null;
        if (nodes != null)
        {
            boolean first = true;
            for (SchemaTreeNode node : nodes) {
                r = first ? visit(node, path, param) : visitAndReduce(node, path, param, r);
                first = false;
            }
        }
        return r;
    }

    protected final R visitAndReduce(Iterable<? extends SchemaTreeNode> nodes, Path path, P p, R r) {
        return reduce(visit(nodes, path, p), r);
    }

    @Override
    public R visitDefaultSchema(DefaultSchema schema, Path path, P param)
    {
        return defaultAction(schema, path, param);
    }

    @Override
    public R visitUserSchema(UserSchema schema, Path path, P param)
    {
        return defaultAction(schema, path, param);
    }

    @Override
    public R visitTable(TableInfo table, Path path, P param)
    {
        return defaultAction(table, path, param);
    }

    @Override
    public R visitTableError(UserSchema schema, String name, Exception e, Path path, P param)
    {
        return defaultErrorAction(schema, name, e, path, param);
    }

}
