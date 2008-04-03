package org.labkey.api.util;

import org.apache.log4j.Category;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.ActionURL;

import javax.mail.internet.ContentType;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Apr 20, 2006
 */
public class MothershipReport implements Runnable
{
    private static Category _log = Category.getInstance(MothershipReport.class);

    private final URL _url;
    private final Map<String, String> _params = new HashMap<String, String>();
    private int _responseCode = -1;
    private String _content;
    public static final String MOTHERSHIP_STATUS_HEADER_NAME = "MothershipStatus";
    public static final String MOTHERSHIP_STATUS_SUCCESS = "Success";

    public MothershipReport(String action) throws MalformedURLException
    {
        ActionURL url = new ActionURL("Mothership", action + ".post", "/_mothership");
        if (AppProps.getInstance().isDevMode())
        {
            // Don't submit to the mothership server, go to the local machine
            _url = new URL(AppProps.getInstance().getScheme(), "localhost", AppProps.getInstance().getServerPort(), url.toString());
        }
        else
        {
            url.setContextPath("");
            _url = new URL("https", "www.labkey.org", 443, url.toString());
        }
    }

    public int getResponseCode()
    {
        return _responseCode;
    }

    public void addParam(String key, long value)
    {
        addParam(key, Long.toString(value));
    }

    public void addParam(String key, boolean value)
    {
        addParam(key, Boolean.toString(value));
    }

    public void addParam(String key, String value)
    {
        if (_params.containsKey(key))
        {
            throw new IllegalArgumentException("This report already has a " + key + " parameter");
        }
        _params.put(key, value);
    }

    public void run()
    {
        try
        {
            HttpURLConnection connection = openConnectionWithRedirects(_url);
            InputStream in = null;
            try
            {
                _responseCode = connection.getResponseCode();
                if (_responseCode == 200 && MOTHERSHIP_STATUS_SUCCESS.equals(connection.getHeaderField(MOTHERSHIP_STATUS_HEADER_NAME)))
                {
                    String encoding = "UTF-8";
                    if (connection.getContentType() != null)
                    {
                        ContentType contentType = new ContentType(connection.getContentType());
                        encoding = contentType.getParameter("charset");
                    }
                    in = connection.getInputStream();
                    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                    byte[] b = new byte[1024];
                    int i;
                    while ((i = in.read(b)) != -1)
                    {
                        bOut.write(b, 0, i);
                    }
                    _content = bOut.toString(encoding);
                }
            }
            finally
            {
                if (in != null) { try { in.close(); } catch (IOException e) {} }
                connection.disconnect();
            }
        }
        catch (Exception e)
        {
            // Don't bother the client if this report fails
            boolean b = false;
        }
    }

    private HttpURLConnection openConnectionWithRedirects(URL url)
            throws IOException
    {
        boolean redirect;
        HttpURLConnection connection;
        int redirectCount = 0;
        do
        {
            redirect = false;
            connection = submitRequest(url);
            int responseCode = connection.getResponseCode();
            if (responseCode >= 300 && responseCode <= 307 && responseCode != 306 && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED)
            {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location != null)
                {
                    URL target = new URL(url, location);
                    if ((target.getProtocol().equals("http") || target.getProtocol().equals("https")) && redirectCount < 5)
                    {
                        redirect = true;
                        redirectCount++;
                        url = target;
                    }
                }
            }
        }
        while (redirect && redirectCount < 5);
        return connection;
    }

    private HttpURLConnection submitRequest(URL url) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        if (connection instanceof HttpsURLConnection)
        {
            HttpsUtil.disableValidation((HttpsURLConnection)connection);
        }
        // We'll handle redirects on our own which makes sure that we
        // POST instead of GET after being redirected
        connection.setInstanceFollowRedirects(false);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        PrintWriter out = new PrintWriter(connection.getOutputStream(), true);
        boolean first = true;
        for (Map.Entry<String, String> entry : _params.entrySet())
        {
            String value = entry.getValue();
            if (value != null)
            {
                if (!first)
                {
                    out.print("&");
                }
                first = false;
                out.println(entry.getKey() + "=" + URLEncoder.encode(value));
            }
        }
        out.close();
        connection.connect();
        return connection;
    }

    public void addServerSessionParams()
    {
        Map<String, String> coreModuleProperties = ModuleLoader.getInstance().getCoreModule().getMetaData();
        String svnRevision = coreModuleProperties.get("SVNRevision");
        if (svnRevision != null)
        {
            addParam("svnRevision", svnRevision);
        }
        addParam("runtimeOS", System.getProperty("os.name"));
        addParam("javaVersion", System.getProperty("java.version"));
        addParam("enterprisePipelineEnabled", AppProps.getInstance().hasPipelineCluster());
        String[] ldapServers = AppProps.getInstance().getLDAPServersArray();
        String ldapDomain = AppProps.getInstance().getLDAPDomain();
        boolean ldapEnabled = ldapServers.length > 0 && ldapServers[0].trim().length() > 0 && ldapDomain != null && ldapDomain.trim().length() > 0;
        addParam("ldapEnabled", ldapEnabled);

        DbSchema schema = CoreSchema.getInstance().getSchema();
        if (schema != null)
        {
            addParam("databaseProductName", schema.getDatabaseProductName());
            addParam("databaseProductVersion", schema.getDatabaseProductVersion());
            addParam("databaseDriverName", schema.getDriverName());
            addParam("databaseDriverVersion", schema.getDriverVersion());
        }
        addParam("serverSessionGUID", AppProps.getInstance().getServerSessionGUID());
        addParam("serverGUID", AppProps.getInstance().getServerGUID());
    }

    public String getContent()
    {
        return _content;
    }
}
