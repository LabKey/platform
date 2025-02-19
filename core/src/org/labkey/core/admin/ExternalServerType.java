package org.labkey.core.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.filters.ContentSecurityPolicyFilter;
import org.springframework.validation.BindException;

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
        public void setHosts(Collection<String> hosts, User user)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setExternalSourceHosts(hosts);
            props.save(user);

            // Refresh the CSP with new values.
            ContentSecurityPolicyFilter.unregisterAllowedConnectionSource(EXTERNAL_SOURCE_HOSTS_KEY);
            ContentSecurityPolicyFilter.registerAllowedConnectionSource(EXTERNAL_SOURCE_HOSTS_KEY, getHosts().toArray(new String[0]));
        }

        @Override
        public HtmlString getTitle()
        {
            return HtmlString.of(String.format("External %1$s Host", name()));
        }

        @Override
        public HtmlString getLabel()
        {
            return HtmlString.of("Host");
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
        public void setHosts(Collection<String> hosts, User user)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setExternalRedirectHosts(hosts);
            props.save(user);
        }

        @Override
        public HtmlString getTitle()
        {
            return HtmlString.of(String.format("External %1$s Host", name()));
        }

        @Override
        public HtmlString getLabel()
        {
            return HtmlString.of("Host");
        }
    },
    FileExtension {
        @Override
        public HtmlString getDescription()
        {
            return HtmlString.unsafe("""
                <div style="width: 700px">
                    <div>
                        This list is the set of file extensions that LabKey will accept for uploads. Any extension that is not in this list will be rejected, this includes multiple extensions. For example, .gz is not sufficient to allow .tar.gz; you must specify .tar.gz. If the list is empty, then this check is ignored.
                    </div>
                    <div>
                    e.g., .tsv, .csv, .tar.gz, .sky.zip, etc.
                    </div>
                </div>
                """);
        }

        @Override
        public List<String> getHosts()
        {
            return AppProps.getInstance().getAllowedExtensions();
        }

        @Override
        public void setHosts(Collection<String> allowedExtensions, User user)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setAllowedFileExtensions(allowedExtensions);
            props.save(user);
        }

        @Override
        public void validateHostFormat(String externalHost, BindException errors)
        {
            if (StringUtils.isEmpty(externalHost))
                errors.addError(new LabKeyError("File extension must not be blank."));
            else if (!externalHost.startsWith("."))
                errors.addError(new LabKeyError("File extension must start with a '.'"));
        }

        @Override
        public HtmlString getTitle()
        {
            return HtmlString.of("Allowed File Extension");
        }

        @Override
        public HtmlString getLabel()
        {
            return HtmlString.of("Extension");
        }
    };

    private static final AuthorityValidator AUTHORITY_VALIDATOR = new AuthorityValidator(UrlValidator.ALLOW_LOCAL_URLS);
    private static final String EXTERNAL_SOURCE_HOSTS_KEY = "External Sources";
    public static String getExternalSourceHostsKey()
    {
        return EXTERNAL_SOURCE_HOSTS_KEY;
    }

    public abstract HtmlString getDescription();
    public abstract List<String> getHosts();
    public abstract void setHosts(Collection<String> redirectHosts, User user);
    public abstract HtmlString getTitle();
    public abstract HtmlString getLabel();


    public String getHelpTopic()
    {
        return "externalHosts#" + name().toLowerCase();
    }

    public URLHelper getSuccessURL(Container container)
    {
        return new ActionURL(AdminController.ExternalHostsAdminAction .class, container).addParameter("type", name());
    }

    @JsonIgnore
    public void validateHostFormat(String externalHost, BindException errors)
    {
        if (StringUtils.isEmpty(externalHost))
        {
            errors.addError(new LabKeyError("External host name must not be blank."));
        }
        else if (!AUTHORITY_VALIDATOR.isValidAuthority(externalHost))
        {
            errors.addError(new LabKeyError(String.format("External host name %1$s is not formatted correctly", externalHost)));
        }
    }

    private static class AuthorityValidator extends UrlValidator
    {
        public AuthorityValidator(long options)
        {
            super(options);
        }

        @Override
        public boolean isValidAuthority(String authority)
        {
            String base = authority.startsWith("*.") ? authority.substring(2) : authority;
            return super.isValidAuthority(base);
        }
    };
}
