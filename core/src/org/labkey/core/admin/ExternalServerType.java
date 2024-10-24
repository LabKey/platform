package org.labkey.core.admin;

import org.labkey.api.data.Container;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.filters.ContentSecurityPolicyFilter;

import java.util.Collection;
import java.util.List;

public enum ExternalServerType
{
    Source {
        @Override
        public HtmlString getDescription()
        {
            return HtmlString.unsafe("""
                <div style="width: 700px">
                    <p>
                        For security reasons, LabKey Server restricts the hosts that can be used as resource origins. By default, only LabKey sources are allowed, other server URLs must be configured below to enable them to be used as script sources.
                        For more information on the security concern, please refer to the <a href="https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html#cross-origin-resource-sharing">OWASP cheat sheet</a>.
                    </p>
                    <p>
                        Add allowed source URLs or IP address as they will be referenced in script source values.
                        For example: www.myexternalhost.com or 1.2.3.4
                    </p>
                </div>
                """);
        }

        @Override
        public List<String> getHosts()
        {
            return AppProps.getInstance().getExternalSourceHosts();
        }

        @Override
        public void setHosts(Collection<String> hosts)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setExternalSourceHosts(hosts);
            props.save(null);

            // Refresh the CSP with new values.
            ContentSecurityPolicyFilter.unregisterAllowedConnectionSource(EXTERNAL_SOURCE_HOSTS_KEY);
            ContentSecurityPolicyFilter.registerAllowedConnectionSource(EXTERNAL_SOURCE_HOSTS_KEY, getHosts().toArray(new String[0]));
        }
    },
    Redirect {
        @Override
        public HtmlString getDescription()
        {
            return HtmlString.unsafe("""
                <div style="width: 700px">
                    <p>
                        For security reasons, LabKey Server restricts the host names that can be used in returnUrl parameters.
                        By default, only redirects to the same LabKey instance are allowed.
                        Other server host names must be configured below to allow them to be automatically redirected.
                        For more information on the security concern, please refer to the
                        <a href="https://cheatsheetseries.owasp.org/cheatsheets/Unvalidated_Redirects_and_Forwards_Cheat_Sheet.html">OWASP cheat sheet</a>.
                    </p>
                    <p>
                        Add allowed hosts based on the server name or IP address, as they will be referenced in returnUrl values.
                        For example: www.myexternalhost.com or 1.2.3.4
                    </p>
                </div>
                """);
        }

        @Override
        public List<String> getHosts()
        {
            return AppProps.getInstance().getExternalRedirectHosts();
        }

        @Override
        public void setHosts(Collection<String> hosts)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setExternalRedirectHosts(hosts);
            props.save(null);
        }
    };

    private static final String EXTERNAL_SOURCE_HOSTS_KEY = "External Sources";
    public static String getExternalSourceHostsKey()
    {
        return EXTERNAL_SOURCE_HOSTS_KEY;
    }

    public abstract HtmlString getDescription();
    public abstract List<String> getHosts();
    public abstract void setHosts(Collection<String> redirectHosts);

    public String getHelpTopic()
    {
        return "externalHosts#" + name().toLowerCase();
    }

    public URLHelper getSuccessURL(Container container)
    {
        return new ActionURL(AdminController.ExternalHostsAdminAction .class, container).addParameter("type", name());
    }
}
