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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.collections.CopyOnWriteHashMap;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.security.Directive;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.CspCommentScanner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringExpressionFactory.AbstractStringExpression.NullValueBehavior;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Content Security Policies (CSPs) are loaded from the csp.enforce and csp.report properties in application.properties.
 */
public class ContentSecurityPolicyFilter implements Filter
{
    private static final Logger LOG = LogHelper.getLogger(ContentSecurityPolicyFilter.class, "Register/unregister allowed resource hosts");

    private static final String NONCE_SUBST = "REQUEST.SCRIPT.NONCE";
    private static final String REPORT_PARAMETER_SUBSTITUTION = "CSP.REPORT.PARAMS";
    private static final String UPGRADE_INSECURE_REQUESTS_SUBSTITUTION = "UPGRADE.INSECURE.REQUESTS";
    private static final String HEADER_NONCE = "org.labkey.filters.ContentSecurityPolicyFilter#NONCE";  // needs to match PageConfig.HEADER_NONCE

    private static final Map<ContentSecurityPolicyType, ContentSecurityPolicyFilter> CSP_FILTERS = new CopyOnWriteHashMap<>();

    // Lock that protects the static data structures below
    private static final Object SUBSTITUTION_LOCK = new Object();
    private static final Map<Directive, SetValuedMap<String, String>> ALLOWED_SOURCES = new HashMap<>();

    public static final String FEATURE_FLAG_DISABLE_ENFORCE_CSP = "disableEnforceCsp";
    public static final String FEATURE_FLAG_FORWARD_CSP_REPORTS = "forwardCspReports";

    // Regenerate and stash on every "allowed source" change as a convenience (so every filter doesn't need to recalculate
    // it on every init() and change)
    private static Map<String, String> SUBSTITUTION_MAP = Collections.emptyMap();

    // Per-filter-instance parameters that are set in init() and never changed
    private ContentSecurityPolicyType _type = ContentSecurityPolicyType.Enforce;
    private @NotNull String _cspVersion = "Unknown";
    // These two are effectively @NotNull since they are set to non-null values in init() and never changed
    private String _stashedTemplate = null;
    private String _reportToEndpointName = null;

    // Per-filter-instance settings are initialized on first request and reset when base server URL or allowed sources
    // change. Don't reference this directly; always use ensureSettings().
    private volatile @Nullable CspFilterSettings _settings = null;

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
        // ReactJS hot reload uses localhost port 3001. If in dev mode, allow the browser to access that port for fonts
        // and connections. Also allow webpack: protocol for source map loading by some external packages.
        if (AppProps.getInstance().isDevMode())
        {
            registerAllowedSources("reactjs.hot.reload", Directive.Connection, "localhost:3001 ws://localhost:3001 webpack:");
            registerAllowedSources("reactjs.hot.reload", Directive.Font, "localhost:3001");
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
                s = substituteReportParams(s);

                _stashedTemplate = s;

                extractCspVersion(s);
            }
            else if ("disposition".equalsIgnoreCase(paramName))
            {
                String s = paramValue.trim();
                if (!"report".equalsIgnoreCase(s) && !"enforce".equalsIgnoreCase(s))
                    throw new ServletException("ContentSecurityPolicyFilter is misconfigured, unexpected disposition value: " + s);
                if ("report".equalsIgnoreCase(s))
                    _type = ContentSecurityPolicyType.Report;
            }
            else
            {
                throw new ServletException("ContentSecurityPolicyFilter is misconfigured, unexpected parameter name: " + paramName);
            }
        }

        if (CSP_FILTERS.put(getType(), this) != null)
            throw new ServletException("ContentSecurityPolicyFilter is misconfigured, duplicate policies of type: " + getType());

        // configure a different endpoint for each type to convey the correct csp version (eXX vs. rXX)
        _reportToEndpointName = "csp-" + getType().name().toLowerCase();
    }

    private String substituteReportParams(String expression)
    {
        return StringExpressionFactory.create(expression, false, NullValueBehavior.KeepSubstitution)
            .eval(Map.of(REPORT_PARAMETER_SUBSTITUTION, "labkeyVersion=" + PageFlowUtil.encodeURIComponent(AppProps.getInstance().getReleaseVersion())));
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

        // Replace sequences of multiple spaces with a single space
        s = s.replaceAll(" +", " ");

        return s;
    }

    /**
     * Extract the cspVersion parameter value from the report-uri directive, if possible. Otherwise, cspVersion is left
     * as "Unknown". This value is reported as part of usage metrics.
     */
    private void extractCspVersion(String s)
    {
        // Simple parser that should be compliant with https://www.w3.org/TR/CSP3/#parse-serialized-policy
        Map<String, String> cspMap = Arrays.stream(s.split(";"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .map(line -> line.split("\\s+", 2))
            .filter(parts -> parts.length == 2)
            .collect(LabKeyCollectors.toCaseInsensitiveLinkedMap(parts -> parts[0], parts -> parts[1]));

        String directive = "report-uri";
        String reportUri = cspMap.get(directive);

        if (reportUri != null)
        {
            try
            {
                ActionURL reportUrl =  new ActionURL(reportUri);
                String cspVersion = reportUrl.getParameter("cspVersion");

                if (null != cspVersion)
                    _cspVersion = cspVersion;
            }
            catch (IllegalArgumentException e)
            {
                LOG.warn("Unable to parse {} URI", directive, e);
            }
        }

        LOG.debug("CspVersion: {}", getCspVersion());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        if (request instanceof HttpServletRequest req && response instanceof HttpServletResponse resp)
        {
            CspFilterSettings settings = ensureSettings();

            if (getType() != ContentSecurityPolicyType.Enforce || !OptionalFeatureService.get().isFeatureEnabled(FEATURE_FLAG_DISABLE_ENFORCE_CSP))
            {
                Map<String, String> map = Map.of(NONCE_SUBST, getScriptNonceHeader(req));
                var csp = settings.getPolicyExpression().eval(map);
                resp.setHeader(getType().getHeaderName(), csp);

                // null if https: is not configured on this server
                String reportingEndpointsHeaderValue = settings.getReportingEndpointsHeaderValue();
                if (reportingEndpointsHeaderValue != null)
                    resp.addHeader("Reporting-Endpoints", reportingEndpointsHeaderValue);
            }
        }
        chain.doFilter(request, response);
    }

    public ContentSecurityPolicyType getType()
    {
        return _type;
    }

    public @NotNull String getCspVersion()
    {
        return _cspVersion;
    }

    public String getStashedTemplate()
    {
        return _stashedTemplate;
    }

    public String getReportToEndpointName()
    {
        return _reportToEndpointName;
    }

    private void clearSettings()
    {
        _settings = null;
    }

    private @NotNull CspFilterSettings ensureSettings()
    {
        String baseServerUrl = AppProps.getInstance().getBaseServerUrl();
        CspFilterSettings settings = _settings; // Stash a local copy to ensure consistency in the checks below

        // Reset settings if null or if base server URL has changed
        if (null == settings || !Objects.equals(baseServerUrl, settings.getPreviousBaseServerUrl()))
        {
            settings = _settings = new CspFilterSettings(this, baseServerUrl);
        }

        return settings;
    }

    // Hold all the mutable per-filter settings in a single object so they can be set atomically
    private static class CspFilterSettings
    {
        private final String _policyTemplate;
        private final String _reportingEndpointsHeaderValue;
        private final String _previousBaseServerUrl;
        private final StringExpression _policyExpression;

        private CspFilterSettings(ContentSecurityPolicyFilter filter, String baseServerUrl)
        {
            // Add "Reporting-Endpoints" header and "report-to" directive only if https: is configured on this
            // server. This ensures that browsers fall-back on report-uri if https: isn't configured.
            if (Strings.CI.startsWith(baseServerUrl, "https://"))
            {
                // Each filter adds its own "Reporting-Endpoints" header since we want to convey the correct version (eXX vs. rXX)
                @SuppressWarnings("DataFlowIssue")
                ActionURL violationUrl = PageFlowUtil.urlProvider(AdminUrls.class).getCspReportToURL(filter.getCspVersion());
                // Use an absolute URL so we always post to https:, even if the violating request uses http:
                _reportingEndpointsHeaderValue = filter.getReportToEndpointName() + "=\"" + filter.substituteReportParams(violationUrl.getURIString() + "&${CSP.REPORT.PARAMS}") + "\"";

                // Add "report-to" directive to the policy
                _policyTemplate = filter.getStashedTemplate() + " report-to " + filter.getReportToEndpointName() + " ;";
            }
            else
            {
                _policyTemplate = filter.getStashedTemplate();
                _reportingEndpointsHeaderValue = null;
            }

            _previousBaseServerUrl = baseServerUrl;

            final String allowSubstitutedPolicy;

            synchronized (SUBSTITUTION_LOCK)
            {
                allowSubstitutedPolicy = StringExpressionFactory.create(_policyTemplate, false, NullValueBehavior.KeepSubstitution)
                    .eval(SUBSTITUTION_MAP);
            }

            _policyExpression = StringExpressionFactory.create(allowSubstitutedPolicy, false, NullValueBehavior.ReplaceNullAndMissingWithBlank);
        }

        public String getPolicyTemplate()
        {
            return _policyTemplate;
        }

        public String getReportingEndpointsHeaderValue()
        {
            return _reportingEndpointsHeaderValue;
        }

        public String getPreviousBaseServerUrl()
        {
            return _previousBaseServerUrl;
        }

        public StringExpression getPolicyExpression()
        {
            return _policyExpression;
        }
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

    public static void registerAllowedSources(String key, Directive directive, String... allowedSources)
    {
        synchronized (SUBSTITUTION_LOCK)
        {
            if (allowedSources.length == 0)
                throw new IllegalStateException("Registering no sources is not allowed");
            LOG.debug("Registering {} for {}: {}", directive, key, Arrays.toString(allowedSources));
            SetValuedMap<String, String> multiMap = ALLOWED_SOURCES.computeIfAbsent(directive, d -> new HashSetValuedHashMap<>());
            Arrays.stream(allowedSources).forEach(s -> multiMap.put(key, s));
            regenerateSubstitutionMap();
        }
    }

    public static void unregisterAllowedSources(String key, Directive directive)
    {
        synchronized (SUBSTITUTION_LOCK)
        {
            LOG.debug("Unregistering {} for {}", directive, key);
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

    /**
     * Regenerate the substitution map on every register/unregister. The policy expression will be regenerated on the
     * next request (see {@link #ensureSettings()}).
     */
    public static void regenerateSubstitutionMap()
    {
        synchronized (SUBSTITUTION_LOCK)
        {
            SUBSTITUTION_MAP = ALLOWED_SOURCES.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                    e -> e.getKey().getSubstitutionKey(),
                    e -> e.getValue().values().stream()
                        .distinct()
                        .collect(Collectors.joining(" ")))
                );

            // Special case for object-src: default to 'none' if no admin overrides exist, Issue 53226
            SUBSTITUTION_MAP.putIfAbsent(Directive.Object.getSubstitutionKey(), "'none'");

            // Add an empty substitution for sources that lack registrations. This strips them from the stashed policy,
            // meaning less work on every request.
            Arrays.stream(Directive.values())
                .forEach(dir -> SUBSTITUTION_MAP.putIfAbsent(dir.getSubstitutionKey(), ""));

            SUBSTITUTION_MAP.put(UPGRADE_INSECURE_REQUESTS_SUBSTITUTION, AppProps.getInstance().isSSLRequired() ? "upgrade-insecure-requests;" : "");

            // Tell each registered ContentSecurityPolicyFilter to clear its settings so the next request recreates them
            // using the new substitution map
            CSP_FILTERS.values().forEach(ContentSecurityPolicyFilter::clearSettings);
        }
    }

    public static boolean hasCsp(ContentSecurityPolicyType type)
    {
        return CSP_FILTERS.get(type) != null;
    }

    public static List<String> getMissingSubstitutions(ContentSecurityPolicyType type)
    {
        ContentSecurityPolicyFilter filter = CSP_FILTERS.get(type);
        final List<String> ret;
        if (filter == null)
        {
            ret = Collections.emptyList();
        }
        else
        {
            String template = filter.ensureSettings().getPolicyTemplate();
            ret = Arrays.stream(Directive.values())
                .map(dir -> "${" + dir.getSubstitutionKey() + "}")
                .filter(key -> !template.contains(key))
                .toList();
        }

        return ret;
    }

    public static void registerMetricsProvider()
    {
        UsageMetricsService.get().registerUsageMetrics("API", () -> Map.of("cspFilters", CSP_FILTERS.values().stream()
            .collect(Collectors.toMap(ContentSecurityPolicyFilter::getType,
                filter -> {
                    CspFilterSettings settings = filter.ensureSettings();
                    return Map.of(
                        "version", filter.getCspVersion(),
                        "csp", settings.getPolicyTemplate(),
                        "cspSubstituted", settings.getPolicyExpression().getSource()
                    );
                }))));
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
                    connect-src 'self' http://www.labkey.org localhost:* ws: ${LABKEY.ALLOWED.CONNECTIONS} ;
                    object-src https://* 'none' ;
                    style-src 'self' https: 'unsafe-inline' ;
                    img-src 'self' https: data: ;
                    font-src 'self' http://www.labkey.com https://* http: https: data: ;
                    script-src 'unsafe-eval' 'strict-dynamic' 'nonce-${REQUEST.SCRIPT.NONCE}' ;
                    base-uri 'self' ;
                    frame-ancestors 'self' ;
                    report-uri /admin-contentsecuritypolicyreport.api?${CSP.REPORT.PARAMS} https://*;""".replace('\n', ' ');

            Assert.assertEquals(expected, filterPolicy(fakePolicyForTesting));
        }

        @Test
        public void testSubstitutionMap()
        {
            synchronized (SUBSTITUTION_LOCK)
            {
                // Ensure the substitution map has been initialized; otherwise the finally block asserts will fail
                regenerateSubstitutionMap();
                // Make a deep copy of ALLOWED_SOURCES so we can restore it after testing
                int sourceMapSize = ALLOWED_SOURCES.size();
                int substitutionMapSize = SUBSTITUTION_MAP.size();
                Map<Directive, SetValuedMap<String, String>> savedSources = ALLOWED_SOURCES.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> new HashSetValuedHashMap<>(e.getValue())));

                try
                {
                    ALLOWED_SOURCES.clear();
                    regenerateSubstitutionMap();

                    // Initial checks
                    assertTrue(ALLOWED_SOURCES.isEmpty());
                    verifySubstitutionMapSize(0);
                    // All "allowed sources" substitutions should be replaced with empty string or 'none' (object-src)
                    verifySubstitutionInPolicyExpressions(".SOURCES}", 0);
                    // Should have been substitution in init()
                    verifySubstitutionInPolicyExpressions("${CSP.REPORT.PARAMS}", 0);
                    verifySubstitutionInPolicyExpressions("${", 0);

                    // Now unregister and register sources for each Directive, testing expectations along the way
                    unregisterAllowedSources("foo", Directive.Connection);
                    assertTrue(ALLOWED_SOURCES.isEmpty());
                    verifySubstitutionMapSize(0);
                    registerAllowedSources("foo", Directive.Connection, "MySource");
                    assertEquals(1, ALLOWED_SOURCES.size());
                    verifySubstitutionMapSize(1);
                    verifySubstitutionInPolicyExpressions("MySource", 1);
                    registerAllowedSources("bar", Directive.Connection, "MySource");
                    assertEquals(1, ALLOWED_SOURCES.size());
                    verifySubstitutionMapSize(1);
                    verifySubstitutionInPolicyExpressions("MySource", 1); // Duplicate source should be filtered out

                    unregisterAllowedSources("font", Directive.Font);
                    registerAllowedSources("font", Directive.Font, "MySource");
                    assertEquals(2, ALLOWED_SOURCES.size());
                    verifySubstitutionMapSize(2);
                    verifySubstitutionInPolicyExpressions("MySource", 2);
                    registerAllowedSources("font2", Directive.Font, "MyFontSource");
                    assertEquals(2, ALLOWED_SOURCES.size());
                    verifySubstitutionMapSize(2);
                    verifySubstitutionInPolicyExpressions("MySource", 2);
                    verifySubstitutionInPolicyExpressions("MyFontSource", 1);
                    unregisterAllowedSources("font2", Directive.Font);
                    assertEquals(2, ALLOWED_SOURCES.size());
                    verifySubstitutionMapSize(2);
                    verifySubstitutionInPolicyExpressions("MySource", 2);
                    verifySubstitutionInPolicyExpressions("MyFontSource", 0);
                    unregisterAllowedSources("font", Directive.Font);
                    assertEquals(2, ALLOWED_SOURCES.size()); // Font entry still exists but should be empty
                    assertTrue(ALLOWED_SOURCES.get(Directive.Font).isEmpty());
                    verifySubstitutionMapSize(1);// Back to the way it was
                    verifySubstitutionInPolicyExpressions("MySource", 1);
                    verifySubstitutionInPolicyExpressions("MyFontSource", 0);

                    unregisterAllowedSources("frame", Directive.Frame);
                    registerAllowedSources("frame", Directive.Frame, "FrameSource", "FrameStore");
                    assertEquals(3, ALLOWED_SOURCES.size());
                    verifySubstitutionMapSize(2);
                    verifySubstitutionInPolicyExpressions("FrameSource", 1);
                    verifySubstitutionInPolicyExpressions("FrameStore", 1);

                    unregisterAllowedSources("style", Directive.Style);
                    registerAllowedSources("style", Directive.Style, "StyleSource", "MoreStylishStore");
                    assertEquals(4, ALLOWED_SOURCES.size());
                    verifySubstitutionMapSize(3);
                    verifySubstitutionInPolicyExpressions("StyleSource", 1);
                    verifySubstitutionInPolicyExpressions("MoreStylishStore", 1);

                    unregisterAllowedSources("image", Directive.Image);
                    registerAllowedSources("image", Directive.Image, "ImageSource", "BetterImageStore");
                    assertEquals(5, ALLOWED_SOURCES.size());
                    verifySubstitutionMapSize(4);
                    verifySubstitutionInPolicyExpressions("ImageSource", 1);
                    verifySubstitutionInPolicyExpressions("BetterImageStore", 1);

                    unregisterAllowedSources("object", Directive.Object);
                    verifySubstitutionInPolicyExpressions("'none'", 1);
                    registerAllowedSources("object", Directive.Object, "ObjectSource", "BetterObjectStore");
                    assertEquals(6, ALLOWED_SOURCES.size());
                    verifySubstitutionMapSize(4); // Adding to object-src doesn't change the non-empty count
                    verifySubstitutionInPolicyExpressions("'none'", 0);
                    verifySubstitutionInPolicyExpressions("ObjectSource", 1);
                    verifySubstitutionInPolicyExpressions("BetterObjectStore", 1);
                }
                finally
                {
                    // Restore the previous ALLOWED_SOURCES
                    ALLOWED_SOURCES.clear();
                    ALLOWED_SOURCES.putAll(savedSources);
                    regenerateSubstitutionMap();
                    assertEquals(sourceMapSize, ALLOWED_SOURCES.size());
                    assertEquals(substitutionMapSize, SUBSTITUTION_MAP.size());
                }
            }
        }

        private void verifySubstitutionInPolicyExpressions(String value, int expectedCount)
        {
            List<String> failures = CSP_FILTERS.values().stream()
                .map(filter -> filter.ensureSettings().getPolicyExpression().eval(Map.of()))
                .filter(policy -> StringUtils.countMatches(policy, value) != expectedCount)
                .toList();

            if (!failures.isEmpty())
            {
                fail("Occurrences of value \"" + value + "\" was not the expected count (" + expectedCount + ") in policies [\"" + String.join("\", \"", failures) + "\"]");
            }
        }

        private void verifySubstitutionMapSize(long expectedNonEmptyValues)
        {
            // Actual map size should stay static throughout the test
            int expectedSubstitutionMapSize = Directive.values().length + 1; // One extra for UPGRADE.INSECURE.REQUESTS
            if (AppProps.getInstance().isSSLRequired())
                expectedNonEmptyValues++;

            assertEquals(expectedSubstitutionMapSize, SUBSTITUTION_MAP.size());
            long nonEmptyValues = SUBSTITUTION_MAP.entrySet().stream().filter(e -> !e.getValue().isEmpty()).count();
            assertEquals(expectedNonEmptyValues + 1, nonEmptyValues); // One extra expected because of OBJECT.SOURCES default value
        }
    }
}
