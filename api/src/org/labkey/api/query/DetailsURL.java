/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.StringExpressionType;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.labkey.api.data.AbstractTableInfo.LINK_DISABLER;

/**
 * Representation of a dynamic URL expression that can substitute in parameter values, typically filled in via
 * the values of database columns from a ResultSet.
 */
public final class DetailsURL extends StringExpressionFactory.FieldKeyStringExpression implements HasViewContext
{
    public static final Pattern actionPattern = Pattern.compile("/?[\\w\\-]+/[\\w\\-]+.view?.*");
    public static final Pattern classPattern = Pattern.compile("[\\w\\.\\$]+\\.class(\\?.*)?");

    protected ContainerContext _containerContext;

    // constructor parameters
    ActionURL _url;
    String _urlSource;

    // parsed fields
    ActionURL _parsedUrl;
    private boolean _strictContainerContextEval;
    // _source from AbstractStringExpression            


    public static String validateURL(String str)
    {
        if (DetailsURL.actionPattern.matcher(str).matches() || DetailsURL.classPattern.matcher(str).matches())
            return null;

        return "Invalid url pattern: " + str;
    }

    /**
     * Create DetailsURL from a string.
     * Use {@link StringExpressionFactory#createURL(String)} to obtain parsed URLs from a cache.
     *
     * @param str The URL template string.
     * @return DetailsURL
     * @throws IllegalArgumentException if URL string is invalid.
     * @see StringExpressionFactory#createURL(String) for cached URLs.
     */
    @NotNull
    public static DetailsURL fromString(@NotNull String str)
    {
        DetailsURL ret = new DetailsURL(str, null, null);
        ret.parse();    // validate
        return ret;
    }

    /**
     * Create DetailsURL from a string with a {@link ContainerContext}.
     * Usually, ContainerContext will be supplied to the DetailsURL from the
     * {@link org.labkey.api.data.TableInfo} that the DetailsURL is attached to.
     *
     * @param str The URL template string.
     * @param cc The ContainerContext.
     * @param nullBehavior Indicate how null values within the expression are handled.
     * @param errors If not null, any URL parse errors are added to the collection and null is returned.  @return DetailsURL or null.
     * @throws IllegalArgumentException if errors is null and URL string is invalid.
     * @see StringExpressionFactory#createURL(String)
     */
    @Nullable
    public static DetailsURL fromString(@NotNull String str, @Nullable ContainerContext cc, @Nullable NullValueBehavior nullBehavior, @Nullable Collection<QueryException> errors)
        throws IllegalArgumentException
    {
        try
        {
            return fromString(str, cc, nullBehavior);
        }
        catch (IllegalArgumentException iae)
        {
            if (errors != null)
                errors.add(new MetadataParseWarning("Illegal URL expression '" + str + "': " + iae.getMessage(), iae, 0, 0));
            else
                throw iae;
        }
        return null;
    }

    /**
     * Create DetailsURL from the string with a {@link ContainerContext}.
     * Usually, ContainerContext will be supplied to the DetailsURL from the
     * {@link org.labkey.api.data.TableInfo} that the DetailsURL is attached to.
     *
     * @param str The URL template string.
     * @param cc The ContainerContext.
     * @return DetailsURL
     * @throws IllegalArgumentException if URL string is invalid.
     * @see StringExpressionFactory#createURL(String)
     */
    @NotNull
    public static DetailsURL fromString(@NotNull String str, @Nullable ContainerContext cc)
            throws IllegalArgumentException
    {
        return fromString(str, cc, null);
    }

    /**
     * Create DetailsURL from the string with a {@link ContainerContext}.
     * Usually, ContainerContext will be supplied to the DetailsURL from the
     * {@link org.labkey.api.data.TableInfo} that the DetailsURL is attached to.
     *
     * @param str The URL template string.
     * @param cc The ContainerContext.
     * @return DetailsURL
     * @throws IllegalArgumentException if URL string is invalid.
     * @see StringExpressionFactory#createURL(String)
     */
    @NotNull
    public static DetailsURL fromString(@NotNull String str, @Nullable ContainerContext cc, @Nullable NullValueBehavior nullBehavior)
        throws IllegalArgumentException
    {
        DetailsURL ret = new DetailsURL(str, cc, nullBehavior);
        ret.parse();    // validate
        return ret;
    }

    /**
     * Create DetailsURL from an StringExpressionType from the tableInto.xsd.
     *
     * @param xurl The URL template string.
     * @param errors If not null, any URL parse errors are added to the collection and null is returned.  @return DetailsURL or null.
     * @throws IllegalArgumentException if errors is null and URL string is invalid.
     * @see StringExpressionFactory#createURL(String)
     */
    public static DetailsURL fromXML(@NotNull StringExpressionType xurl, @Nullable Collection<QueryException> errors)
        throws IllegalArgumentException
    {
        String url = xurl.getStringValue();
        if (StringUtils.isBlank(url))
            return LINK_DISABLER;

        NullValueBehavior nullBehavior = NullValueBehavior.fromXML(xurl.getReplaceMissing());
        return DetailsURL.fromString(url, null, nullBehavior, errors);
    }


    protected DetailsURL(String str)
    {
        this(str, null, null);
    }

    protected DetailsURL(String str, @Nullable ContainerContext cc)
    {
        this(str, cc, null);
    }

    protected DetailsURL(@NotNull String str, @Nullable ContainerContext cc, @Nullable NullValueBehavior nullBehavior)
    {
        super("", true, nullBehavior);
        _urlSource = str.trim();
        _containerContext = cc;
    }


    public DetailsURL(ActionURL url)
    {
        _url = url.clone();
    }

    /**
     * @param url base URL to which parameters may be added
     * @param columnParams map from URL parameter name to source column identifier, which may be a String, FieldKey, or ColumnInfo
     */
    public DetailsURL(ActionURL url, Map<String, ?> columnParams)
    {
        this(url, columnParams, null);
    }

    public DetailsURL(ActionURL url, @Nullable Map<String, ?> columnParams, @Nullable NullValueBehavior nullBehavior)
    {
        super("", true, nullBehavior);
        url = url.clone();
        if (columnParams != null)
        {
            for (Map.Entry<String, ?> e : columnParams.entrySet())
            {
                Object v = e.getValue();
                String strValue;
                if (v instanceof String)
                    strValue = (String) v;
                else if (v instanceof FieldKey)
                    strValue = ((FieldKey) v).encode();
                else if (v instanceof ColumnInfo)
                    strValue = ((ColumnInfo) v).getFieldKey().encode();
                else
                    throw new IllegalArgumentException("Column param not supported: " + String.valueOf(v));
                url.addParameter(e.getKey(), "${" + strValue + "}");
            }
        }
        _url = url;
    }

    public DetailsURL(ActionURL baseURL, String param, FieldKey subst)
    {
        this(baseURL, Collections.singletonMap(param,subst));
    }

    @Override
    protected void parse()
            throws IllegalArgumentException
    {
        assert null == _url || null == _urlSource;

        if (null != _url)
        {
            _parsedUrl = _url;
        }
        else if (null != _urlSource)
        {
            String expr = _urlSource;
            int i = StringUtils.indexOfAny(expr,": /");
            String protocol = (i != -1 && expr.charAt(i) == ':') ? expr.substring(0,i) : "";

            if (protocol.contains("script"))
                throw new IllegalArgumentException("Script not allowed in urls: " + expr);

            if (actionPattern.matcher(expr).matches())
            {
                if (!expr.startsWith("/")) expr = "/" + expr;
                _parsedUrl = new ActionURL(expr);
            }
            else if (classPattern.matcher(expr).matches())
            {
                int indexClass = expr.indexOf(".class?");
                int indexQuery = expr.length();
                if (indexClass >= 0)
                {
                    indexQuery = indexClass + ".class?".length();
                }
                else
                {
                    if (expr.endsWith(".class"))
                        indexClass = expr.length()-".class".length();
                    else
                        indexClass = expr.length();
                }
                String className = expr.substring(0,indexClass);
                Class<Controller> cls;
                try { cls = (Class<Controller>)Class.forName(className); } catch (Exception x) {throw new IllegalArgumentException("action class '" + className + "' not found: " + expr);}
                _parsedUrl = new ActionURL(cls, null);
                _parsedUrl.setRawQuery(expr.substring(indexQuery));
            }
            else
                throw new IllegalArgumentException(
                        "Failed to parse url '" + _urlSource + "'.\n" +
                        "Supported url formats:\n" +
                        "\t/controller/action.view?id=${RowId}\n" +
                        "\torg.labkey.package.MyController$ActionAction.class?id=${RowId}");
        }
        else
            throw new IllegalArgumentException("URL required");
            
        _source = StringUtils.trimToEmpty(_parsedUrl.getQueryString(true));

        super.parse();
    }


    @Override
    public Set<FieldKey> getFieldKeys()
    {
        Set<FieldKey> set = super.getFieldKeys();
        if (_containerContext instanceof ContainerContext.FieldKeyContext)
            set.add(((ContainerContext.FieldKeyContext) _containerContext).getFieldKey());
        return set;
    }

    public boolean hasContainerContext()
    {
        return _containerContext != null;
    }

    @Override
    public DetailsURL remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> remap)
    {
        DetailsURL copy = (DetailsURL)super.remapFieldKeys(parent, remap);
        if (copy._containerContext instanceof ContainerContext.FieldKeyContext)
        {
            FieldKey key = ((ContainerContext.FieldKeyContext)copy._containerContext).getFieldKey();
            FieldKey re = FieldKey.remap(key, parent, remap);
            copy._containerContext = new ContainerContext.FieldKeyContext(re);
        }
        // copy changes backwards
        copy._parsedUrl.setRawQuery(copy._source);
        copy._url = copy._parsedUrl;
        copy._urlSource = null;
        return copy;
    }


    @Override
    public StringExpressionFactory.FieldKeyStringExpression dropParent(String parentName)
    {
        DetailsURL copy = (DetailsURL) super.dropParent(parentName);

        if (copy._containerContext instanceof ContainerContext.FieldKeyContext)
        {
            FieldKey key = ((ContainerContext.FieldKeyContext)copy._containerContext).getFieldKey();
            FieldKey newKey = key.removeParent(parentName);
            if (newKey != null)
                copy._containerContext = new ContainerContext.FieldKeyContext(newKey);
        }

        return copy;
    }


    @Override
    public String eval(Map context)
    {
        String query = super.eval(context);
        if (query == null)
        {
            // Bail out if the context is missing one of the substitutions. Better to have no URL than a URL that's
            // missing parameters
            return null;
        }
        Container c = getContainer(context);
        if (c == null && _strictContainerContextEval)
        {
            return null;
        }
        if (null != c)
            _parsedUrl.setContainer(c);
        return _parsedUrl.getPath() + "?" + query;
    }


    public DetailsURL copy(ContainerContext cc)
    {
        assert this != LINK_DISABLER : "Shouldn't copy a disabled link";
        return copy(cc, false);
    }


    public DetailsURL copy(ContainerContext cc, boolean overwrite)
    {
        assert this != LINK_DISABLER : "Shouldn't copy a disabled link";
        DetailsURL ret = (DetailsURL)copy();
        ret.setContainerContext(cc, overwrite);
        return ret;
    }


    @Override
    public DetailsURL clone()
    {
        assert this != LINK_DISABLER : "Shouldn't clone a disabled link";
        DetailsURL clone = (DetailsURL)super.clone();
        if (null != clone._url)
            clone._url = clone._url.clone();
        if (null != clone._parsedUrl)
            clone._parsedUrl = clone._parsedUrl.clone();
        return clone;
    }

    private ViewContext _context;

    public void setViewContext(ViewContext context)
    {
        assert this != LINK_DISABLER : "Shouldn't set ViewContext on disabled link";
        _context = context;
    }


    public ViewContext getViewContext()
    {
        return _context;
    }


    Container getContainer(Map context)
    {
        if (null != _containerContext)
            return _containerContext.getContainer(context);
        // CONSIDER: get from DataRegion's table: ((RenderContext)context).getCurrentRegion().getTable().getContainerContext()
        if (null != _context)
            return _context.getContainer();
        Object c = null==context ? null
                 : context.containsKey("container") ? context.get("container")
                 : context instanceof RenderContext ? ((RenderContext)context).getContainer()
                 : null;
        if (c instanceof Container)
            return (Container)c;
        return null;
    }

    public void setStrictContainerContextEval(boolean strictContainerContextEval)
    {
        _strictContainerContextEval = strictContainerContextEval;
    }


    public void setContainerContext(ContainerContext cc)
    {
        setContainerContext(cc, true);
    }

    public void setContainerContext(ContainerContext cc, boolean overwrite)
    {
        assert this != LINK_DISABLER : "Shouldn't set ContainerFilter on disabled link";
        if (null == _containerContext || overwrite)
            _containerContext = cc;
    }

    public ContainerContext getContainerContext()
    {
        return _containerContext;
    }

    public ActionURL getActionURL()
    {
        if (null == _parsedUrl)
            parse();
        ActionURL ret = null == _parsedUrl ? null : _parsedUrl.clone();
        if (null != ret)
        {
            Container c = getContainer(null);
            if (null != c)
                ret.setContainer(c);
        }
        return ret;
    }


    @Override
    public String getSource()
    {
        if (null != _urlSource || this == AbstractTableInfo.LINK_DISABLER)
            return _urlSource;
        String controller = _url.getController();
        String action = _url.getAction();
        if (!action.endsWith(".view"))
            action = action + ".view";
        String to = "/" + encode(controller) + "/" + encode(action) + "?" + _url.getQueryString(true);
        assert null == DetailsURL.validateURL(to) : DetailsURL.validateURL(to);
        return to;
    }

    @Override
    public boolean canRender(Set<FieldKey> fieldKeys)
    {
        // Call super so that we don't consider the ContainerContext's column mandatory (we will default to the current
        // container if it's not present)
        return fieldKeys.containsAll(super.getFieldKeys());
    }


    private String encode(String s)
    {
        return PageFlowUtil.encode(s);
    }
}
