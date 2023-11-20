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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.DOM.Attribute.tabindex;

/**
 * {@link DisplayColumn} subclass that supports injecting JavaScript when rendering to HTML, allowing custom click
 * handlers and other magic behavior to be wired in. Can also add external .js files to the page via client dependencies.
 */
public class JavaScriptDisplayColumn extends DataColumn
{
    private final LinkedHashSet<ClientDependency> _dependencies = new LinkedHashSet<>();
    private final @Nullable StringExpressionFactory.FieldKeyStringExpression _onClickExpression;
    private final @Nullable String _linkClassName;

    public JavaScriptDisplayColumn(ColumnInfo col, @Nullable Collection<String> dependencies)
    {
        this(col, dependencies, null, null);
    }

    public JavaScriptDisplayColumn(ColumnInfo col, @Nullable Collection<String> dependencies, @Nullable String onClickJavaScript, @Nullable String linkClassName)
    {
        super(col);

        if (null != dependencies)
        {
            for (String dependency : dependencies)
                _dependencies.add(ClientDependency.fromPath(dependency));
        }

        _onClickExpression = null != onClickJavaScript ? StringExpressionFactory.FieldKeyStringExpression.create(onClickJavaScript, false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.OutputNull) : null;
        _linkClassName = linkClassName;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);

        if (null != o)
        {
            String onClick = null;

            if (_onClickExpression != null)
                onClick = StringUtils.trim(_onClickExpression.eval(ctx));

            renderLink(out, getFormattedHtml(ctx), onClick, _linkClassName);
        }
        else
            out.write("&nbsp;");
    }

    protected void renderLink(Writer out, HtmlString html, @Nullable String onClick, @Nullable String linkClassName)
    {
        LinkBuilder builder = new LinkBuilder(html)
            .href("#")
            .attributes(Map.of(tabindex.name(), "-1"))
            .onClick(onClick);

        if (linkClassName != null)
            builder.addClass(linkClassName);
        else
            builder.clearClasses();

        builder.appendTo(out);
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

        if (_onClickExpression != null)
        {
            keys.addAll(_onClickExpression.getFieldKeys());
        }
    }
}
