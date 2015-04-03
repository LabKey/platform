package org.labkey.authentication.cas;

import edu.yale.tp.cas.AttributesType;
import edu.yale.tp.cas.ServiceResponseDocument;
import edu.yale.tp.cas.ServiceResponseType;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.values.XmlAnyTypeImpl;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.security.AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by adam on 3/29/2015.
 */
public class CasManager
{
    private static final String NAMESPACE = "http://www.yale.edu/tp/cas";
    private static final CasManager INSTANCE = new CasManager();

    private static final String SET_KEY = "CasAuthenticationProperties";
    private static final String SERVER_URL_KEY = "ServerURL";

    public static CasManager getInstance()
    {
        return INSTANCE;
    }

    private CasManager()
    {
    }

    String getServerUrlProperty()
    {
        PropertyMap map = PropertyManager.getWritableProperties(SET_KEY, true);

        return map.get(SERVER_URL_KEY);
    }

    void saveServerUrlProperty(String serverUrl, User user)
    {
        PropertyMap map = PropertyManager.getWritableProperties(SET_KEY, true);
        map.put(SERVER_URL_KEY, serverUrl);
        map.save();

        AuthProviderConfigAuditEvent event = new AuthProviderConfigAuditEvent(
                ContainerManager.getRoot().getId(), CasAuthenticationProvider.NAME + " provider configuration was changed.");
        event.setChanges("Server URL");
        AuditLogService.get().addEvent(user, event);
    }

    public URLHelper getServerURL(String action)
    {
        // TODO: Save and load base server URL from properties
        String baseURL = getServerUrlProperty();

        try
        {
            URLHelper url = new URLHelper(baseURL + "/" + action);
            url.addParameter("service", getServiceParameter());

            return url;
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    public URLHelper getLoginURL() throws URISyntaxException
    {
        return getServerURL("login");
    }

    public String getServiceParameter()
    {
        // Return absolute URL of our validate action
        return CasController.getValidateURL().getURIString();
    }

    // TODO: Make this return a ValidEmail
    public @Nullable String validate(String ticket) throws IOException, XmlException
    {
        // Assume CAS 3.0 protocol
        URLHelper validateURL = getServerURL("p3/serviceValidate");
        validateURL.addParameter("ticket", ticket);

        URL url = new URL(validateURL.getURIString());
        URLConnection con = url.openConnection();

        ServiceResponseDocument doc;

        try (InputStream is = con.getInputStream())
        {
            doc = ServiceResponseDocument.Factory.parse(is);
        }

        ServiceResponseType response = doc.getServiceResponse();

        if (response.isSetAuthenticationSuccess())
        {
            AttributesType attributes = response.getAuthenticationSuccess().getAttributes();

            return getValue(attributes, "email");
        }
        else
        {
            return null;
        }
    }

    private @Nullable String getValue(XmlObject object, String name)
    {
        XmlObject[] objects = object.selectChildren(NAMESPACE, name);

        return objects.length > 0 ? ((XmlAnyTypeImpl)objects[0]).getStringValue() : null;
    }
}
