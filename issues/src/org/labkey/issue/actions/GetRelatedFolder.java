/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.issue.actions;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SortHelpers;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueManager;
import org.springframework.validation.BindException;

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
        ApiSimpleResponse response = new ApiSimpleResponse();
        LinkedList<Map<String, String>> containers = new LinkedList<>();

        // exclude current container
        IssueManager.getIssueListDefs(null).stream().forEach(def -> {
            Container c = ContainerManager.getForId(def.getContainerId());
            if (c.hasPermission(getUser(), InsertPermission.class))
            {
                containers.add(PageFlowUtil.map(
                        "containerId", c.getId(),
                        "containerPath", c.getPath(),
                        "issueDefName", def.getName(),
                        "displayName", String.format("%s (%s)", c.getPath(), def.getName())));
            }
        });
        containers.sort((o1, o2) -> SortHelpers.compareNatural(
                o1.get("containerPath"),
                o2.get("containerPath")));

        response.put("containers", containers);
        return response;
    }
}
