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
import org.springframework.validation.BindException;

import java.util.Collection;
import java.util.List;

public enum AllowListType
{
    Redirect {
        private static final AuthorityValidator AUTHORITY_VALIDATOR = new AuthorityValidator(UrlValidator.ALLOW_LOCAL_URLS);

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
        public List<String> getValues()
        {
            return AppProps.getInstance().getExternalRedirectHosts();
        }

        @Override
        public void setValues(Collection<String> allowedHosts, User user)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setExternalRedirectHosts(allowedHosts);
            props.save(user);
        }

        @Override
        @JsonIgnore
        public void validateValueFormat(String host, BindException errors)
        {
            if (StringUtils.isEmpty(host))
            {
                errors.addError(new LabKeyError("Redirect host name must not be blank."));
            }
            else if (!AUTHORITY_VALIDATOR.isValidAuthority(host))
            {
                errors.addError(new LabKeyError(String.format("Redirect host name %1$s is not formatted correctly", host)));
            }
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
        public List<String> getValues()
        {
            return AppProps.getInstance().getAllowedExtensions();
        }

        @Override
        public void setValues(Collection<String> allowedExtensions, User user)
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setAllowedFileExtensions(allowedExtensions);
            props.save(user);
        }

        @Override
        @JsonIgnore
        public void validateValueFormat(String value, BindException errors)
        {
            if (StringUtils.isEmpty(value))
                errors.addError(new LabKeyError("File extension must not be blank."));
            else if (!value.startsWith("."))
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

    public abstract HtmlString getDescription();
    public abstract List<String> getValues();
    public abstract void setValues(Collection<String> allowedValues, User user);
    public abstract void validateValueFormat(String value, BindException errors);
    public abstract HtmlString getTitle();
    public abstract HtmlString getLabel();

    public String getHelpTopic()
    {
        return "externalHosts#" + name().toLowerCase();
    }

    public URLHelper getSuccessURL(Container container)
    {
        return new ActionURL(AdminController.AllowListAction.class, container).addParameter("type", name());
    }
}
