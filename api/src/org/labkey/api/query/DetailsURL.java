/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.apache.commons.lang.StringUtils;

import java.util.*;

public class DetailsURL extends StringExpressionFactory.URLStringExpression
{
    Container _container;
    ActionURL _baseURL;     // url w/o substitution parameters


    public DetailsURL(String str)
    {
        super(str);
    }


    public DetailsURL(Container c, String str)
    {
        super(str);
        _container = c;
    }


    public static DetailsURL fromString(String str)
    {
        return new DetailsURL(str);
    }

    
    public static DetailsURL fromString(Container c, String str)
    {
        return new DetailsURL(c, str);
    }
    
    
    @Override
    protected Container getContainer()
    {
        return _container;
    }


    public DetailsURL(ActionURL baseURL, Map<String,? extends Object> columnParams)
    {
        super(baseURL);
        _baseURL = baseURL.clone();
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
            _url.addParameter(e.getKey(), "${" + strValue + "}");
        }
    }


    public DetailsURL(ActionURL baseURL, String param, FieldKey subst)
    {
        super(baseURL);
        _baseURL = baseURL.clone();
        _url.addParameter(param, "${" + subst.encode() + "}");
    }


    public DetailsURL(String controller, String action, Container container, Map<String, String> fixedParams, Map<String, String> columnParams)
    {
        super(new ActionURL(controller, action, container));
        for (Map.Entry<String,String> e : fixedParams.entrySet())
            _url.addParameter(e.getKey(), e.getValue());
        _baseURL = _url.clone();
        for (Map.Entry<String,String> e : columnParams.entrySet())
            _url.addParameter(e.getKey(), "${" + e.getValue() + "}");
    }


    /** @deprecated use FieldKeyStringExpression.validateFields() and copy(c) */
    @Deprecated
    public StringExpression getURL(Map<String,ColumnInfo> columns, Container c)
    {
        Set<FieldKey> keys = new HashSet<FieldKey>();
        for (String s : columns.keySet())
            keys.add(new FieldKey(null,s));
        if (!validateFieldKeys(keys))
            return null;
        return copy(c);
    }


    public DetailsURL copy(Container c)
    {
        DetailsURL ret = (DetailsURL)copy();
        if (null != c)
        {
            ret._container = c;
            if (null != _baseURL)
                ret._baseURL.setContainer(c);
            if (null != _url)
                ret._url.setContainer(c);
        }
        return ret;
    }


    @Override
    public DetailsURL clone()
    {
        DetailsURL clone = (DetailsURL)super.clone();
        clone._baseURL = _baseURL == null ? null : _baseURL.clone();
        return clone;
    }
}
