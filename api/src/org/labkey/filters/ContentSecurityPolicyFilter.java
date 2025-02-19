package org.labkey.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.security.Directive;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.CspCommentScanner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringExpressionFactory.AbstractStringExpression.NullValueBehavior;
import org.labkey.api.util.logging.LogHelper;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Content Security Policies (CSPs) are loaded from the csp.enforce and csp.report properties in application.properties.
 */
public class ContentSecurityPolicyFilter implements Filter
{
    public static final String FEATURE_FLAG_DISABLE_ENFORCE_CSP = "disableEnforceCsp";

    private static final String NONCE_SUBST = "REQUEST.SCRIPT.NONCE";
    private static final String REPORT_PARAMETER_SUBSTITUTION = "CSP.REPORT.PARAMS";
    private static final String HEADER_NONCE = "org.labkey.filters.ContentSecurityPolicyFilter#NONCE";  // needs to match PageConfig.HEADER_NONCE
    private static final Object ALLOWED_SOURCES_LOCK = new Object();
    private static final Map<Directive, SetValuedMap<String, String>> ALLOWED_SOURCES = new HashMap<>();

    private static Map<String, String> _substitutionMap = Collections.emptyMap();

    // Per-filter-instance parameters that are set in init() and never changed
    private StringExpression policyExpression = null;
    private ContentSecurityPolicyType type = ContentSecurityPolicyType.Enforce;

    public enum ContentSecurityPolicyType
    {
        Report("Content-Security-Policy-Report-Only"), Enforce("Content-Security-Policy");

        private final String _headerName;

        ContentSecurityPolicyType(String headerName)
        {
            _headerName = headerName;
        }

        public String getHeaderName()
        {
            return _headerName;
        }
    }

    static
    {
        // ReactJS hot reload uses localhost port 3001. If in dev mode, allow browser to access that port for fonts
        // and connections.
        if (AppProps.getInstance().isDevMode())
        {
            registerAllowedSources(Directive.Connection, "reactjs.hot.reload", "localhost:3001");
            registerAllowedSources(Directive.Font, "reactjs.hot.reload", "localhost:3001");
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {
        LogHelper.getLogger(ContentSecurityPolicyFilter.class, "CSP filter initialization").info("Initializing {}", filterConfig.getFilterName());

        Enumeration<String> paramNames = filterConfig.getInitParameterNames();
        while (paramNames.hasMoreElements())
        {
            String paramName = paramNames.nextElement();
            String paramValue = filterConfig.getInitParameter(paramName);
            if ("policy".equalsIgnoreCase(paramName))
            {
                String s = filterPolicy(paramValue);

                // Replace REPORT_PARAMETER_SUBSTITUTION now since its value is static
                s = StringExpressionFactory.create(s, false, NullValueBehavior.KeepSubstitution)
                    .eval(Map.of(REPORT_PARAMETER_SUBSTITUTION, "labkeyVersion=" + PageFlowUtil.encodeURIComponent(AppProps.getInstance().getReleaseVersion())));

                policyExpression = StringExpressionFactory.create(s, false, NullValueBehavior.ReplaceNullAndMissingWithBlank);
            }
            else if ("disposition".equalsIgnoreCase(paramName))
            {
                String s = paramValue.trim();
                if (!"report".equalsIgnoreCase(s) && !"enforce".equalsIgnoreCase(s))
                    throw new ServletException("ContentSecurityPolicyFilter is misconfigured, unexpected disposition value: " + s);
                if ("report".equalsIgnoreCase(s))
                    type = ContentSecurityPolicyType.Report;
            }
            else
            {
                throw new ServletException("ContentSecurityPolicyFilter is misconfigured, unexpected parameter name: " + paramName);
            }
        }
    }

    /** Filter out block comments and replace special characters in the provided policy */
    public static String filterPolicy(String policy)
    {
        String s = policy.trim();
        s = s.replace( '\n', ' ' );
        s = s.replace( '\r', ' ' );
        s = s.replace( '\t', ' ' );
        s = s.replace((char)0x2018, (char)0x027);     // LEFT SINGLE QUOTATION MARK -> APOSTROPHE
        s = s.replace((char)0x2019, (char)0x027);     // RIGHT SINGLE QUOTATION MARK -> APOSTROPHE

        // We use pseudo-Java-style block comments to document our policies; strip them since CSP syntax doesn't allow
        // them. All replacements are performed before stripping comments to ensure whitespace is replaced with spaces.
        CspCommentScanner scanner = new CspCommentScanner(s);
        s = scanner.stripComments().toString();

        return s;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        if (request instanceof HttpServletRequest req && response instanceof HttpServletResponse resp && null != policyExpression)
        {
            if (type != ContentSecurityPolicyType.Enforce || !OptionalFeatureService.get().isFeatureEnabled(FEATURE_FLAG_DISABLE_ENFORCE_CSP))
            {
                Map<String, String> map = new HashMap<>(_substitutionMap);
                map.put(NONCE_SUBST, getScriptNonceHeader(req));
                var csp = policyExpression.eval(map);
                resp.setHeader(type.getHeaderName(), csp);
            }
        }
        chain.doFilter(request, response);
    }

    public static String getScriptNonceHeader(HttpServletRequest request)
    {
        String nonce = (String)request.getAttribute(HEADER_NONCE);
        if (nonce != null)
            return nonce;

        nonce = Long.toHexString(rand.nextLong());
        rand.setSeed(request.getRequestURI().hashCode());

        request.setAttribute(HEADER_NONCE, nonce);
        return nonce;
    }

    private static final SecureRandom rand = new SecureRandom();

    @Deprecated // Use registerAllowedSources(Directive.Connection...)
    public static void registerAllowedConnectionSource(String key, String... allowedUrls)
    {
        registerAllowedSources(Directive.Connection, key, allowedUrls);
    }

    public static void registerAllowedSources(Directive directive, String key, String... allowedSources)
    {
        synchronized (ALLOWED_SOURCES_LOCK)
        {
            SetValuedMap<String, String> multiMap = ALLOWED_SOURCES.computeIfAbsent(directive, d -> new HashSetValuedHashMap<>());
            Arrays.stream(allowedSources).forEach(s -> multiMap.put(key, s));
            regenerateSubstitutionMap();
        }
    }

    public static void unregisterAllowedSources(Directive directive, String key)
    {
        synchronized (ALLOWED_SOURCES_LOCK)
        {
            SetValuedMap<String, String> multiMap = ALLOWED_SOURCES.get(directive);
            if (multiMap != null)
            {
                Set<String> previous = multiMap.remove(key);
                // Empty set means no previous mappings were removed, so no need to regenerate the substitution map
                if (!previous.isEmpty())
                    regenerateSubstitutionMap();
            }
        }
    }

    // Pre-generate the substitution map on every register/unregister
    private static void regenerateSubstitutionMap()
    {
        _substitutionMap = ALLOWED_SOURCES.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .collect(Collectors.toMap(
                e -> e.getKey().getSubstitutionKey(),
                e -> e.getValue().values().stream()
                    .distinct()
                    .collect(Collectors.joining(" ")))
            );

        // Backward compatibility for CSPs using old substitution key
        // TODO: Remove in 25.4 and adjust the junit test below
        if (_substitutionMap.containsKey(Directive.Connection.getSubstitutionKey()))
            _substitutionMap.put("LABKEY.ALLOWED.CONNECTIONS", _substitutionMap.get(Directive.Connection.getSubstitutionKey()));
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testPolicyFiltering()
        {
            String fakePolicyForTesting = """
                /* Beginning of line comment should be removed */default-src\t'self' https: http: ;
                    connect-src 'self' http://www.labkey.org /* this is a mistake! */ localhost:* ws: ${LABKEY.ALLOWED.CONNECTIONS} ;
                    object-src https://* ‘none’ ; /* Hard to see, but there are curly quotes surrounding "none" on this line */\r
                    style-src 'self'\rhttps: 'unsafe-inline' ;
                    img-src 'self'\thttps: data: ;
                    font-src 'self' http://www.labkey.com https://* http: /* I don't know why we're doing this! */ https: data: ;
                    script-src 'unsafe-eval' 'strict-dynamic' 'nonce-${REQUEST.SCRIPT.NONCE}' ;
                    base-uri 'self' ; /* what in the world?! */
                    frame-ancestors 'self' ;  /* This here comment spans
                        multiple lines
                        for testing purposes
                        */
                    report-uri /* Whoa! */ /admin-contentsecuritypolicyreport.api?${CSP.REPORT.PARAMS} https://*;
                """;

            // Multi-line for readability, but notice that newlines are replaced before assignment
            String expected = """
                default-src 'self' https: http: ;
                    connect-src 'self' http://www.labkey.org  localhost:* ws: ${LABKEY.ALLOWED.CONNECTIONS} ;
                    object-src https://* 'none' ;
                      style-src 'self' https: 'unsafe-inline' ;
                    img-src 'self' https: data: ;
                    font-src 'self' http://www.labkey.com https://* http:  https: data: ;
                    script-src 'unsafe-eval' 'strict-dynamic' 'nonce-${REQUEST.SCRIPT.NONCE}' ;
                    base-uri 'self' ;
                     frame-ancestors 'self' ;
                      report-uri  /admin-contentsecuritypolicyreport.api?${CSP.REPORT.PARAMS} https://*;""".replace('\n', ' ');

            Assert.assertEquals(expected, filterPolicy(fakePolicyForTesting));
        }

        @Test
        public void testSubstitutionMap()
        {
            // Make a deep copy of ALLOWED_SOURCES so we can restore it after testing
            final Map<Directive, SetValuedMap<String, String>> savedSources;
            final int sourceMapSize;
            final int substitutionMapSize;
            synchronized (ALLOWED_SOURCES_LOCK)
            {
                sourceMapSize = ALLOWED_SOURCES.size();
                substitutionMapSize = _substitutionMap.size();
                savedSources = ALLOWED_SOURCES.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> new HashSetValuedHashMap<>(e.getValue())));
                ALLOWED_SOURCES.clear();
                regenerateSubstitutionMap();
            }

            assertTrue(ALLOWED_SOURCES.isEmpty());
            assertTrue(_substitutionMap.isEmpty());
            unregisterAllowedSources(Directive.Connection, "foo");
            assertTrue(ALLOWED_SOURCES.isEmpty());
            assertTrue(_substitutionMap.isEmpty());
            registerAllowedSources(Directive.Connection, "foo", "MySource");
            assertEquals(1, ALLOWED_SOURCES.size());
            assertEquals(2, _substitutionMap.size()); // Old connection substitution key should be added as well
            registerAllowedSources(Directive.Connection, "bar", "MySource");
            assertEquals(1, ALLOWED_SOURCES.size());
            assertEquals(2, _substitutionMap.size()); // Duplicate source should be filtered out

            registerAllowedSources(Directive.Font, "font", "MySource");
            assertEquals(2, ALLOWED_SOURCES.size());
            assertEquals(3, _substitutionMap.size());
            registerAllowedSources(Directive.Font, "font2", "MyOtherSource");
            assertEquals(2, ALLOWED_SOURCES.size());
            assertEquals(3, _substitutionMap.size());
            String value = _substitutionMap.get("FONT.SOURCES");
            assertEquals("! !", value.replace("MyOtherSource", "!").replace("MySource", "!"));
            unregisterAllowedSources(Directive.Font, "font2");
            assertEquals(2, ALLOWED_SOURCES.size());
            assertEquals(3, _substitutionMap.size());
            unregisterAllowedSources(Directive.Font, "font");
            assertEquals(2, ALLOWED_SOURCES.size()); // Font entry still exists, but should be empty
            assertTrue(ALLOWED_SOURCES.get(Directive.Font).isEmpty());
            assertEquals(2, _substitutionMap.size()); // Back to the way it was

            registerAllowedSources(Directive.Frame, "frame", "FrameSource", "FrameStore");
            assertEquals(3, ALLOWED_SOURCES.size());
            assertEquals(3, _substitutionMap.size());

            registerAllowedSources(Directive.Style, "style", "StyleSource", "MoreStylishStore");
            assertEquals(4, ALLOWED_SOURCES.size());
            assertEquals(4, _substitutionMap.size());

            // Restore the previous ALLOWED_SOURCES
            synchronized (ALLOWED_SOURCES_LOCK)
            {
                ALLOWED_SOURCES.clear();
                ALLOWED_SOURCES.putAll(savedSources);
                regenerateSubstitutionMap();
                assertEquals(sourceMapSize, ALLOWED_SOURCES.size());
                assertEquals(substitutionMapSize, _substitutionMap.size());
            }
        }
    }
}
