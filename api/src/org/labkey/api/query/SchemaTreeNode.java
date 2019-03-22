/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
     * @param <P> type of additional data.
     * @see SchemaTreeVisitor
     */
    public <R, P> R accept(SchemaTreeVisitor<R, P> visitor, SchemaTreeVisitor.Path path, P param);
}
