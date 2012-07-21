/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.ActionURL;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: migra
 * Date: Dec 28, 2004
 * Time: 4:29:45 PM
 */
public class StringExpressionFactory
{
    private static final Logger LOG = Logger.getLogger(StringExpressionFactory.class);

    private static Cache<String, StringExpression> templates = CacheManager.getCache(1000, CacheManager.DAY, "StringExpression templates");
    private static Cache<String, StringExpression> templatesUrl = CacheManager.getCache(1000, CacheManager.DAY, "StringExpression template URLs");

    public static final StringExpression EMPTY_STRING = new ConstantStringExpression("");


    public static StringExpression create(String str)
    {
        return create(str, false);
    }


    public static StringExpression create(String str, boolean urlEncodeSubstitutions)
    {
        if (StringUtils.isEmpty(str))
            return EMPTY_STRING;

        if (str.indexOf("${") < 0)
            return new ConstantStringExpression(str);

        String key = "simple:" + str + "(" + urlEncodeSubstitutions + ")";

        StringExpression expr = templates.get(key);
        if (null != expr)
            return expr;

        expr = new SimpleStringExpression(str, urlEncodeSubstitutions);
        templates.put(key, expr);
        return expr;
    }



    /**
     * HANDLES two cases
     *
     *  freeform
     *  a) http://*?param=${Column}
     *     free form
     *
     *  b) /Controller/Action.view?param=${Column}
     *     org.labkey.module.Controller$Action.class?param=${Column}\s
     *     special w/ some container support
     *
     * CONSIDER javascript: (permissions!)
     *
     */
    public static StringExpression createURL(String str)
    {
        if (str == null)
            return null;

        StringExpression expr;

        String key = "url:" + str;
        expr = templatesUrl.get(key);
        if (null != expr)
            return expr.copy();

        try
        {
            if (str.startsWith("mailto:"))
                expr = new FieldKeyStringExpression(str);
            else if (StringUtilsLabKey.startsWithURL(str))
                expr = new URLStringExpression(str);
            else if (null == DetailsURL.validateURL(str))
                expr = DetailsURL.fromString(str);
            else
            {
                // improve compatibility with old URLs
                // UNDONE: remove these, or make a new createHelper() 
                ActionURL url = new ActionURL(str);
                if (StringUtils.isEmpty(url.getExtraPath()))
                    expr = new DetailsURL(url);
                else
                    expr = new URLStringExpression(url);
            }
        }
        catch (URISyntaxException x)
        {
            return null;
        }

        templatesUrl.put(key, expr);
        return expr.copy();
    }

    /** Silently swallow any problems with parsing, effectively ignoring the URL if there are any errors */
    public static StringExpression createURLSilent(String str)
    {
        try
        {
            return createURL(str);
        }
        catch (IllegalArgumentException e)
        {
            // We were told to be silent
            return null;
        }
    }


    /** somewhat stricter than createURL() to enforce doc'd syntax (above) */
    public static String validateURL(String str)
    {
        if (str.startsWith("mailto:"))
            return null;
        
        if (str.startsWith("http://") || str.startsWith("https://"))
        {
            try
            {
                new URLHelper(str);
                return null;
            }
            catch (URISyntaxException x)
            {
                return x.getMessage();
            }
        }

        return DetailsURL.validateURL(str);
    }


    public static StringExpression createURL(ActionURL url)
    {
        String key = "url:" + url.getLocalURIString(true);

        StringExpression expr = templates.get(key);
        if (null != expr)
            return expr;

        expr = new URLStringExpression(url);

        templates.put(key, expr);
        return expr;
    }


    //
    // StringExpression implementations
    //
    

    protected static abstract class StringPart implements Cloneable
    {
        /** @return null if the value cannot be resolved given the map */
        @Nullable abstract String getValue(Map map);
        final String valueOf(Object o)
        {
            return o == null ? "" : String.valueOf(o);
        }

        @Override
        public  Object clone()
        {
            try
            {
                return super.clone();
            }
            catch (CloneNotSupportedException x)
            {
                throw new RuntimeException(x);
            }
        }
    }

    private static class ConstantPart extends StringPart
    {
        private String _value;
        public ConstantPart(String value)
        {
            _value = value;
        }
        public String getValue(Map map)
        {
            return _value;
        }
        @Override
        public String toString()
        {
            return _value;
        }
    }
    private static class SubstitutePart extends StringPart
    {
        protected String _value;
        public SubstitutePart(String value)
        {
            _value = value;
        }
        public String getValue(Map map)
        {
            return valueOf(map.get(_value));
        }

        @Override
        public String toString()
        {
            return "${" + _value + "}";
        }
    }
    private static class RenderContextPart extends SubstitutePart
    {
        public RenderContextPart(String value)
        {
            super(value);
            assert "containerPath".equals(value) || "contextPath".equals(value) || "selectionKey".equals(value);
        }

        @Override
        public String getValue(Map map)
        {
            if (!(map instanceof RenderContext))
                return "";

            if ("containerPath".equals(_value))
                return ((RenderContext)map).getContainerPath();
            if ("contextPath".equals(_value))
                return ((RenderContext)map).getContextPath();
            if ("selectionKey".equals(_value))
                return ((RenderContext)map).getSelectionKey();
            return "";
        }

        @Override
        public String toString()
        {
            return super.toString();
        }
    }
    private static class EncodePart extends SubstitutePart
    {
        public EncodePart(String value)
        {
            super(value);
        }
        public String getValue(Map map)
        {
            return PageFlowUtil.encodePath(valueOf(map.get(_value)));
        }
    }

    

    public static abstract class AbstractStringExpression implements StringExpression, Cloneable
    {
        // Ideally, we'd be able to distinguish between null values and missing fields... this would let us output
        // the field part as part of eval (instead of blank) to signal the user that the template is broken.  But only
        // FieldParts know the field, so this is hard.
        public enum NullValueBehavior
        {
            NullResult,                 // Any null field results in a null eval (good for URLs)
            ReplaceNullWithBlank;       // Null or missing fields get replaced with blank
        }

        protected NullValueBehavior _nullValueBehavior = NullValueBehavior.NullResult;
        protected String _source;
        protected ArrayList<StringPart> _parsedExpression = null;

        AbstractStringExpression(String source)
        {
            _source = source;
            //assert MemTracker.put(this);
        }


        protected synchronized ArrayList<StringPart> getParsedExpression()
        {
            if (null == _parsedExpression)
                parse();
            return _parsedExpression;
        }
        

        protected void parse()
        {
            _parsedExpression = new ArrayList<StringPart>();
            int start = 0;
            int index;
            while (start < _source.length() && (index = _source.indexOf("${", start)) >= 0)
            {
                if (index > 0)
                    _parsedExpression.add(new ConstantPart(_source.substring(start, index)));
                int closeIndex = _source.indexOf('}', index + 2);
                String sub = _source.substring(index+2,closeIndex);
                _parsedExpression.add(parsePart(sub));
                start = closeIndex + 1;
            }
            if (start < _source.length())
                _parsedExpression.add(new ConstantPart(_source.substring(start)));
        }

        protected abstract StringPart parsePart(String expr);

        
        public String eval(Map context)
        {
            ArrayList<StringPart> parts = getParsedExpression();
            if (parts.size() == 1)
                return parts.get(0).getValue(context);
            
            StringBuilder builder = new StringBuilder();
            for (StringPart part : parts)
            {
                String value = part.getValue(context);
                if (value == null)
                {
                    switch(_nullValueBehavior)
                    {
                        // Bail out if the context is missing one of the substitutions. Better to have no URL than
                        // a URL that's missing parameters
                        case NullResult:
                            return null;

                        // More lenient... just substitute blank for missing/null
                        case ReplaceNullWithBlank:
                            value = "";
                            break;

                        default:
                            throw new IllegalStateException("Unknown behavior: " + _nullValueBehavior);
                    }
                }
                builder.append(value);
            }
            return builder.toString();
        }


        public String getSource()
        {
            return _source;
        }

        @Override
        public String toString()
        {
            return getSource();
        }

        public void render(Writer out, Map context) throws IOException
        {
            out.write(eval(context));
        }

        @Override
        public AbstractStringExpression clone()
        {
            try
            {
                AbstractStringExpression clone = (AbstractStringExpression)super.clone();
                if (null != clone._parsedExpression)
                {
                    clone._parsedExpression = new ArrayList<StringPart>(clone._parsedExpression);
                    for (int i=0 ; i<clone._parsedExpression.size() ; i++)
                        clone._parsedExpression.set(i, (StringPart)clone._parsedExpression.get(i).clone());
                }
                return clone;
            }
            catch (CloneNotSupportedException x)
            {
                throw new RuntimeException(x);
            }
        }

        public AbstractStringExpression copy()
        {
            return clone();
        }
    }


    
    public static class ConstantStringExpression extends AbstractStringExpression
    {
        ConstantStringExpression(String str)
        {
            super(str);
        }

        protected StringPart parsePart(String expr)
        {
            throw new IllegalArgumentException(this._source);
        }

        @Override
        public String eval(Map map)
        {
            return _source;
        }
    }



    public static class SimpleStringExpression extends AbstractStringExpression
    {
        boolean _urlEncodeSubstitutions = true;

        SimpleStringExpression(String source, boolean urlEncodeSubstitutions)
        {
            super(source);
            _urlEncodeSubstitutions = urlEncodeSubstitutions;
        }
        
        protected StringPart parsePart(String expr)
        {
            if (_urlEncodeSubstitutions)
                return new EncodePart(expr);
            else
                return new SubstitutePart(expr);
        }

//        public void addParameter(String key, String value)
//        {
//            _parsedExpression.add(new ConstantPart("&" + key + "="));
//            if (value.startsWith("${") && value.endsWith("}"))
//            {
//                _parsedExpression.add(parsePart(value.substring(2, value.length() - 3)));
//            }
//            else
//            {
//                _parsedExpression.add(new ConstantPart(value));
//            }
//        }
    }


    private static class FieldPart extends StringPart
    {
        private FieldKey _key;
        private final boolean _urlEncodeSubstitutions;

        FieldPart(String s)
        {
            this(s, true);
        }

        FieldPart(String s, boolean urlEncodeSubstitutions)
        {
            _key = FieldKey.decode(s);
            _urlEncodeSubstitutions = urlEncodeSubstitutions;
        }

        String getValue(Map map)
        {
            Object lookupKey = _key;

            if (!map.containsKey(lookupKey))
            {
                lookupKey = _key.getParent() == null ? _key.getName() : _key.encode();
                if (map.containsKey(lookupKey))
                    LOG.debug("No string substitution found for FieldKey '" + _key.encode() + "', but found String '" + lookupKey + "'.");
            }
            if (!map.containsKey(lookupKey))
            {
                // If we don't have the value at all, return null instead of the empty string to communicate that we
                // don't have the info required to evaluate this expression
                return null;
            }

            String value = valueOf(map.get(lookupKey));

            return _urlEncodeSubstitutions ? PageFlowUtil.encodePath(value) : value;
        }

        @Override
        public String toString()
        {
            return "${" + _key.encode() + "}";
        }
    }


    public static class FieldKeyStringExpression extends AbstractStringExpression
    {
        private final boolean _urlEncodeSubstitutions;

        protected FieldKeyStringExpression()
        {
            this("");
        }
        
        protected FieldKeyStringExpression(String source)
        {
            this(source, true, NullValueBehavior.NullResult);
        }

        public FieldKeyStringExpression(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior)
        {
            super(source);
            _urlEncodeSubstitutions = urlEncodeSubstitutions;
            _nullValueBehavior = nullValueBehavior;
        }

        public static FieldKeyStringExpression create(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior)
        {
            return new FieldKeyStringExpression(source, urlEncodeSubstitutions, nullValueBehavior);
        }

        protected StringPart parsePart(String expr)
        {
            // HACK
            if ("containerPath".equals(expr) || "contextPath".equals(expr) || "selectionKey".equals(expr))
                return new RenderContextPart(expr);
            return new FieldPart(expr, _urlEncodeSubstitutions);
        }

        /**
         * Used to fix up column names when a table is referred to via a lookup.
         * E.g. consider column lk in table A, which joins to pk in table B
         * NOTE: original StringExpression is unchanged, it is cloned and the clone is modified
         *
         * @param parent   title -> lk/title
         * @param remap    pk -> fk
         * @return clone of original StringExpressions with updated fieldkey substitutions
         */
        public FieldKeyStringExpression remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap)
        {
            FieldKeyStringExpression clone = this.clone();
            StringBuilder source = new StringBuilder();
            for (StringPart p : clone.getParsedExpression())
            {
                if (p instanceof FieldPart)
                {
                    FieldPart fp = (FieldPart)p;
                    fp._key = _remap(fp._key, parent, remap);
                }
                source.append(p.toString());
            }
            clone._source = source.toString();
            return clone;
        }


        protected FieldKey _remap(FieldKey key, FieldKey parent, Map<FieldKey,FieldKey> remap)
        {
            FieldKey replace = remap == null ? null : remap.get(key);
            if (null != replace)
                return replace;
            else if (null != parent)
                return FieldKey.fromParts(parent, key);
            // TODO I think we want this to be strict sometimes, and return null
            return key;
        }


        /**
         * @param set set of FieldKeys
         * @return true if set contains all substitutions in this string expression
         */
        public boolean validateFieldKeys(Set<FieldKey> set)
        {
            Set<FieldKey> keys = getFieldKeys();
            return set.containsAll(keys);
        }

        public Set<FieldKey> getFieldKeys()
        {
            Set<FieldKey> set = new HashSet<FieldKey>();
            for (StringPart p : getParsedExpression())
            {
                if (p instanceof FieldPart)
                    set.add(((FieldPart)p)._key);
            }
            return set;
        }

        @Override
        public FieldKeyStringExpression clone()
        {
            return (FieldKeyStringExpression)super.clone();
        }

        /**
         * Remove the specified parent prefix from all FieldKeys
         * @return a modified copy, the original is not mutated
         */
        public FieldKeyStringExpression dropParent(String parentName)
        {
            FieldKeyStringExpression clone = this.clone();
            StringBuilder source = new StringBuilder();

            for (StringPart stringPart : clone.getParsedExpression())
            {
                if (stringPart instanceof FieldPart)
                {
                    FieldPart fieldPart = (FieldPart)stringPart;
                    List<String> parts = fieldPart._key.getParts();
                    // If it the first part of the FieldKey matches the parent name
                    if (parts.get(0).equals(parentName))
                    {
                        FieldKey newFieldKey = null;
                        for (int i = 1; i < parts.size(); i++)
                        {
                            // Copy all of the subsequent parts
                            newFieldKey = new FieldKey(newFieldKey, parts.get(i));
                        }
                        fieldPart._key = newFieldKey;
                    }
                }
                source.append(stringPart.toString());
            }
            clone._source = source.toString();
            return clone;
        }
    }


    /**
     *  Same as FieldKeyExpression, but validates !startsWith(javascript:)
     * additional constructor
     */
    public static class URLStringExpression extends FieldKeyStringExpression
    {
        public URLStringExpression(String source) throws URISyntaxException
        {
            super("");
            _source = source.trim();
            new URLHelper(_source);
        }

        public URLStringExpression(ActionURL url)
        {
            super("");
            _source = url.getLocalURIString(true);
        }

        @Override
        public String eval(Map context)
        {
            String ret = super.eval(context);
            int i = StringUtils.indexOfAny(ret, ": /");
            if (i != -1 && ret.charAt(i) == ':')
            {
                int s = ret.indexOf("script");
                if (s > -1 && s < i)
                    return null;
            }
            return ret;
        }
    }


    protected static String getURIString(URLHelper url, URLHelper base)
    {
        if (null != base)
        {
            if (StringUtils.isEmpty(url.getScheme()))
                url.setScheme(base.getScheme());
            if (StringUtils.isEmpty(url.getHost()))
                url.setHost(base.getHost());
            if (url.getPort() == -1)
                url.setPort(base.getPort());
        }
        if (StringUtils.isEmpty(url.getHost()) || StringUtils.isEmpty(url.getScheme()))
            return url.getLocalURIString(true);
        else
            return url.getURIString(true);
    }


    /** example */
    public static class ScriptEngineStringExpression extends AbstractStringExpression
    {
        ScriptEngine _engine;

        class ScriptPart extends SubstitutePart
        {
            ScriptPart(String value)
            {
                super(value);
            }
            public String getValue(Map map)
            {
                if (!(map instanceof Bindings))
                    throw new IllegalArgumentException();
                try
                {
                    return valueOf(_engine.eval(_value, (Bindings)map));
                }
                catch (ScriptException x)
                {
                    throw new RuntimeException(x);
                }
            }
        }
        
        ScriptEngineStringExpression(String source, ScriptEngine engine)
        {
            super(source);
            _engine = engine;
        }

        protected StringPart parsePart(String expr)
        {
            return new ScriptPart(expr);
        }
    }



    public static class TestCase extends Assert
    {
        @Test
        public void testSimple() throws ServletException
        {
            Map<Object,Object> m = new HashMap<Object,Object>();

            StringExpression a = StringExpressionFactory.create("${one} ${and} ${two} = ${three}");
            m.put("and", "y");
            m.put("one", "uno");
            m.put("two", "dos");
            m.put("three", "tres");

            assertEquals("uno y dos = tres", a.eval(m));

            StringExpression b = StringExpressionFactory.create("${contextPath}/controller${containerPath}/details.view?id=${rowId}&label=${label}", true);
            m.put("contextPath","/labkey");
            m.put("containerPath","/home");
            m.put("rowId",5);
            m.put("label","%encode me%");
            assertEquals("/labkey/controller/home/details.view?id=5&label=%25encode%20me%25", b.eval(m));


            FieldKeyStringExpression fkse = new FieldKeyStringExpression("details.view?id=${rowid}&title=${title}");
            m.put(FieldKey.fromParts("A","rowid"), "BUG");
            m.put(new FieldKey(null, "lookup"), 5);
            m.put(FieldKey.fromParts("A","title"), "title one");
            Map<FieldKey,FieldKey> remap = new HashMap<FieldKey, FieldKey>();
            remap.put(new FieldKey(null,"rowid"), new FieldKey(null,"lookup"));
            FieldKeyStringExpression lookup = fkse.remapFieldKeys(new FieldKey(null, "A"), remap);
            assertEquals("details.view?id=5&title=title%20one", lookup.eval(m));
        }
    }


    /* UNDONE: can't distinguish simple string expression and a custom URL expression */
    public static class Converter implements org.apache.commons.beanutils.Converter
    {
        public Object convert(Class type, Object value)
        {
            if (value == null || value instanceof StringExpression)
                return value;

            return StringExpressionFactory.createURL(String.valueOf(value));
        }
    }
}
