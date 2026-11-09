/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
