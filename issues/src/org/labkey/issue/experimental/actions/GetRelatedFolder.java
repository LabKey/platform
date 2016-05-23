package org.labkey.issue.experimental.actions;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.springframework.validation.BindException;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by klum on 5/20/2016.
 */
@RequiresPermission(ReadPermission.class)
public class GetRelatedFolder extends ApiAction<IssuesController.IssuesForm>
{
    @Override
    public Object execute(IssuesController.IssuesForm form, BindException errors) throws Exception
    {
        IssueManager.getIssueListDefs(null);
        ApiSimpleResponse response = new ApiSimpleResponse();
        Collection<Map<String, String>> containers = new LinkedList<>();

        String issueDefName = form.getIssueDefName();
        if (issueDefName != null)
        {
            for (IssueListDef def : IssueManager.getIssueListDefs(null))
            {
                if (!def.getName().equals(issueDefName) || !def.getContainerId().equals(getContainer().getId()))
                {
                    Container c = ContainerManager.getForId(def.getContainerId());
                    if (c.hasPermission(getUser(), InsertPermission.class))
                    {
                        containers.add(PageFlowUtil.map(
                                "containerId", c.getId(),
                                "containerPath", c.getPath()));
                    }
                }
            }
        }
        response.put("containers", containers);

        return response;
    }
}
