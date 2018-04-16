package org.labkey.api.security;


import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This is not a defense against any particular vulnerability
 * however, this can help protect against wasting resources being consumed by scanners
 */
public class BlacklistFilter
{
    static Logger _log = Logger.getLogger(BlacklistFilter.class);
    static Cache<String,Suspicious> suspiciousMap = CacheManager.getStringKeyCache(1_000, CacheManager.HOUR, "suspicious cache");


    private static String getBrowserKey(HttpServletRequest req)
    {
        return req.getRemoteHost() + '|' + req.getHeader("User-Agent");
    }


    static void handleBadRequest(HttpServletRequest req)
    {
        String key = getBrowserKey(req);
        final String host = req.getRemoteHost();
        final String userAgent = req.getHeader("User-Agent");
        Suspicious s = suspiciousMap.get(key, null, (k, a) -> new Suspicious(host,userAgent));
        int count = s.add(req);
        String uri = req.getRequestURI();
        String q = req.getQueryString();
        if (count == 1 || count == 20)
        {
            _log.log(count==1?Level.INFO:Level.WARN,
            count + " suspicious request(s) by this host: " + host + " " + userAgent + (null == s.user ? "" : "(" + s.user + ")") + "\n" + uri + (null==q ? "" : "?" + q));
        }
    }


    static void handleNotFound(HttpServletRequest req)
    {
        if (isSuspicious(req.getRequestURI(),req.getQueryString()))
        {
            handleBadRequest(req);
        }
    }


    static boolean isOnBlacklist(HttpServletRequest req)
    {
        String key = getBrowserKey(req);
        Suspicious s = suspiciousMap.get(key);
        return s != null && s.getCount() > 20;
    }


    private static boolean isSuspicious(String request_path, String query)
    {
        final char REPLACEMENT_CHAR = '\uFFFD';
        final Set<String> suspectExtensions = PageFlowUtil.set("ini","dll","jsp","asp","aspx","php","pl","vbs");
        final Pattern sql = Pattern.compile(";.*select.*from.*(dbo|master|sys)\\.");
        final Pattern pipe = Pattern.compile("\\|\\s*(ls|id|echo|vol|curl|wget)");

        try
        {
            // CHARS
            // contains %2E %2F
            String raw_path = request_path.toLowerCase();
            // why encode '.' or '/'???
            if (raw_path.contains("%252e") || raw_path.contains("%252f") || raw_path.contains("%2e") || raw_path.contains("%2f") || raw_path.indexOf(REPLACEMENT_CHAR) != -1)
                return true;
            String decode_path = PageFlowUtil.decode(raw_path);
            if (decode_path.indexOf(REPLACEMENT_CHAR) != -1)
                return true;
            // PATH
            Path path = Path.parse(decode_path);
            Path norm = path.normalize();
            if (null == norm || !path.equals(norm))
                return true;
            for (String part : path)
            {
                if (part.startsWith("wp-") || part.endsWith("-inf") || part.equals("etc"))
                    return true;
            }
            // EXTENSIONS
            String ext = FileUtil.getExtension(path.getName());
            if (null != ext && !path.contains("_webdav"))
                if (suspectExtensions.contains(ext))
                    return true;
            // QUERY STRING
            if (!StringUtils.isBlank(query))
            {
                for (Pair<String, String> p : PageFlowUtil.fromQueryString(query.toLowerCase()))
                {
                    String key = p.first;
                    String value = p.second;
                    if (key.indexOf(REPLACEMENT_CHAR)!=-1 || value.indexOf(REPLACEMENT_CHAR)!=-1)
                        return true;
                    if (sql.matcher(value).find() || pipe.matcher(value).find())
                        return true;
                    if (value.contains("/../../") || value.contains("/etc/") || value.endsWith("win.ini") || value.endsWith("boot.ini"))
                        return true;
                    if (!"returnurl".equals(key) && value.startsWith("http://") || value.startsWith("https://"))
                        return true;
                }
            }
            return false;
        }
        catch (IllegalArgumentException ex)
        {
            return true;
        }
    }

    public static Collection<Suspicious> reportSuspicious()
    {
        ArrayList<Suspicious> ret = new ArrayList<>();
        for (String key : suspiciousMap.getKeys())
        {
            Suspicious s = suspiciousMap.get(key);
            if (null == s)
                continue;
            Suspicious copy = s.clone();
            if (copy.getCount() > 0)
                ret.add(copy);
        }
        return ret;
    }

    public static class Suspicious
    {
        public final String host;
        public final String userAgent;
        public String user = null;
        public int count = 0;
        public Suspicious(String host, String userAgent)
        {
            this.host = host;
            this.userAgent = userAgent;
        }

        public synchronized Suspicious clone()
        {
            Suspicious c = new Suspicious(this.host,this.userAgent);
            c.user = this.user;
            c.count = this.count;
            return c;
        }

        public synchronized int getCount()
        {
            return count;
        }

        public synchronized int add(HttpServletRequest req)
        {
            count++;
            User u = (User)req.getUserPrincipal();
            if (u != null && !u.isGuest())
                 this.user = u.getEmail();
            return count;
        }
    }

    /*
    public static class TestCase extends Assert
    {
        @Test
        public void testSuspicious() throws Exception
        {
            try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("org/labkey/api/security/urls.txt"))
            {
                List<String> urls = IOUtils.readLines(is,"UTF-8");
                for (String url : urls)
                {
                    String path = url;
                    String query = "";
                    int q = url.indexOf('?');
                    if (q != -1)
                    {
                        path = url.substring(0,q);
                        query = url.substring(q+1);
                    }
                    if (isSuspicious(path,query))
                        System.err.println(url);
                    else
                        System.out.println(url);
                }
            }
        }

        @Test
        public void testNotSuspicious()
        {
        }
    }
    */
}
