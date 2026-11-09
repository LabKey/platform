/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.api.issues;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.Errors;

import java.util.List;
import java.util.Map;

public interface IssueService
{
    static IssueService get()
    {
        return ServiceRegistry.get().getService(IssueService.class);
    }

    static void setInstance(IssueService impl)
    {
        ServiceRegistry.get().registerService(IssueService.class, impl);
    }

    @Nullable
    Issue saveIssue(ViewContext context, Issue issue, Issue.action action, List<AttachmentFile> attachments, Errors errors);
    @Nullable
    Issue saveIssue(ViewContext context, Issue issue, Issue.action action, Errors errors);

    void validateIssue(Container container, User user, Issue issue, Issue.action action, Errors errors);

    @Nullable
    Issue getIssue(Container container, User user, Integer issueId);

    /**
     * Returns an existing issue merging in properties that represent updates that will
     * be made to the existing issue. The returned issue can then be updated using any of the save methods
     * in this interface.
     * @param issueId - The id of the issue to fetch
     * @param updates - The map which represents the changes to the existing issue that should be applied.
     */
    @Nullable
    Issue getIssueForUpdate(Container container, User user, Integer issueId, Map<String, Object> updates);

    // Returns the ID of the requested issue list definition
    Integer getIssueDefinitionId(Container container, String name);
}
