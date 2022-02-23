/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.query.sql;

import org.antlr.runtime.tree.CommonTree;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class QUnknownNode extends QNode implements SupportsAnnotations
{
    int _type = 0;
    
    public QUnknownNode()
    {
        _type = 0;
    }

    public QUnknownNode(CommonTree n)
    {
		super(n);
    }
    
    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append(getTokenText());
    }

    // QUnknownNode is used to represent a table aka RANGE in the FROM list, need annotations for that
    Map<String,Object> _annotations = null;

    @Override
    public void setAnnotations(Map<String, Object> a)
    {
        _annotations = a;
    }

    @Override
    public @NotNull Map<String, Object> getAnnotations()
    {
        return null==_annotations ? Map.of() : _annotations;
    }
}
