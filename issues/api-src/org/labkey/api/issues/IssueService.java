package org.labkey.api.issues;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.Errors;

import java.util.List;

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
}
