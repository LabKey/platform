package org.labkey.query.reports;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.RedirectReport;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.DateUtil;

import java.util.Date;
import java.util.List;

/**
 * User: kevink
 * Date: 6/21/12
 */
public abstract class BaseRedirectReport extends RedirectReport
{
    public static final String MODIFIED = "modified";

    protected Container getContainer()
    {
        return ContainerManager.getForId(getDescriptor().getContainerId());
    }

    public void setModified(Date modified)
    {
        getDescriptor().setProperty(MODIFIED, DateUtil.formatDate(modified));
    }

    public void setDescription(String description)
    {
        getDescriptor().setReportDescription(description);
    }

    public String getDescription()
    {
        return getDescriptor().getReportDescription();
    }

    public void setCategory(Integer id)
    {
        if (id != null)
        {
            getDescriptor().setCategory(ViewCategoryManager.getInstance().getCategory(id));
        }
        else
        {
            getDescriptor().setCategory(null);
        }
    }

    public ViewCategory getCategory()
    {
        return getDescriptor().getCategory();
    }

    public void setOwner(Integer owner)
    {
        getDescriptor().setOwner(owner);
    }

    public void getOwner()
    {
        getDescriptor().getOwner();
    }

    public boolean canEdit(User user, Container container, List<ValidationError> errors)
    {
        super.canEdit(user, container, errors);

        if (errors.isEmpty() && getDescriptor().getOwner() != null)
        {
            if (!container.hasPermission(user, InsertPermission.class))
                errors.add(new SimpleValidationError("You must be in the Author role to update a private attachment report."));
        }
        return errors.isEmpty();
    }

    public boolean canDelete(User user, Container container, List<ValidationError> errors)
    {
        super.canDelete(user, container, errors);

        if (errors.isEmpty())
        {
            if (isPrivate())
            {
                if (!container.hasPermission(user, InsertPermission.class))
                    errors.add(new SimpleValidationError("You must be in the Author role to delete a private attachment report."));
            }
        }
        return errors.isEmpty();
    }
}
