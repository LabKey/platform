package org.labkey.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.OptionalFeatureService;
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


/** example usage,

 NOTE: LabKey does not yet support setting the "Report-To" header, so we do not support the report-to CSP directive.

 Example 1 : very strict, disallows 'external' websites, disallows unsafe-inline, but only reports violations (does not enforce)
 good for test automation!

  <pre>
      <filter>
        <filter-name>Content Security Policy Filter Filter</filter-name>
        <filter-class>org.labkey.filters.ContentSecurityPolicyFilter</filter-class>
        <init-param>
          <param-name>policy</param-name>
          <param-value>
            default-src 'self';
            connect-src 'self' ${LABKEY.ALLOWED.CONNECTIONS} ;
            object-src 'none' ;
            style-src 'self' 'unsafe-inline' ;
            img-src 'self' data: ;
            font-src 'self' data: ;
            script-src 'unsafe-eval' 'strict-dynamic' 'nonce-${REQUEST.SCRIPT.NONCE}';
            base-uri 'self' ;
            upgrade-insecure-requests ;
            frame-ancestors 'self' ;
            report-uri /labkey/admin-contentsecuritypolicyreport.api?${CSP.REPORT.PARAMS} ;
          </param-value>
        </init-param>
        <init-param>
          <param-name>disposition</param-name>
          <param-value>report</param-value>
        </init-param>
      </filter>
      <filter-mapping>
        <filter-name>Content Security Policy Filter Filter</filter-name>
        <url-pattern>/*</url-pattern>
      </filter-mapping>
  </pre>

 Example 2 : less strict but enforces directives, (NOTE: unsafe-inline is still required for many modules)

  <pre>
      <filter>
        <filter-name>Content Security Policy Filter Filter</filter-name>
        <filter-class>org.labkey.filters.ContentSecurityPolicyFilter</filter-class>
        <init-param>
          <param-name>policy</param-name>
          <param-value>
            default-src 'self' https: ;
            connect-src 'self' https: ${LABKEY.ALLOWED.CONNECTIONS} ;
            object-src 'none' ;
            style-src 'self' https: 'unsafe-inline' ;
            img-src 'self' data: ;
            font-src 'self' data: ;
            script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' 'nonce-${REQUEST.SCRIPT.NONCE}';
            base-uri 'self' ;
            upgrade-insecure-requests ;
            frame-ancestors 'self' ;
            report-uri /labkey/admin-contentsecuritypolicyreport.api?${CSP.REPORT.PARAMS} ;
          </param-value>
        </init-param>
        <init-param>
          <param-name>disposition</param-name>
          <param-value>enforce</param-value>
        </init-param>
      </filter>
      <filter-mapping>
        <filter-name>Content Security Policy Filter Filter</filter-name>
        <url-pattern>/*</url-pattern>
      </filter-mapping>
  </pre>

 Do not copy-and-paste these examples for any production environment without understanding the meaning of each directive!
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

    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {
        LogHelper.getLogger(ContentSecurityPolicyFilter.class, "CSP filter initialization").info("Initializing " + filterConfig.getFilterName());

        Enumeration<String> paramNames = filterConfig.getInitParameterNames();
        while (paramNames.hasMoreElements())
        {
            String paramName = paramNames.nextElement();
            String paramValue = filterConfig.getInitParameter(paramName);
            if ("policy".equalsIgnoreCase(paramName))
            {
                String s = paramValue.trim();
                s = s.replace( '\n', ' ' );
                s = s.replace( '\r', ' ' );
                s = s.replace( '\t', ' ' );
                s = s.replace((char)0x2018, (char)0x027);     // LEFT SINGLE QUOTATION MARK -> APOSTROPHE
                s = s.replace((char)0x2019, (char)0x027);     // RIGHT SINGLE QUOTATION MARK -> APOSTROPHE

                // This is temporary. TODO: Remove once we've propagated this substitution into our CSPs
                String directive = "report-uri";
                int reportUriIdx = StringUtils.indexOfIgnoreCase(s, directive);
                if (reportUriIdx != -1)
                {
                    int semicolonIdx = s.indexOf(';', reportUriIdx);

                    if (semicolonIdx != -1)
                    {
                        int urlStart = reportUriIdx + directive.length();
                        String oldUrl = s.substring(urlStart, semicolonIdx).stripTrailing();
                        if (!oldUrl.contains("?"))
                        {
                            String newUrl = oldUrl + "?${" + REPORT_PARAMETER_SUBSTITUTION + "}";
                            s = s.substring(0, urlStart) + newUrl + s.substring(urlStart + oldUrl.length());
                        }
                    }
                }

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

    @Override
    public void destroy()
    {
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
}
