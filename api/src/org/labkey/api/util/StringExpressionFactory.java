/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public interface StringExpression
    {
        public String eval(Map ctx);

        public String getSource();

        public void render(Writer out, Map ctx) throws IOException;
    }

    public static StringExpression create(String str)
    {
        return create(str, false);
    }

    public static StringExpression create(String str, boolean urlEncodeSubstitutions)
    {
        if (null == str)
            return NULL_STRING;

        if (str.indexOf("<%") < 0 && str.indexOf("${") < 0)
            return new ConstantStringExpression(str);

        Pair<String, Boolean> key = new Pair<String, Boolean>(str, urlEncodeSubstitutions);

        StringExpression expr = templates.get(key);
        if (null != expr)
            return expr;

        expr = new GStringExpression(str, urlEncodeSubstitutions);
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

        GStringExpression(String source, boolean urlEncodeSubstitutions)
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
                _parsedExpression.add(new StringPortion(source.substring(index + 2, closeIndex), true));
                start = closeIndex + 1;
            }
            if (start < source.length())
                _parsedExpression.add(new StringPortion(source.substring(start), false));
        }

        public String eval(Map context)
        {
            StringBuilder builder = new StringBuilder();
            for (StringPortion portion : _parsedExpression)
            {
                if (portion.isSubstitution())
                {
                    Object o = context.get(portion.getValue());
                    String s = o == null ? "null" : o.toString();
                    if (_urlEncodeSubstitutions)
                    {
                        s = PageFlowUtil.encode(s);
                    }
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
