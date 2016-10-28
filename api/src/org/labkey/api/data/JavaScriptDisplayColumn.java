/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link DisplayColumn} subclass that supports injecting JavaScript when rendering to HTML, allowing custom click
 * handlers and other magic behavior to be wired in. Can also add external .js files to the page via client dependencies.
 * User: adam
 * Date: 6/13/13
 */
public class JavaScriptDisplayColumn extends DataColumn
{
    private final LinkedHashSet<ClientDependency> _dependencies = new LinkedHashSet<>();
    private final StringExpressionFactory.FieldKeyStringExpression _eventExpression;
    private String _linkClassName;

    public JavaScriptDisplayColumn(ColumnInfo col, @Nullable Collection<String> dependencies, String javaScriptEvents)
    {
        this(col, dependencies, javaScriptEvents, null);
    }

    public JavaScriptDisplayColumn(ColumnInfo col, @Nullable Collection<String> dependencies, String javaScriptEvents, @Nullable String linkClassName)
    {
        super(col);

        if (null != dependencies)
        {
            for (String dependency : dependencies)
                _dependencies.add(ClientDependency.fromPath(dependency));
        }

        _eventExpression = StringExpressionFactory.FieldKeyStringExpression.create(javaScriptEvents, false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.OutputNull);
        _linkClassName = linkClassName;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);

        if (null != o)
        {
            out.write("<a href=\"#\" tabindex=\"-1\" ");
            if (_linkClassName != null)
            {
                out.write("class=\"" + _linkClassName + "\" ");
            }
            out.write(_eventExpression.eval(ctx));
            out.write(">");
            out.write(getFormattedValue(ctx));
            out.write("</a>");
        }
        else
            out.write("&nbsp;");
    }

    @Override
    public @NotNull Set<ClientDependency> getClientDependencies()
    {
        return _dependencies;
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);

        if (_eventExpression != null)
        {
            keys.addAll(_eventExpression.getFieldKeys());
        }
    }
}
