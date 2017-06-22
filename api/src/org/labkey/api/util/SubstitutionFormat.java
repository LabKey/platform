/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.api.ExperimentService;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *  These are the supported formatting functions that can be used with string substitution, for example, when substituting
 *  values into a details URL or the javaScriptEvents property of a JavaScriptDisplayColumnFactory. The function definitions
 *  are patterned off Ext.util.Format (formats used in ExtJs templates), http://docs.sencha.com/extjs/4.2.1/#!/api/Ext.util.Format
 *
 *  Examples:
 *  <pre>
 *  ${Name:htmlEncode}
 *  ${MyParam:urlEncode}
 *
 *  ${MyParam:defaultValue('foo')}
 *
 *  ${MyDate:date}
 *  ${MyDate:date('yy-MM d')}
 *
 *  ${MyNumber:number('')}
 *
 *  ${MyParam:trim}
 *
 *  ${MyParam:prefix('-')}
 *  ${MyParam:suffix('-')}
 *  ${MyParam:join('-')}
 *
 *  ${MyParam:first}
 *  ${MyParam:rest}
 *  ${MyParam:last}
 *
 * </pre>
 * We should add more functions and allow parametrized functions. As we add functions, we should use the Ext names and
 * parameters if at all possible.
 *
 * User: adam
 * Date: 6/20/13
 */
public class SubstitutionFormat
{
    static final SubstitutionFormat passThrough = new SubstitutionFormat("passThrough", "none") {
        @Override
        public Object format(Object value)
        {
            return value;
        }
    };

    static final SubstitutionFormat htmlEncode = new SubstitutionFormat("htmlEncode", "html")
    {
        @Override
        public String format(Object value)
        {
            if (value == null)
                return null;
            return PageFlowUtil.filter(value);
        }
    };

    static final SubstitutionFormat jsString = new SubstitutionFormat("jsString", "jsString")
    {
        @Override
        public String format(Object value)
        {
            if (value == null)
                return null;
            return PageFlowUtil.jsString(String.valueOf(value));
        }
    };

    static final SubstitutionFormat urlEncode = new SubstitutionFormat("urlEncode", "path")
    {
        @Override
        public String format(Object value)
        {
            if (value == null)
                return null;
            return PageFlowUtil.encodePath(String.valueOf(value));
        }
    };

    // like javascript encodeURIComponent
    static final SubstitutionFormat encodeURIComponent = new SubstitutionFormat("encodeURIComponent", "uricomponent")
    {
        @Override
        public String format(Object value)
        {
            if (value == null)
                return null;
            return PageFlowUtil.encodeURIComponent(String.valueOf(value));
        }
    };

    // like javascript encodeURI
    static final SubstitutionFormat encodeURI = new SubstitutionFormat("encodeURI", "uri")
    {
        @Override
        public String format(Object value)
        {
            if (value == null)
                return null;
            return PageFlowUtil.encodeURI(String.valueOf(value));
        }
    };

    static final SubstitutionFormat first = new SubstitutionFormat("first")
    {
        @Override
        public Object format(Object value)
        {
            if (value == null)
                return null;

            if (!(value instanceof Collection))
                throw new IllegalArgumentException("Expected collection: " + value);

            Collection<?> c = (Collection)value;
            return c.stream().findFirst().orElse(null);
        }
    };

    static final SubstitutionFormat rest = new SubstitutionFormat("rest")
    {
        @Override
        public Object format(Object value)
        {
            if (value == null)
                return null;

            if (!(value instanceof Collection))
                throw new IllegalArgumentException("Expected collection: " + value);

            Collection<?> c = (Collection)value;
            return c.stream().skip(1).collect(Collectors.toList());
        }
    };

    static final SubstitutionFormat last = new SubstitutionFormat("last")
    {
        @Override
        public Object format(Object value)
        {
            if (value == null)
                return null;

            if (!(value instanceof Collection))
                throw new IllegalArgumentException("Expected collection: " + value);

            Collection<?> c = (Collection)value;
            return c.stream().reduce((a, b) -> b).orElse(null);
        }
    };

    static final SubstitutionFormat trim = new SubstitutionFormat("trim")
    {
        @Override
        public String format(Object value)
        {
            if (value == null)
                return null;

            if (!(value instanceof String))
                throw new IllegalArgumentException("Expected string: " + value);

            return ((String)value).trim();
        }
    };

    public static class DefaultSubstitutionFormat extends SubstitutionFormat
    {
        private final String _default;

        public DefaultSubstitutionFormat(@NotNull String def)
        {
            super("defaultValue");
            _default = def;
        }

        @Override
        public Object format(Object value)
        {
            if (value == null || "".equals(value))
                return _default;

            return value;
        }
    }

    public static class JoinSubstitutionFormat extends SubstitutionFormat
    {
        private final String _sep;
        private final String _prefix;
        private final String _suffix;

        public JoinSubstitutionFormat(@NotNull String sep)
        {
            super("join");
            _sep = sep;
            _prefix = "";
            _suffix = "";
        }

        public JoinSubstitutionFormat(@NotNull String sep, @NotNull String prefix, @NotNull String suffix)
        {
            super("join");
            _sep = sep;
            _prefix = prefix;
            _suffix = suffix;
        }

        @Override
        public String format(Object value)
        {
            if (value == null)
                return null;

            Stream<String> ss;
            if (value instanceof Collection)
            {
                Collection<?> c = (Collection)value;
                if (c.isEmpty())
                    return "";

                ss = c.stream().map(String::valueOf);
            }
            else
            {
                String s = String.valueOf(value);
                if (s.length() == 0)
                    return "";

                ss = Stream.of(s);
            }

            return ss.collect(Collectors.joining(_sep, _prefix, _suffix));
        }
    }

    public static class DateSubstitutionFormat extends SubstitutionFormat
    {
        // NOTE: We use DateTimeFormatter since it is thread-safe unlike SimpleDateFormat
        final DateTimeFormatter _format;

        public DateSubstitutionFormat(@NotNull DateTimeFormatter format)
        {
            super("date");
            _format = format;
        }

        @Override
        public String format(Object value)
        {
            if (value == null)
                return null;

            TemporalAccessor temporal;
            if (value instanceof TemporalAccessor)
                temporal = (TemporalAccessor) value;
            else if (value instanceof Date)
                temporal = LocalDateTime.ofInstant(((Date)value).toInstant(), ZoneId.systemDefault());
            else
                throw new IllegalArgumentException("Expected date: " + value);

            return _format.format(temporal);
        }
    }

    static final SubstitutionFormat date = new DateSubstitutionFormat(DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneId.systemDefault()));

    public static class NumberSubstitutionFormat extends SubstitutionFormat
    {
        final DecimalFormat _format;

        NumberSubstitutionFormat(String formatString)
        {
            super("number");
            _format = new DecimalFormat(formatString);
        }

        @Override
        public Object format(Object value)
        {
            if (value == null)
                return null;

            if (!(value instanceof Number))
                throw new IllegalArgumentException("Expected number: " + value);

            return _format.format(value);
        }
    }

    public static class SampleCountSubstitutionFormat extends SubstitutionFormat
    {
        SampleCountSubstitutionFormat(String name)
        {
            super(name);
        }

        @Override
        public boolean hasSideEffects()
        {
            return true;
        }

        @Override
        public Object format(Object value)
        {
            Date date = null;
            if (value instanceof Date)
                date = (Date)value;

            // Increment sample counters for the given date or today's date if null
            // TODO: How can we check if we have incremented sample counters for this same date within the current context/row?
            Map<String, Integer> counts = ExperimentService.get().incrementSampleCounts(date);
            return counts.get(_name);
        }
    }

    public static SampleCountSubstitutionFormat dailySampleCount = new SampleCountSubstitutionFormat("dailySampleCount");
    public static SampleCountSubstitutionFormat weeklySampleCount = new SampleCountSubstitutionFormat("weeklySampleCount");
    public static SampleCountSubstitutionFormat monthlySampleCount = new SampleCountSubstitutionFormat("monthlySampleCount");
    public static SampleCountSubstitutionFormat yearlySampleCount = new SampleCountSubstitutionFormat("yearlySampleCount");


    final String _name;
    final String _shortName;

    SubstitutionFormat(String name)
    {
        _name = name;
        _shortName = null;
    }

    SubstitutionFormat(String name, String shortName)
    {
        _name = name;
        _shortName = shortName;
    }

    public String name()
    {
        return _name;
    }

    public Object format(Object value)
    {
        return value;
    }

    public boolean hasSideEffects()
    {
        return false;
    }

    private final static Map<String, SubstitutionFormat> _map = new CaseInsensitiveHashMap<>();

    private static void register(SubstitutionFormat fmt)
    {
        _map.put(fmt.name(), fmt);
        if (fmt._shortName != null)
            _map.put(fmt._shortName, fmt);
    }

    static
    {
        register(SubstitutionFormat.date);
        register(SubstitutionFormat.encodeURI);
        register(SubstitutionFormat.encodeURIComponent);
        register(SubstitutionFormat.first);
        register(SubstitutionFormat.htmlEncode);
        register(SubstitutionFormat.jsString);
        register(SubstitutionFormat.last);
        register(SubstitutionFormat.passThrough);
        register(SubstitutionFormat.rest);
        register(SubstitutionFormat.trim);
        register(SubstitutionFormat.urlEncode);

        // sample counters
        register(SubstitutionFormat.dailySampleCount);
        register(SubstitutionFormat.weeklySampleCount);
        register(SubstitutionFormat.monthlySampleCount);
        register(SubstitutionFormat.yearlySampleCount);
    }

    // More lenient than SubstitutionFormat.valueOf(), returns null for non-match
    public static @Nullable SubstitutionFormat getFormat(String formatName)
    {
        return _map.get(formatName);
    }
}
