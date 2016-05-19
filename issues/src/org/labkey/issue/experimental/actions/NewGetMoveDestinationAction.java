package org.labkey.issue.experimental.actions;

import org.hamcrest.core.Is;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.springframework.validation.BindException;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/17/2016.
 */

@RequiresPermission(AdminPermission.class)
public class NewGetMoveDestinationAction extends ApiAction<IssuesController.IssuesForm>
{
    @Override
    public ApiResponse execute(IssuesController.IssuesForm form, BindException errors) throws Exception
    {
        ApiSimpleResponse response = new ApiSimpleResponse();
        Collection<Map<String, String>> containers = new LinkedList<>();

        String issueDefName = form.getIssueDefName();
        if (issueDefName != null)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("name"), issueDefName);
            List<IssueListDef> defs = new TableSelector(IssuesSchema.getInstance().getTableInfoIssueListDef(), filter, null).getArrayList(IssueListDef.class);
            for (IssueListDef def : defs)
            {
                // exclude current container
                if (!def.getContainerId().equals(getContainer().getId()))
                {
                    containers.add(PageFlowUtil.map(
                            "containerId", def.getContainerId(),
                            "containerPath", def.getContainerPath()));
                }
            }
        }
        response.put("containers", containers);

        return response;
    }
}
