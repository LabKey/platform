/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.StringExpressionType;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.data.AbstractTableInfo.LINK_DISABLER;

/**
 * Factory for creating {@link StringExpression} instances from a string that defines the pattern.
 * User: migra
 * Date: Dec 28, 2004
 *
 * TODO: Use a real expression or interpolation library:
 *  - JEXL: http://commons.apache.org/proper/commons-jexl/
 *  - Unified Expression Language: http://juel.sourceforge.net/index.html
 *  - Spring EL: http://docs.spring.io/spring/docs/current/spring-framework-reference/html/expressions.html
 *  - String Template: https://github.com/antlr/stringtemplate4
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
        return create(str, urlEncodeSubstitutions, null);
    }

    public static StringExpression create(String str, boolean urlEncodeSubstitutions, @Nullable AbstractStringExpression.NullValueBehavior nullValueBehavior)
    {
        return create(str, urlEncodeSubstitutions, nullValueBehavior, false);
    }

    public static StringExpression create(String str, boolean urlEncodeSubstitutions, @Nullable AbstractStringExpression.NullValueBehavior nullValueBehavior, boolean allowSideEffects)
    {
        if (StringUtils.isEmpty(str))
            return EMPTY_STRING;

        if (!str.contains("${"))
            return new ConstantStringExpression(str);

        String key = "simple:" + str + "(" + urlEncodeSubstitutions + ")";

        StringExpression expr = templates.get(key);
        if (null != expr)
            return expr;

        expr = new SimpleStringExpression(str, urlEncodeSubstitutions, nullValueBehavior, allowSideEffects);
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
        return createURL(str, null);
    }

    public static StringExpression createURL(String str, @Nullable AbstractStringExpression.NullValueBehavior nullValueBehavior)
    {
        if (str == null)
            return null;

        StringExpression expr;

        String key = "url:" + (nullValueBehavior == null ? "" : nullValueBehavior.name()) + ":" + str;
        expr = templatesUrl.get(key);
        if (null != expr)
            return expr.copy();

        if (str.startsWith("mailto:"))
            expr = new FieldKeyStringExpression(str, true, nullValueBehavior);
        else if (StringUtilsLabKey.startsWithURL(str))
        {
            expr = new URLStringExpression(str, nullValueBehavior);
        }
        else if (null == DetailsURL.validateURL(str))
        {
            expr = DetailsURL.fromString(str, null, nullValueBehavior);
        }
        else
        {
            // improve compatibility with old URLs
            // UNDONE: remove these, or make a new createHelper()
            try
            {
                ActionURL url = new ActionURL(str);
                if (StringUtils.isEmpty(url.getExtraPath()))
                    expr = new DetailsURL(url, null, nullValueBehavior);
            }
            catch (IllegalArgumentException x)
            {
                //
            }
            if (null == expr)
                expr = new URLStringExpression(str);
        }

        templatesUrl.put(key, expr);
        return expr.copy();
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

    public static StringExpression fromXML(@NotNull StringExpressionType xurl, boolean throwErrors)
    {
        String url = xurl.getStringValue();
        if (StringUtils.isBlank(url))
            return LINK_DISABLER;

        try
        {
            AbstractStringExpression.NullValueBehavior nullBehavior = AbstractStringExpression.NullValueBehavior.fromXML(xurl.getReplaceMissing());
            return createURL(url, nullBehavior);
        }
        catch (IllegalArgumentException e)
        {
            if (throwErrors)
                throw e;
            else
                return null;
        }
    }

    //
    // StringExpression implementations
    //

    protected static final String UNDEFINED = "~~undefined~~";

    public static abstract class StringPart implements Cloneable
    {
        /**
         * @return The string value or null if the part is found in the map,
         * otherwise UNDEFINED if the value does not exist in the map.
         */
        @Nullable abstract String getValue(Map map);

        @NotNull
        final String valueOf(Object o)
        {
            return o == null ? "" : String.valueOf(o);
        }

        public abstract boolean isConstant();

        /** Get the token that will be replaced -- either a String of FieldKey.  */
        public abstract Object getToken();

        @NotNull
        public Collection<SubstitutionFormat> getFormats()
        {
            return Collections.emptyList();
        }

        public boolean hasSideEffects()
        {
            return false;
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
        public boolean isConstant() { return true; }

        @Override
        public Object getToken() { throw new UnsupportedOperationException(); }

        @Override
        public String toString()
        {
            return _value;
        }
    }

    private static class SubstitutePart extends StringPart
    {
        protected String _value;

        final protected Collection<SubstitutionFormat> _formats;

        public SubstitutePart(String value, boolean urlEncodeSubstitutions)
        {
            List<SubstitutionFormat> formats = new ArrayList<>(2);

            while (true)
            {
                // Does the string end with a known format function? If so, save it for later and trim it off the string.
                int colon = value.lastIndexOf(':');
                if (colon == -1)
                    break;

                // TODO: Use a real expression parser
                SubstitutionFormat format;
                String rest = value.substring(colon + 1);
                if (rest.startsWith("defaultValue('") && rest.endsWith("')"))
                {
                    String param = rest.substring("defaultValue('".length(), rest.length() - "')".length());
                    format = new SubstitutionFormat.DefaultSubstitutionFormat(param);
                }
                else if (rest.startsWith("number('") && rest.endsWith("')"))
                {
                    // TODO: Without a format string parameter, use container default number format
                    String param = rest.substring("number('".length(), rest.length() - "')".length());
                    format = new SubstitutionFormat.NumberSubstitutionFormat(param);
                }
                else if (rest.startsWith("date('") && rest.endsWith("')"))
                {
                    String param = rest.substring("date('".length(), rest.length() - "')".length());
                    format = createDateSubstitutionFormat(param);
                }
                else if (rest.startsWith("prefix('") && rest.endsWith("')"))
                {
                    String param = rest.substring("prefix('".length(), rest.length() - "')".length());
                    format = new SubstitutionFormat.JoinSubstitutionFormat("", param, "");
                }
                else if (rest.startsWith("suffix('") && rest.endsWith("')"))
                {
                    String param = rest.substring("suffix('".length(), rest.length() - "')".length());
                    format = new SubstitutionFormat.JoinSubstitutionFormat("", "", param);
                }
                else if (rest.startsWith("join('") && rest.endsWith("')"))
                {
                    // TODO: Support three parameter variation
                    String param = rest.substring("join('".length(), rest.length() - "')".length());
                    format = new SubstitutionFormat.JoinSubstitutionFormat(param, "", "");
                }
                else
                {
                    // No-arg substitution formats
                    format = SubstitutionFormat.getFormat(rest);
                }

                if (format == null)
                    break;

                // Add in reverse order since we are parsing from back to front
                formats.add(0, format);

                value = value.substring(0, colon);
            }

            _value = value;
            if (formats.isEmpty())
                _formats = Collections.singleton(urlEncodeSubstitutions ? SubstitutionFormat.urlEncode : SubstitutionFormat.passThrough);
            else
                _formats = Collections.unmodifiableList(formats);
        }

        public SubstitutePart(String value, SubstitutionFormat sf)
        {
            this(value, Collections.singleton(sf));
        }

        public SubstitutePart(String value, Collection<SubstitutionFormat> formats)
        {
            _value = value;
            _formats = formats;
        }

        @Override
        public boolean isConstant() { return false; }

        @Override
        public Object getToken()
        {
            return _value;
        }

        @NotNull
        @Override
        public Collection<SubstitutionFormat> getFormats()
        {
            return _formats;
        }

        public String getValue(Map map)
        {
            String s = applyFormats(map.get(_value));
            if (s == null && !map.containsKey(_value))
                return UNDEFINED;

            return s;
        }

        protected final String applyFormats(Object o)
        {
            // Allow substitution format to transform the value, including nulls
            for (SubstitutionFormat f : _formats)
            {
                o = f.format(o);
            }

            if (o == null)
            {
                // If we don't have the value at all, return null instead of the empty string to communicate that we
                // don't have the info required to evaluate this expression.
                return null;
            }

            return valueOf(o);
        }

        public boolean hasSideEffects()
        {
            for (SubstitutionFormat f : _formats)
            {
                if (f.hasSideEffects())
                    return true;
            }

            return false;
        }

        @Override
        public String toString()
        {
            return "${" + _value + "}";
        }
    }

    private static SubstitutionFormat createDateSubstitutionFormat(String format)
    {
        return new SubstitutionFormat.DateSubstitutionFormat(createDateFormatter(format));
    }

    private static DateTimeFormatter createDateFormatter(String format)
    {
        switch (format)
        {
            case "BASIC_ISO_DATE":       return DateTimeFormatter.BASIC_ISO_DATE;
            case "ISO_DATE":             return DateTimeFormatter.ISO_DATE;
            case "ISO_DATE_TIME":        return DateTimeFormatter.ISO_DATE_TIME;
            case "ISO_INSTANT":          return DateTimeFormatter.ISO_INSTANT;
            case "ISO_LOCAL_DATE":       return DateTimeFormatter.ISO_LOCAL_DATE;
            case "ISO_LOCAL_DATE_TIME":  return DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            case "ISO_LOCAL_TIME":       return DateTimeFormatter.ISO_LOCAL_TIME;
            case "ISO_OFFSET_DATE":      return DateTimeFormatter.ISO_OFFSET_DATE;
            case "ISO_OFFSET_DATE_TIME": return DateTimeFormatter.ISO_OFFSET_DATE_TIME;
            case "ISO_OFFSET_TIME":      return DateTimeFormatter.ISO_OFFSET_TIME;
            case "ISO_ORDINAL_DATE":     return DateTimeFormatter.ISO_ORDINAL_DATE;
            case "ISO_TIME":             return DateTimeFormatter.ISO_TIME;
            case "ISO_WEEK_DATE":        return DateTimeFormatter.ISO_WEEK_DATE;
            case "ISO_ZONED_DATE_TIME":  return DateTimeFormatter.ISO_ZONED_DATE_TIME;
            case "RFC_1123_DATE_TIME":   return DateTimeFormatter.RFC_1123_DATE_TIME;
            default:                     return DateTimeFormatter.ofPattern(format);
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
            schemaPath
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
                            UserSchema userSchema = table.getUserSchema();
                            if (null != userSchema)
                                return userSchema.getSchemaPath().toString();
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
            return applyFormats(s);
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
            // Any null field results in a null eval (good for URLs)
            NullResult(StringExpressionType.ReplaceMissing.NULL_RESULT),

            // Null or missing fields get replaced with blank
            ReplaceNullWithBlank(StringExpressionType.ReplaceMissing.BLANK_VALUE),

            // Insert "null" into the string
            OutputNull(StringExpressionType.ReplaceMissing.NULL_VALUE);

            private final StringExpressionType.ReplaceMissing.Enum _xenum;

            NullValueBehavior(StringExpressionType.ReplaceMissing.Enum xenum)
            {
                _xenum = xenum;
            }

            @Nullable
            public static AbstractStringExpression.NullValueBehavior fromXML(StringExpressionType.ReplaceMissing.Enum xmissing)
            {
                if (xmissing == null)
                    return null;

                for (NullValueBehavior nullBehavior : values())
                {
                    if (xmissing == nullBehavior._xenum)
                        return nullBehavior;
                }

                return null;
            }

        }

        protected final NullValueBehavior _nullValueBehavior;
        protected final boolean _allowSideEffects;

        protected String _source;
        protected ArrayList<StringPart> _parsedExpression = null;

        AbstractStringExpression(String source)
        {
            this(source, NullValueBehavior.NullResult);
            //MemTracker.getInstance().put(this);
        }

        AbstractStringExpression(String source, NullValueBehavior nullValueBehavior)
        {
            this(source, nullValueBehavior, false);
        }

        AbstractStringExpression(String source, NullValueBehavior nullValueBehavior, boolean allowSideEffects)
        {
            _source = source;
            if (nullValueBehavior != null)
                _nullValueBehavior = nullValueBehavior;
            else
                _nullValueBehavior = NullValueBehavior.NullResult;
            _allowSideEffects = allowSideEffects;
            //MemTracker.getInstance().put(this);
        }


        public synchronized ArrayList<StringPart> getParsedExpression()
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
                int closeIndex = _source.indexOf('}', index + 2);
                if (closeIndex == -1)
                    break;
                if (index > 0)
                    _parsedExpression.add(new ConstantPart(_source.substring(start, index)));
                String sub = _source.substring(index+2,closeIndex);

                StringPart part = parsePart(sub);
                if (part.hasSideEffects() && !_allowSideEffects)
                    throw new IllegalArgumentException("Side-effecting expression part not allowed: " + sub);

                _parsedExpression.add(part);
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
            {
                try
                {
                    return nullFilter(parts.get(0).getValue(context));
                }
                catch (StopIteratingException e)
                {
                    return null;
                }
            }

            try
            {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < parts.size(); i++)
                {
                    StringPart part = parts.get(i);
                    String value = nullFilter(part.getValue(context));
                    builder.append(value);
                }
                return builder.toString();
            }
            catch (StopIteratingException e)
            {
                return null;
            }
        }

        protected final String nullFilter(String value) throws StopIteratingException
        {
            // Allow non-null and non-UNDEFINED values
            if (value != null && !UNDEFINED.equals(value))
                return value;

            // For now, always bail out if the context is missing one of the substitutions.
            // Better to have no URL than a URL that's missing parameters.  In the future, we
            // could emit the missing FieldKey into the URL.
            if (UNDEFINED.equals(value))
                throw new StopIteratingException();

            switch (_nullValueBehavior)
            {
                // Bail out if the context is missing one of the substitutions. Better to have no URL than
                // a URL that's missing parameters
                case NullResult:
                    throw new StopIteratingException();

                // More lenient... just substitute blank for missing/null
                case ReplaceNullWithBlank:
                    return "";

                // Output "null"
                case OutputNull:
                    return "null";

                default:
                    throw new IllegalStateException("Unknown behavior: " + _nullValueBehavior);
            }
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

        public StringExpressionType toXML()
        {
            StringExpressionType xurl = StringExpressionType.Factory.newInstance();
            xurl.setStringValue(toString());

            // If the null handling isn't the default, add the missingBehavior attribute
            if (_nullValueBehavior != NullValueBehavior.NullResult)
                xurl.setReplaceMissing(_nullValueBehavior._xenum);

            return xurl;
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
            this(source, urlEncodeSubstitutions, null, false);
        }

        SimpleStringExpression(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects)
        {
            super(source, nullValueBehavior, allowSideEffects);
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

        @Override
        public FieldKey getToken()
        {
            return _key;
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

            String result = applyFormats(map.get(lookupKey));
            if (result == null && !map.containsKey(lookupKey))
                return UNDEFINED;

            return result;
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
            this(source, true, null);
        }

        public FieldKeyStringExpression(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior)
        {
            this(source, urlEncodeSubstitutions, nullValueBehavior, false);
        }

        // NOTE: URL expressions are slightly different from vanilla string expressions
        // in that they default to ReplaceNullWithBlank instead of NullResult.
        public FieldKeyStringExpression(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects)
        {
            super(source, nullValueBehavior != null ? nullValueBehavior : NullValueBehavior.ReplaceNullWithBlank, allowSideEffects);
            _urlEncodeSubstitutions = urlEncodeSubstitutions;
        }

        public static FieldKeyStringExpression create(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior)
        {
            return new FieldKeyStringExpression(source, urlEncodeSubstitutions, nullValueBehavior);
        }

        public static FieldKeyStringExpression create(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects)
        {
            return new FieldKeyStringExpression(source, urlEncodeSubstitutions, nullValueBehavior, allowSideEffects);
        }

        protected StringPart parsePart(String expr)
        {
            // HACK
            if (RenderContextPart.SUPPORTED_SUBSTITUTIONS.contains(expr))
                return new RenderContextPart(expr);
            try
            {
                return new FieldPart(expr, _urlEncodeSubstitutions);
            }
            catch (IllegalArgumentException x)
            {
                return new ConstantPart("${" + expr + "}");
            }
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
        public URLStringExpression(String source)
        {
            this(source, null);
        }

        public URLStringExpression(String source, @Nullable NullValueBehavior nullBehavior)
        {
            super("", true, nullBehavior);
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


        @Test
        public void testEncoding()
        {
            {
                StringExpression se = FieldKeyStringExpression.create("${html:htmlEncode}|${pass:passThrough}|${url:urlEncode}", false, AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);
                Map<Object, Object> m = new HashMap<>();
                m.put(new FieldKey(null, "html"), "<script></script>");
                m.put(new FieldKey(null, "pass"), "<pass>10%</pass>");
                m.put(new FieldKey(null, "url"), "<url>10%</url>");
                String s = se.eval(m);
                assertEquals("&lt;script&gt;&lt;/script&gt;|<pass>10%</pass>|%3Curl%3E10%25%3C/url%3E", s);
            }

            {
                StringExpression se = StringExpressionFactory.create("${html:htmlEncode}|${pass:passThrough}|${url:urlEncode}", false);
                Map<Object, Object> m = new HashMap<>();
                m.put("html", "<script></script>");
                m.put("pass", "<pass>10%</pass>");
                m.put("url", "<url>10%</url>");
                String s = se.eval(m);
                assertEquals("&lt;script&gt;&lt;/script&gt;|<pass>10%</pass>|%3Curl%3E10%25%3C/url%3E", s);
            }

        }

        @Test
        public void testDateFormats()
        {
            Date d = new GregorianCalendar(2011, 11, 3).getTime();
            Map<Object, Object> m = new HashMap<>();
            m.put("d", d);

            {
                StringExpression se = StringExpressionFactory.create("${d:date}", false);
                String s = se.eval(m);
                assertEquals("20111203", s);
            }

            {
                StringExpression se = StringExpressionFactory.create("${d:date('yy-MM-dd')}", false);
                String s = se.eval(m);
                assertEquals("11-12-03", s);
            }

            {
                StringExpression se = StringExpressionFactory.create("${d:date('& yy-MM-dd'):htmlEncode}", false);
                String s = se.eval(m);
                assertEquals("&amp; 11-12-03", s);
            }

            {
                StringExpression se = StringExpressionFactory.create("${d:date('ISO_ORDINAL_DATE')}", false);
                String s = se.eval(m);
                assertEquals("2011-337", s);
            }
        }

        @Test
        public void testNumberFormats()
        {
            Double d = 123456.789;
            Map<Object, Object> m = new HashMap<>();
            m.put("d", d);

            {
                StringExpression se = StringExpressionFactory.create("${d:number('0.0000')}", false);
                String s = se.eval(m);
                assertEquals("123456.7890", s);
            }

            {
                StringExpression se = StringExpressionFactory.create("${d:number('000000000')}", false);
                String s = se.eval(m);
                assertEquals("000123457", s);
            }
        }

        @Test
        public void testStringFormats()
        {
            Map<Object, Object> m = new HashMap<>();
            m.put("a", "A");
            m.put("b", " B ");
            m.put("empty", "");
            m.put("null", null);
            m.put("list", Arrays.asList("a", "b", "c"));

            {
                StringExpression se = StringExpressionFactory.create(
                        "${null:defaultValue('foo')}|${empty:defaultValue('bar')}|${a:defaultValue('blee')}", false, AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);

                String s = se.eval(m);
                assertEquals("foo|bar|A", s);
            }

            {
                StringExpression se = StringExpressionFactory.create(
                        "${b}|${b:trim}|${empty:trim}|${null:trim}", false, AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);
                String s = se.eval(m);
                assertEquals(" B |B||", s);
            }

            {
                StringExpression se = StringExpressionFactory.create(
                        "${a:prefix('!')}|${a:suffix('?')}|${null:suffix('#')}|${empty:suffix('*')}|${empty:defaultValue('foo'):suffix('@')}", false, AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);
                String s = se.eval(m);
                assertEquals("!A|A?|||foo@", s);
            }

            {
                StringExpression se = StringExpressionFactory.create(
                        "${a:join('-')}|${list:join('-')}|${list:join('_'):prefix('['):suffix(']')}|${empty:join('-')}|${null:join('-')}", false, AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);
                String s = se.eval(m);
                assertEquals("A|a-b-c|[a_b_c]||", s);
            }
        }

        @Test
        public void testCollectionFormats()
        {
            Map<Object, Object> m = new HashMap<>();
            m.put("a", "A");
            m.put("empty", Collections.emptyList());
            m.put("null", null);
            m.put("list", Arrays.asList("a", "b", "c"));

            // CONSIDER: We may want to allow empty string to pass through the collection methods untouched
            try
            {
                StringExpression se = StringExpressionFactory.create(
                        "${a:first}", false, AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);

                String s = se.eval(m);
                fail("Expected exception");
            }
            catch (IllegalArgumentException e)
            {
                // ok
            }

            {
                StringExpression se = StringExpressionFactory.create(
                        "${null:first}|${empty:first}|${list:first}", false, AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);

                String s = se.eval(m);
                assertEquals("||a", s);
            }

            {
                StringExpression se = StringExpressionFactory.create(
                        "${null:rest}|${empty:rest:join('-')}|${list:rest:join('-')}", false, AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);

                String s = se.eval(m);
                assertEquals("||b-c", s);
            }

            {
                StringExpression se = StringExpressionFactory.create(
                        "${null:last}|${empty:last}|${list:last}", false, AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);

                String s = se.eval(m);
                assertEquals("||c", s);
            }
        }

        @Test
        public void testUndefined()
        {
            Map<Object, Object> m = new HashMap<>();
            m.put("a", "A");
            m.put("null", null);

            {
                StringExpression se = new FieldKeyStringExpression("${a}");
                String s = se.eval(m);
                assertEquals("A", s);
            }

            {
                // SimpleStringExpression default behavior is to emit blank for null values
                StringExpression se1 = new SimpleStringExpression("${null}", false);
                String s1 = se1.eval(m);
                assertEquals(null, s1);

                // For backwards compatibility, FieldKeyStringExpression default behavior is to emit empty string for null values
                FieldKeyStringExpression se2 = new FieldKeyStringExpression("${null}");
                String s2 = se2.eval(m);
                assertEquals("", s2);

                // ... but can be overridden to emit a null result
                FieldKeyStringExpression se3 = new FieldKeyStringExpression("${null}", true, AbstractStringExpression.NullValueBehavior.NullResult);
                String s3 = se3.eval(m);
                assertEquals(null, s3);
            }

            {
                // Undefined values always result in a null result
                StringExpression se1 = new FieldKeyStringExpression("${doesNotExist}");
                String s1 = se1.eval(m);
                assertEquals(null, s1);

                // ... unless it has a defaultValue
                StringExpression se2 = new FieldKeyStringExpression("${doesNotExist:defaultValue('fred')}");
                String s2 = se2.eval(m);
                assertEquals("fred", s2);
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
