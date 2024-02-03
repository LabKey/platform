/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.HtmlString;

import java.util.Collection;

public enum PasswordRule
{
    Good(new GoodPasswordValidator()),
    Strong(new StrongPasswordValidator());

    private final PasswordValidator _validator;

    PasswordRule(PasswordValidator validator)
    {
        _validator = validator;
    }

    /**
     * Returns HTML that describes the password rules in detail. This is presented to administrators when they
     * configure or review the password rules, so it is written in third person (e.g., "the user's email address").
     * @return HtmlString providing a detailed description of the rules
     */
    @NotNull
    public HtmlString getFullRuleHtml()
    {
        return _validator.getFullRuleHtml();
    }

    /**
     * Returns HTML that describes a summary of the password rules. This is presented to individual users when they
     * pick or change a password, so it is written in second person (e.g., "your email address").
     * @return HtmlString providing a summary of the rules
     */
    @NotNull
    public HtmlString getSummaryRuleHtml()
    {
        return _validator.getSummaryRuleHtml();
    }

    /**
     * We check the password rules at each login and when storing in the database. The storing rules may be a superset
     * of the login rules.
     */
    public boolean isValidForLogin(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
    {
        assert password.equals(password.trim()) : "Caller should have trimmed password";
        return _validator.isValidForLogin(password, user, messages);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isValidToStore(String password1, String password2, User user, boolean changeOperation, @NotNull Collection<String> messages)
    {
        if (StringUtils.isBlank(password1))
        {
            messages.add("You must enter a " + (changeOperation ? "new " : "") + "password.");
            return false;
        }

        if (!password1.equals(password2))
        {
            if (StringUtils.isBlank(password2))
            {
                messages.add("You must confirm your password.");
            }
            else
            {
                messages.add("Your password entries didn't match.");
            }

            return false;
        }

        if (_validator.isPreviousPasswordForbidden() && SecurityManager.matchesPreviousPassword(password1, user))
        {
            messages.add("Your password must not match a recently used password.");
            return false;
        }

        return isValidForLogin(password1, user, messages);
    }

    public boolean isDeprecated()
    {
        return _validator.isDeprecated();
    }

    public boolean shouldShowPasswordGuidance()
    {
        return _validator.shouldShowPasswordGuidance();
    }
}
