package org.labkey.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Assert;
import org.junit.Test;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


/**
 * For example CSPs, see csp.enforce and csp.report property examples in application.properties
 * Do not use those examples for any production environment without understanding the meaning of each directive!
 */

public class ContentSecurityPolicyFilter implements Filter
{
    public static final String FEATURE_FLAG_DISABLE_ENFORCE_CSP = "disableEnforceCsp";

    private static final String NONCE_SUBST = "REQUEST.SCRIPT.NONCE";
    private static final String ALLOWED_CONNECT_SUBSTITUTION = "LABKEY.ALLOWED.CONNECTIONS";
    private static final String REPORT_PARAMETER_SUBSTITUTION = "CSP.REPORT.PARAMS";
    private static final String HEADER_NONCE = "org.labkey.filters.ContentSecurityPolicyFilter#NONCE";  // needs to match PageConfig.HEADER_NONCE
    private static final Map<String, List<String>> ALLOWED_CONNECTION_SOURCES = new ConcurrentHashMap<>();

    private static String connectionSrc = "";

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
        // ReactJS hot reload uses localhost port 3001. If in dev mode, allow browser to access that port.
        if (AppProps.getInstance().isDevMode())
            registerAllowedConnectionSource("reactjs.hot.reload", "localhost:3001 ws:");
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
                Map<String, String> map = Map.of(
                    NONCE_SUBST, getScriptNonceHeader(req),
                    ALLOWED_CONNECT_SUBSTITUTION, connectionSrc
                );
                var csp = policyExpression.eval(map);
                resp.setHeader(type.getHeaderName(), csp);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Return concatenated list of allowed connection hosts
     */
    private static String getAllowedConnectionsHeader(Collection<List<String>> allowedConnectionSources)
    {
        //Remove substitution parameter if no sources are registered
        if (allowedConnectionSources.isEmpty())
            return "";

        return allowedConnectionSources.stream().flatMap(Collection::stream).distinct().collect(Collectors.joining(" "));
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

    public static void registerAllowedConnectionSource(String key, String... allowedUrls)
    {
        ALLOWED_CONNECTION_SOURCES.put(key, Collections.unmodifiableList(Arrays.asList(allowedUrls)));
        connectionSrc = getAllowedConnectionsHeader(ALLOWED_CONNECTION_SOURCES.values());
    }

    public static void unregisterAllowedConnectionSource(String key)
    {
        ALLOWED_CONNECTION_SOURCES.remove(key);
        connectionSrc = getAllowedConnectionsHeader(ALLOWED_CONNECTION_SOURCES.values());
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
    }
}
