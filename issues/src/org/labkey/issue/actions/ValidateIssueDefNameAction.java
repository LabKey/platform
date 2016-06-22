package org.labkey.issue.actions;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.query.IssuesListDefTable;
import org.springframework.validation.BindException;

/**
 * Created by klum on 5/26/2016.
 */
@RequiresPermission(ReadPermission.class)
public class ValidateIssueDefNameAction extends ApiAction<ValidateIssueDefNameAction.IssueDefForm>
{
    @Override
    public ApiResponse execute(IssueDefForm form, BindException errors) throws Exception
    {
        ApiSimpleResponse response = new ApiSimpleResponse();

        if (!StringUtils.isBlank(form.getIssueDefName()))
        {
            String message = "A new Issue Definition will be generated in this folder: " + getContainer().getPath();
            Domain domain = IssueListDef.findExistingDomain(getContainer(), getUser(), IssuesListDefTable.nameFromLabel(form.getIssueDefName()));

            if (domain != null)
            {
                message = "An existing Issue Definition was found in this folder: " + domain.getContainer().getPath() +
                        ". This existing definition will be shared with your new issue list if created.";
            }

            response.put("message", message);
        }
        return response;
    }

    public static class IssueDefForm
    {
        String _issueDefName;

        public String getIssueDefName()
        {
            return _issueDefName;
        }

        public void setIssueDefName(String issueDefName)
        {
            _issueDefName = issueDefName;
        }
    }
}
