package org.labkey.core.admin;

import org.labkey.api.util.HtmlString;

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
    };

    public abstract HtmlString getDescription();
}
