/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.ActionURL;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
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

    private static Cache<String, StringExpression> templates = CacheManager.getCache(5000, CacheManager.DAY, "StringExpression templates");
    private static Cache<String, StringExpression> templatesUrl = CacheManager.getCache(5000, CacheManager.DAY, "StringExpression template URLs");

    public static final StringExpression EMPTY_STRING = new ConstantStringExpression("");


    public static StringExpression create(String str)
    {
        return create(str, false);
    }


    public static StringExpression create(String str, boolean urlEncodeSubstitutions)
    {
        if (StringUtils.isEmpty(str))
            return EMPTY_STRING;

        if (!str.contains("${"))
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
     * HANDLES three cases
     *
     *  a) http[s]://*?param=${Column}
     *
     *  b) /Controller/Action.view?param=${Column}
     *     org.labkey.module.Controller$Action.class?param=${Column}\s
     *     special w/ some container support
     *
     *  c) freeform, whatever
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
            {
                expr = new URLStringExpression(str);
            }
            else if (null == DetailsURL.validateURL(str))
            {
                expr = DetailsURL.fromString(str);
            }
            else
            {
                // improve compatibility with old URLs
                // UNDONE: remove these, or make a new createHelper()
                try
                {
                    ActionURL url = new ActionURL(str);
                    if (StringUtils.isEmpty(url.getExtraPath()))
                        expr = new DetailsURL(url);
                }
                catch (IllegalArgumentException x)
                {
                    //
                }
                if (null == expr)
                    expr = new URLStringExpression(str);
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


    public static String validateURL(String str)
    {
        if (str.startsWith("mailto:"))
            return null;

        StringExpression s = StringExpressionFactory.createURL(str);
        if (null != s)
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
        final protected SubstitutionFormat _substitutionFormat;

        public SubstitutePart(String value, boolean urlEncodeSubstitutions)
        {
            // Does the string end with a known format function? If so, save it for later and trim it off the string.
            int colon = value.lastIndexOf(':');
            SubstitutionFormat format = null;

            if (colon > -1)
            {
                format = SubstitutionFormat.getFormat(value.substring(colon + 1));

                if (null != format)
                    value = value.substring(0, colon);
            }

            _value = value;
            _substitutionFormat = (null != format ? format : urlEncodeSubstitutions ? SubstitutionFormat.urlEncode : SubstitutionFormat.passThrough);
        }

        public SubstitutePart(String value, SubstitutionFormat sf)
        {
            _value = value;
            _substitutionFormat = sf;
        }

        public String getValue(Map map)
        {
            String s = valueOf(map.get(_value));
            return _substitutionFormat.format(s);
        }

        @Override
        public String toString()
        {
            return "${" + _value + "}";
        }
    }


    private static class RenderContextPart extends SubstitutePart
    {
        public enum Substitution
        {
            schemaName
            {
                @Override
                public String getValue(RenderContext context)
                {
                    DataRegion region = context.getCurrentRegion();
                    if (region != null)
                    {
                        TableInfo table = region.getTable();
                        if (table != null)
                        {
                            return table.getPublicSchemaName();
                        }
                    }
                    return "";
                }
            },
            queryName
            {
                @Override
                public String getValue(RenderContext context)
                {
                    DataRegion region = context.getCurrentRegion();
                    if (region != null)
                    {
                        TableInfo table = region.getTable();
                        if (table != null)
                        {
                            return table.getPublicName();
                        }
                    }
                    return "";
                }
            },
            dataRegionName
                    {
                        @Override
                        public String getValue(RenderContext context)
                        {
                            DataRegion region = context.getCurrentRegion();
                            if (region != null)
                            {
                                return region.getName();
                            }
                            return "";
                        }
                    },
            containerPath
            {
                @Override
                public String getValue(RenderContext context)
                {
                    return context.getContainerPath();
                }
            },
            contextPath
            {
                @Override
                public String getValue(RenderContext context)
                {
                    return context.getContextPath();
                }
            },
            selectionKey
            {
                @Override
                public String getValue(RenderContext context)
                {
                    return context.getSelectionKey();
                }
            };

            public abstract String getValue(RenderContext context);
        }

        public static final Set<String> SUPPORTED_SUBSTITUTIONS;

        static
        {
            Set<String> s = new HashSet<>(Substitution.values().length);
            for (Substitution substitution : Substitution.values())
            {
                s.add(substitution.toString());
            }
            SUPPORTED_SUBSTITUTIONS = Collections.unmodifiableSet(s);
        }

        public RenderContextPart(String value)
        {
            super(value, SubstitutionFormat.urlEncode);
            assert SUPPORTED_SUBSTITUTIONS.contains(value);
        }

        @Override
        public String getValue(Map map)
        {
            if (!(map instanceof RenderContext))
                return "";

            String s = Substitution.valueOf(_value).getValue((RenderContext)map);
            return _substitutionFormat.format(s);
        }

        @Override
        public String toString()
        {
            return super.toString();
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
            ReplaceNullWithBlank,       // Null or missing fields get replaced with blank
            OutputNull                  // Insert "null" into the string
        }

        protected NullValueBehavior _nullValueBehavior = NullValueBehavior.NullResult;
        protected String _source;
        protected ArrayList<StringPart> _parsedExpression = null;

        AbstractStringExpression(String source)
        {
            _source = source;
            //MemTracker.getInstance().put(this);
        }


        protected synchronized ArrayList<StringPart> getParsedExpression()
        {
            if (null == _parsedExpression)
                parse();
            return _parsedExpression;
        }
        

        protected void parse()
        {
            _parsedExpression = new ArrayList<>();
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

                        // Output "null"
                        case OutputNull:
                            value = "null";
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
                    clone._parsedExpression = new ArrayList<>(clone._parsedExpression);
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

        @Override
        public boolean canRender(Set<FieldKey> fieldKeys)
        {
            return true;
        }

        public AbstractStringExpression addParameter(String key, String value)
        {
            if (_parsedExpression == null)
                parse();

            AbstractStringExpression copy = copy();
            copy._parsedExpression.add(new ConstantPart("&" + key + "="));
            if (value.startsWith("${") && value.endsWith("}"))
            {
                copy._parsedExpression.add(parsePart(value.substring(2, value.length() - 1)));
            }
            else
            {
                copy._parsedExpression.add(new ConstantPart(value));
            }
            return copy;
        }

        @Nullable
        @Override
        public Object getJdbcParameterValue()
        {
            return getSource();
        }

        @NotNull
        @Override
        public JdbcType getJdbcParameterType()
        {
            return JdbcType.VARCHAR;
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
            return new SubstitutePart(expr, _urlEncodeSubstitutions);
        }
    }


    private static class FieldPart extends SubstitutePart
    {
        @NotNull private FieldKey _key;

        FieldPart(@NotNull String s, boolean urlEncodeSubstitutions)
        {
            super(s, urlEncodeSubstitutions);
            _key = FieldKey.decode(_value);
            if (_key == null)
            {
                throw new IllegalArgumentException("Could not parse FieldKey from '" + s + "'");
            }
        }

        FieldPart(@NotNull FieldKey key, SubstitutionFormat sf)
        {
            super(key.toString(), sf);
            _key = key;
        }

        public String getValue(Map map)
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

            String s = valueOf(map.get(lookupKey));
            return _substitutionFormat.format(s);
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
            if (RenderContextPart.SUPPORTED_SUBSTITUTIONS.contains(expr))
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
                    fp._key = FieldKey.remap(fp._key, parent, remap);
                }
                source.append(p.toString());
            }
            clone._source = source.toString();
            return clone;
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
            Set<FieldKey> set = new HashSet<>();
            for (StringPart p : getParsedExpression())
            {
                if (p instanceof FieldPart)
                    set.add(((FieldPart)p)._key);
            }
            return set;
        }

        @Override
        public boolean canRender(Set<FieldKey> fieldKeys)
        {
            return fieldKeys.containsAll(getFieldKeys());
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
                    // Removes the parent if the first part of the FieldKey matches the parent name
                    FieldKey newFieldKey = fieldPart._key.removeParent(parentName);
                    if (newFieldKey != null)
                    {
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
        }

        public URLStringExpression(ActionURL url)
        {
            super("");
            _source = url.getLocalURIString(true);
        }

        @Override
        protected void parse()
        {
            super.parse();
            // special case if entire pattern consists of one substitution, don't encode
            if (1 == _parsedExpression.size())
            {
                StringPart p = _parsedExpression.get(0);
                if (p instanceof FieldPart)
                {
                    FieldPart fp = (FieldPart)p;
                    _parsedExpression.set(0,new FieldPart(fp._key,SubstitutionFormat.passThrough));
                }
            }
        }

        @Override
        public String eval(Map context)
        {
            String ret = super.eval(context);
            if (null == ret)
                return null;
            int i = StringUtils.indexOfAny(ret, ": /");
            if (-1 == i)
                i = ret.length();
            int s = ret.indexOf("script");
            if (s > -1 && s < i)
                return null;
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


    public static class TestCase extends Assert
    {
        @Test
        public void testSimple() throws ServletException
        {
            Map<Object,Object> m = new HashMap<>();

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
        }

        @Test
        public void testFieldKey()
        {
            Map<Object,Object> m = new HashMap<>();

            FieldKeyStringExpression fkse = new FieldKeyStringExpression("details.view?id=${rowid}&title=${title}");
            m.put(FieldKey.fromParts("A","rowid"), "BUG");
            m.put(new FieldKey(null, "lookup"), 5);
            m.put(FieldKey.fromParts("A","title"), "title one");
            Map<FieldKey,FieldKey> remap = new HashMap<>();
            remap.put(new FieldKey(null,"rowid"), new FieldKey(null,"lookup"));
            FieldKeyStringExpression lookup = fkse.remapFieldKeys(new FieldKey(null, "A"), remap);
            assertEquals("details.view?id=5&title=title%20one", lookup.eval(m));
        }


        @Test public void testEncoding()
        {
            {
                StringExpression se = FieldKeyStringExpression.create("${html:htmlEncode}|${pass:passThrough}|${url:urlEncode}", false, AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);
                Map<Object,Object> m = new HashMap<>();
                m.put(new FieldKey(null,"html"),"<script></script>");
                m.put(new FieldKey(null,"pass"),"<pass>10%</pass>");
                m.put(new FieldKey(null,"url"),"<url>10%</url>");
                String s = se.eval(m);
                assertEquals("&lt;script&gt;&lt;/script&gt;|<pass>10%</pass>|%3Curl%3E10%25%3C/url%3E",s);
            }

            {
                StringExpression se = StringExpressionFactory.create("${html:htmlEncode}|${pass:passThrough}|${url:urlEncode}", false);
                Map<Object,Object> m = new HashMap<>();
                m.put("html","<script></script>");
                m.put("pass","<pass>10%</pass>");
                m.put("url","<url>10%</url>");
                String s = se.eval(m);
                assertEquals("&lt;script&gt;&lt;/script&gt;|<pass>10%</pass>|%3Curl%3E10%25%3C/url%3E",s);
            }
        }

        @Test
        public void testCreateUrl()
        {
            Container container = JunitUtil.getTestContainer();
            String containerPath = container.getPath();
            String contextPath = AppProps.getInstance().getContextPath();
            ActionURL url = new ActionURL("controller","action",container);
            ActionURL urlBegin = new ActionURL("project","begin",container);
            String s;
            Map<FieldKey,Object> m = new HashMap<>();

            /* test auto-detect of details URL, 'old' details url, mailto:, etc etc */
            StringExpression a = StringExpressionFactory.createURL("mailto:tester@test.labkey.com");
            assertTrue(a instanceof FieldKeyStringExpression);
            assertEquals("mailto:tester@test.labkey.com", a.eval(Collections.emptyMap()));

            StringExpression b = StringExpressionFactory.createURL("http://www.labkey.com/");
            assertTrue(b instanceof URLStringExpression);
            assertEquals("http://www.labkey.com/", b.eval(Collections.emptyMap()));

            StringExpression c = StringExpressionFactory.createURL("https://www.labkey.com/");
            assertTrue(c instanceof URLStringExpression);
            assertEquals("https://www.labkey.com/", c.eval(Collections.emptyMap()));

            StringExpression d = StringExpressionFactory.createURL("ftp://www.labkey.com/");
            assertTrue(d instanceof URLStringExpression);
            assertEquals("ftp://www.labkey.com/", d.eval(Collections.emptyMap()));

            StringExpression e = StringExpressionFactory.createURL("/controller/action.view");
            assertTrue(e instanceof DetailsURL);
            ((DetailsURL)e).setContainerContext(container);
            s = e.eval(Collections.emptyMap());
            assertEquals(url.getLocalURIString(), s);

            StringExpression f = StringExpressionFactory.createURL("org.labkey.core.portal.ProjectController$BeginAction.class");
            assertTrue(f instanceof DetailsURL);
            ((DetailsURL)f).setContainerContext(container);
            s = f.eval(Collections.emptyMap());
            assertEquals(urlBegin.getLocalURIString(), s);

            StringExpression g = StringExpressionFactory.createURL("org.labkey.core.portal.ProjectController$BeginAction.class?q=labkey");
            assertTrue(g instanceof DetailsURL);
            ((DetailsURL)g).setContainerContext(container);
            s = g.eval(Collections.emptyMap());
            urlBegin.addParameter("q","labkey");
            assertEquals(urlBegin.getLocalURIString(), s);

            m.put(new FieldKey(null,"h"),"http://www.labkey.com/");
            StringExpression h = StringExpressionFactory.createURL("${h}");
            assertTrue(h instanceof URLStringExpression);
            s = h.eval(m);
            assertEquals("http://www.labkey.com/",s);

            m.put(new FieldKey(null,"i"),"javascript://www.labkey.com");
            StringExpression i = StringExpressionFactory.createURL("${i}");
            assertTrue(i instanceof URLStringExpression);
            s = i.eval(m);
            assertNull(s);

            StringExpression j = StringExpressionFactory.createURL("${i:urlEncode}");
            assertTrue(j instanceof URLStringExpression);
            s = j.eval(m);
            assertNull(s);

            StringExpression k = StringExpressionFactory.createURL("${i:htmlEncode}");
            assertTrue(k instanceof URLStringExpression);
            s = k.eval(m);
            assertNull(s);
        }


        @Test
        public void testAddParameter() throws URISyntaxException
        {
            Map<Object,Object> m = new HashMap<>();

            StringExpression b = StringExpressionFactory.create("z/details.view?id=${rowId}", true);

            // Add a srcURL parameter expression
            StringExpression c = ((AbstractStringExpression)b).addParameter("srcURL", "${srcURL}");
            assertNotSame("addParameter() should clone the original expression", c, b);

            URLHelper srcURL = new URLHelper("/x/y.view?foo=bar&blee=q");
            m.put("rowId",5);
            m.put("srcURL", srcURL);
            // XXX: URL parameters should be encoded with PageFlowUtil.encode() instead of PageFlowUtil.encodePart()
            //assertEquals("z/details.view?id=5&srcURL=%2Fx%2Fy.view%3Ffoo%3Dbar%26blee%3Dq", c.eval(m));

            // Add a srcURL parameter literal
            String encodedSrcURL = PageFlowUtil.encode(srcURL.getLocalURIString(false));
            StringExpression d = ((AbstractStringExpression)b).addParameter("srcURL", encodedSrcURL);
            m.remove("srcURL");
            assertEquals("z/details.view?id=5&srcURL=%2Fx%2Fy.view%3Ffoo%3Dbar%26blee%3Dq", d.eval(m));
            //assertEquals(b.eval(m), d.eval(m));
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
