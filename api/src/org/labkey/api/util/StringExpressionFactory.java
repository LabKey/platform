/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import org.labkey.api.collections.CacheMap;
import org.labkey.api.collections.LimitedCacheMap;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.query.FieldKey;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * User: migra
 * Date: Dec 28, 2004
 * Time: 4:29:45 PM
 */
public class StringExpressionFactory
{
    private static CacheMap<Pair<String, Boolean>, StringExpression> templates = new LimitedCacheMap<Pair<String, Boolean>, StringExpression>(1000, 1000);
    public static final StringExpression NULL_STRING = new ConstantStringExpression(null);
    public static final StringExpression EMPTY_STRING = new ConstantStringExpression("");


    public static StringExpression create(String str)
    {
        return create(str, Collections.EMPTY_MAP, false);
    }


    public static StringExpression create(String str, boolean urlEncodeSubstitutions)
    {
        return create(str, Collections.EMPTY_MAP, urlEncodeSubstitutions);
    }


    /** map from column.getName() to column.getAlias() */
    public static StringExpression createMapColumns(String str, Collection<ColumnInfo> cols, boolean urlEncodeSubstitutions)
    {
        Map<FieldKey,String> map = new HashMap<FieldKey, String>(cols.size()*2);
        for (ColumnInfo col : cols)
            map.put(FieldKey.fromString(col.getName()), col.getAlias());
        return create(str, map, urlEncodeSubstitutions);
    }


    /** map from FieldKey to column.getAlias() */
    public static StringExpression createMapFields(String str, Map<FieldKey,ColumnInfo> cols, boolean urlEncodeSubstitutions)
    {
        Map<FieldKey,String> map = new HashMap<FieldKey, String>(cols.size()*2);
        for (Map.Entry<FieldKey,ColumnInfo> entry : cols.entrySet())
            map.put(entry.getKey(), entry.getValue().getAlias());
        return create(str, map, urlEncodeSubstitutions);
    }


    public static StringExpression createMapStrings(String str, Map<String,String> strings, boolean urlEncodeSubstitutions)
    {
        Map<FieldKey,String> map = new HashMap<FieldKey, String>(strings.size()*2);
        for (Map.Entry<String,String> entry : strings.entrySet())
            map.put(new FieldKey(null, entry.getKey()), entry.getValue());
        return create(str, map, urlEncodeSubstitutions);
    }


    public static StringExpression create(String str, Map<FieldKey,String> fkMap, boolean urlEncodeSubstitutions)
    {
        if (null == str)
            return NULL_STRING;

        if (str.indexOf("<%") < 0 && str.indexOf("${") < 0)
            return new ConstantStringExpression(str);

        Pair<String, Boolean> key = new Pair<String, Boolean>(str + "?" + PageFlowUtil.toQueryString(fkMap.entrySet()), urlEncodeSubstitutions);

        StringExpression expr = templates.get(key);
        if (null != expr)
            return expr;

        expr = new GStringExpression(str, fkMap, urlEncodeSubstitutions);
        templates.put(key, expr);
        return expr;
    }


    private static class ConstantStringExpression implements StringExpression
    {
        String str;

        ConstantStringExpression(String str)
        {
            this.str = str;
        }

        public String eval(Map map)
        {
            return str;
        }

        public String getSource()
        {
            return str;
        }

        public String toString()
        {
            return str;
        }

        public void addParameter(String key, String value)
        {
            throw new UnsupportedOperationException();
        }

        public void render(Writer out, Map map) throws IOException
        {
            out.write(str);
        }
    }


    private static class GStringExpression implements StringExpression
    {
        private static class StringPortion
        {
            private String _value;
            private boolean _isSubstitution;
            public StringPortion(String value, boolean isReplacement)
            {
                _value = value;
                _isSubstitution = isReplacement;
            }
            public boolean isSubstitution()
            {
                return _isSubstitution;
            }
            public String getValue()
            {
                return _value;
            }
        }

        private List<StringPortion> _parsedExpression = new ArrayList<StringPortion>();
        private String _source;
        private boolean _urlEncodeSubstitutions;



        GStringExpression(String source, Map<FieldKey,String> map, boolean urlEncodeSubstitutions)
        {
            _source = source;
            _urlEncodeSubstitutions = urlEncodeSubstitutions;
            int start = 0;
            int index;
            while (start < source.length() && (index = source.indexOf("${", start)) >= 0)
            {
                if (index > 0)
                    _parsedExpression.add(new StringPortion(source.substring(start, index), false));
                int closeIndex = source.indexOf('}', index + 2);
                String sub = source.substring(index+2,closeIndex);
                String rep = map.get(FieldKey.decode(sub));
                _parsedExpression.add(new StringPortion(null!=rep?rep:sub, true));
                start = closeIndex + 1;
            }
            if (start < source.length())
                _parsedExpression.add(new StringPortion(source.substring(start), false));
        }


        public void addParameter(String key, String value)
        {
            _parsedExpression.add(new StringPortion("&" + key + "=", false));
            if (value.startsWith("${") && value.endsWith("}"))
            {
                _parsedExpression.add(new StringPortion(value.substring(2, value.length() - 3), true));
            }
            else
            {
                _parsedExpression.add(new StringPortion(value, false));
            }
        }


        public String eval(Map context)
        {
            ViewContext viewContext = null;
            if (context instanceof ViewContext)
            {
                viewContext = (ViewContext)context;
            }
            else if (context instanceof RenderContext)
            {
                viewContext = ((RenderContext)context).getViewContext();
            }
            return eval(viewContext, context);
        }


        public String eval(ViewContext viewContext, Map context)
        {
            StringBuilder builder = new StringBuilder();
            for (StringPortion portion : _parsedExpression)
            {
                if (portion.isSubstitution())
                {
                    String key = portion.getValue();
                    String s = null;

                    if (viewContext != null)
                    {
                        if (key.equalsIgnoreCase("contextPath"))
                            s = viewContext.getContextPath();
                        else if (key.equalsIgnoreCase("containerPath"))
                            s = viewContext.getContainer().getPath();
                    }
                    if (s == null)
                        s = String.valueOf(context.get(key));
                    if (_urlEncodeSubstitutions)
                        s = PageFlowUtil.encode(s);
                    builder.append(s);
                }
                else
                    builder.append(portion.getValue());
            }
            return builder.toString();
        }


        public String getSource()
        {
            return _source;
        }


        public String toString()
        {
            return _source;
        }

        public void render(Writer out, Map context) throws IOException
        {
            out.write(eval(context));
        }
    }
}
