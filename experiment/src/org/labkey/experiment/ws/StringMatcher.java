package org.labkey.experiment.ws;

import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * User: jeckels
 * Date: Jul 25, 2006
 */
public abstract class StringMatcher
{
    private static StringMatcher NULL_MATCHER = new NullMatcher();

    public abstract boolean matches(String s);

    public static StringMatcher createMatcher(String pattern)
    {
        if (pattern == null)
        {
            return NULL_MATCHER;
        }
        if (pattern.indexOf('*') == -1)
        {
            return new SimpleMatcher(pattern);
        }
        else
        {
            return new WildcardMatcher(pattern);
        }
    }

    /** Null means the client doesn't care if it matches */
    private static class NullMatcher extends StringMatcher
    {
        public boolean matches(String s)
        {
            return true;
        }
    }

    private static class WildcardMatcher extends StringMatcher
    {
        private Pattern _pattern;

        public WildcardMatcher(String pattern)
        {
            StringTokenizer st = new StringTokenizer(pattern, "*", true);
            StringBuilder sb = new StringBuilder();
            while (st.hasMoreTokens())
            {
                String token = st.nextToken();
                if ("*".equals(token))
                {
                    sb.append(".*");
                }
                else
                {
                    sb.append(Pattern.quote(st.nextToken()));
                }
            }
            _pattern = Pattern.compile(sb.toString());
        }

        public boolean matches(String s)
        {
            return _pattern.matcher(s).matches();
        }
    }

    private static class SimpleMatcher extends StringMatcher
    {
        private final String _pattern;
        private final boolean _caseInsensitive;

        public SimpleMatcher(String pattern, boolean caseInsensitive)
        {
            _pattern = pattern;
            _caseInsensitive = caseInsensitive;
        }

        public SimpleMatcher(String pattern)
        {
            this(pattern, false);
        }

        public boolean matches(String s)
        {
            if (_caseInsensitive)
            {
                return _pattern.equalsIgnoreCase(s);
            }
            else
            {
                return _pattern.equals(s);
            }
        }
    }
}
