/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.mcp;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

/**
 * Implemented by enums that expose a fixed set of container-scoped, argument-free navigation targets
 * (e.g. "manage study", "manage cohorts") to a single dispatcher {@code @Tool} method, instead of
 * registering one tool per target. See {@code McpService.McpImpl} for the surrounding MCP tool conventions.
 *
 * <p>KNOWN GAP: {@code getUrl} returns a URL without checking whether the current user can actually access
 * the target action -- the surrounding {@code @Tool} method only enforces its own (usually coarse,
 * {@code ReadPermission}-level) requirement, not the target action's real {@code @RequiresPermission}/
 * {@code @RequiresAllOf}/{@code @RequiresAnyOf}/{@code @RequiresSiteAdmin}/{@code @RequiresLogin}/
 * {@code @RequiresNoPermission}. This applies equally to entity-resolving tools built the same way (e.g.
 * a "get import URL for this dataset" tool gated only by {@code ReadPermission} when the target action
 * actually requires {@code InsertPermission}). A returned URL may 403 when the caller clicks it.
 *
 * <p>Future direction: check the target action's annotations via the same primitives
 * {@code PermissionCheckableAction._checkActionPermissions} uses ({@code SecurityManager.hasAllPermissions}/
 * {@code hasAnyPermissions}) rather than re-deriving permission logic. Prefer fail-open: always return the
 * URL and attach an accessibility hint, rather than withholding it or throwing. A static check can't see
 * contextual roles (only available via a live action instance's {@code getContextualRoles()}), and
 * contextual roles only ever grant access -- so a static check can produce false negatives but never false
 * positives; withholding the URL on a false negative would be worse than the current gap. When implemented
 * for an enum built through existing {@code UrlProvider} methods (as opposed to built directly from the
 * action class), carry the action class as separate metadata for the permission check only -- don't change
 * how the URL itself is built, or an overridden {@code UrlProviderService} registration would be bypassed.</p>
 */
public interface NavigablePage
{
    @NotNull String description();

    ActionURL getUrl(@NotNull Container container);
}