/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;


public class DetailsURL extends StringExpressionFactory.FieldKeyStringExpression implements HasViewContext
{
    public static Pattern actionPattern = Pattern.compile("/?[\\w\\-]+/[\\w\\-]+.view?.*");
    public static Pattern classPattern = Pattern.compile("[\\w\\.\\$]+\\.class?.*");

    Container _container;

    // constructor parameters
    ActionURL _url;
    String _urlSource;

    // parsed fields
    ActionURL _parsedUrl;
    // _source from AbstractStringExpression            


    public static String validateURL(String str)
    {
        if (DetailsURL.actionPattern.matcher(str).matches() || DetailsURL.classPattern.matcher(str).matches())
            return null;

        return "invalid url pattern " + str;
    }


    public static DetailsURL fromString(String str)
    {
        DetailsURL ret = new DetailsURL(str);
        try
        {
            ret.parse();
        }
        catch (IllegalStateException x)
        {
            // ignore during startup
        }
        return ret;
    }


    public static DetailsURL fromString(Container c, String str)
    {
        DetailsURL ret = new DetailsURL(c, str);
        ret.parse();    // validate
        return ret;
    }


    protected DetailsURL(String str)
    {
        _urlSource = str;
    }


    protected DetailsURL(Container c, String str)
    {
        _urlSource = str;
        _container = c;
    }


    public DetailsURL(ActionURL url)
    {
        _url = url.clone();
    }


    public DetailsURL(ActionURL url, Map<String,? extends Object> columnParams)
    {
        url = url.clone();
        for (Map.Entry<String,? extends Object> e : columnParams.entrySet())
        {
            Object v = e.getValue();
            String strValue;
            if (v instanceof String)
                strValue = (String)v;
            else if (v instanceof FieldKey)
                strValue = ((FieldKey)v).encode();
            else if (v instanceof ColumnInfo)
                strValue = ((ColumnInfo)v).getFieldKey().encode();
            else
                throw new IllegalArgumentException(String.valueOf(v));
            url.addParameter(e.getKey(), "${" + strValue + "}");
        }
        _url = url;
    }


    public DetailsURL(ActionURL baseURL, String param, FieldKey subst)
    {
        this(baseURL, Collections.singletonMap(param,subst));
    }


    @Override
    protected void parse()
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
                throw new IllegalArgumentException(expr);

            if (actionPattern.matcher(expr).matches())
            {
                if (!expr.startsWith("/")) expr = "/" + expr;
                _parsedUrl = new ActionURL(expr);
            }
            else if (classPattern.matcher(expr).matches())
            {
                String className = expr.substring(0,expr.indexOf(".class?"));
                Class<Controller> cls;
                try { cls = (Class<Controller>)Class.forName(className); } catch (Exception x) {throw new IllegalArgumentException(expr);}
                _parsedUrl = new ActionURL(cls, null);
                _parsedUrl.setRawQuery(expr.substring(expr.indexOf('?')+1));
            }
            else
                throw new IllegalArgumentException(_urlSource);
        }
        else
            throw new IllegalStateException();
            
        _source = StringUtils.trimToEmpty(_parsedUrl.getQueryString(true));

        super.parse();
    }


    @Override
    public DetailsURL addParent(FieldKey parent, Map<FieldKey, FieldKey> remap)
    {
        DetailsURL copy = (DetailsURL)super.addParent(parent, remap);
        // copy changes backwards
        copy._parsedUrl.setRawQuery(copy._source);
        copy._url = copy._parsedUrl;
        copy._urlSource = null;
        return copy;
    }


    @Override
    public String eval(Map context)
    {
        String query = super.eval(context);
        Container c = getContainer();
        if (null != c)
            _parsedUrl.setContainer(getContainer());
        return _parsedUrl.getPath() + "?" + query;
    }


    public DetailsURL copy(Container c)
    {
        DetailsURL ret = (DetailsURL)copy();
        if (null != c)
            ret._container = c;
        return ret;
    }


    @Override
    public DetailsURL clone()
    {
        DetailsURL clone = (DetailsURL)super.clone();
        if (null != clone._url)
            clone._url = clone._url.clone();
        if (null != clone._parsedUrl)
            clone._parsedUrl = clone._parsedUrl.clone();
        return clone;
    }

    ViewContext _context;

    public void setViewContext(ViewContext context)
    {
        _context = context;
    }


    public ViewContext getViewContext()
    {
        return _context;
    }


    Container getContainer()
    {
        if (null != _container)
            return _container;
        if (null != _context)
            return _context.getContainer();
        return null;
    }


    void setContainer(Container c)
    {
        _container = c;
    }


    public ActionURL getActionURL()
    {
        if (null == _parsedUrl)
            parse();
        return null == _parsedUrl ? null : _parsedUrl.clone();
    }


    @Override
    public String toString()
    {
        if (null != _urlSource)
            return _urlSource;
        String controller = _url.getPageFlow();
        String action = _url.getAction();
        if (!action.endsWith(".view"))
            action = action + ".view";
        String to = "/" + encode(controller) + "/" + encode(action) + "?" + _url.getQueryString(true);
        assert null == DetailsURL.validateURL(to);
        return to;
    }
    

    private String encode(String s)
    {
        return PageFlowUtil.encode(s);
    }
}
