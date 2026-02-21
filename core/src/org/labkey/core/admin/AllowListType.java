package org.labkey.core.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.Arrays;
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
                        For security reasons, external redirects are restricted to the host names in this list.
                        By default, only redirects to the same LabKey instance are allowed.
                        Other server host names must be configured below to allow redirects to them.
                        For more information on the security concern, please refer to the
                        <a href="https://cheatsheetseries.owasp.org/cheatsheets/Unvalidated_Redirects_and_Forwards_Cheat_Sheet.html">OWASP cheat sheet</a>.
                    </p>
                    <p>
                        Add allowed hosts based on the server name or IP address, as they will be referenced in parameters
                        such as returnUrl. An asterisk (*) follow by a dot acts as a wild card that matches any leading
                        subdomain for that host.
                        Examples: www.myexternalhost.com, *.myexternalhost.com, or 1.2.3.4
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
            else
            {
                if (AUTHORITY_VALIDATOR.isValidAuthority(host))
                {
                    // Validate wild card patterns
                    int starCount = StringUtils.countMatches(host, '*');
                    if (starCount > 0)
                    {
                        if (starCount > 1)
                        {
                            errors.addError(new LabKeyError(String.format("Redirect host name %1$s has multiple wild card characters", host)));
                        }
                        else if (!host.startsWith("*."))
                        {
                            errors.addError(new LabKeyError(String.format("Redirect host name %1$s has an invalid wild card. The pattern must start with \"*.\".", host)));
                        }
                        else if (StringUtils.countMatches(host, '.') < 2)
                        {
                            errors.addError(new LabKeyError(String.format("Redirect host name %1$s with wild card is too short. The pattern must include at least two dots.", host)));
                        }
                    }
                }
                else
                {
                    errors.addError(new LabKeyError(String.format("Redirect host name %1$s is not a valid host name", host)));
                }
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
                    <p>
                        Restrict the file types that LabKey will accept for uploads by specifying a list of all allowed
                        file extensions. Add the extensions one-by-one via the "Extension" box. Any extension that is
                        not in the list below will be rejected. Multiple extensions must be provided explicitly; for
                        example, specify ".tar.gz" to allow those files (".gz" is not sufficient). If the list is empty
                        then all file types will be allowed.
                    </p>
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
            FileUtil.clearExtensionChecker(); // Should be redundant, but going to leave this here
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

    public static class TestCase extends Assert
    {
        @Test
        public void testAllowedExtensions()
        {
            List<String> existing = FileExtension.getValues();
            Assume.assumeTrue("Initial allowed extensions list should be empty to prevent overriding existing values", existing.isEmpty());

            try
            {
                List<String> newValues = Arrays.asList(".tar.gz", ".bar");
                FileExtension.setValues(newValues, User.getAdminServiceUser());

                try
                {
                    FileUtil.checkAllowedFileName("test.tar.gz", true);
                    FileUtil.checkAllowedFileName("foo.bar", true);
                }
                catch (IOException e)
                {
                    fail("Filename should've been accepted: " + e.getMessage());
                }

                Assert.assertThrows("We dont allow 'extra' extensions", IOException.class, () -> FileUtil.checkAllowedFileName("test.foo.tar.gz", true));
                Assert.assertThrows("We dont allow partial extensions, first segment", IOException.class, () -> FileUtil.checkAllowedFileName("test.tar", true));
                Assert.assertThrows("We dont allow partial extensions, second segment", IOException.class, () -> FileUtil.checkAllowedFileName("test.gz", true));
                Assert.assertThrows("We dont allow files with no extensions", IOException.class, () -> FileUtil.checkAllowedFileName("test", true));
            }
            finally
            {
                // Verify values were restored to original state.
                FileExtension.setValues(existing, User.getAdminServiceUser());
                List<String> current = FileExtension.getValues();
                assertEquals(existing, current);
            }
        }
    }
}
