package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.DOM;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.HtmlString;

import java.util.Collection;

public interface PasswordValidator
{
    Renderable PREVIOUS_PASSWORD_BULLET = DOM.LI("Must not match any of the user's 10 previously used passwords.");

    @NotNull HtmlString getFullRuleHtml();
    @NotNull HtmlString getSummaryRuleHtml();
    boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages);
    boolean isPreviousPasswordForbidden();
    boolean isDeprecated();
    boolean shouldShowPasswordGuidance();
}
