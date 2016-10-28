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
        response.put("success", true);

        if (!StringUtils.isBlank(form.getIssueDefName()) && !StringUtils.isBlank(form.getIssueDefKind()))
        {
            String message = "A new Issue Definition will be generated in this folder: " + getContainer().getPath();
            Domain domain = IssueListDef.findExistingDomain(getContainer(), getUser(),
                                                            IssuesListDefTable.nameFromLabel(form.getIssueDefName()), form.getIssueDefKind());

            if (domain != null)
            {
                if (domain.getDomainKind().getKindName().equalsIgnoreCase(form.getIssueDefKind()))
                {
                    message = "An existing Issue Definition was found in this folder: " + domain.getContainer().getPath() +
                    ". This existing definition will be shared with your new issue list if created.";
                }
                else
                {
                    message = "An existing Issue Tracker Definition was found in this folder: " + domain.getContainer().getPath() +
                    ". But the existing definition is a different kind of tracker. Therefore you must choose a different name.";
                    response.put("success", false);
                }
            }

            response.put("message", message);
        }
        return response;
    }

    public static class IssueDefForm
    {
        String _issueDefName;
        private String _issueDefKind;

        public String getIssueDefName()
        {
            return _issueDefName;
        }

        public void setIssueDefName(String issueDefName)
        {
            _issueDefName = issueDefName;
        }

        public String getIssueDefKind()
        {
            return _issueDefKind;
        }

        public void setIssueDefKind(String issueDefKind)
        {
            _issueDefKind = issueDefKind;
        }
    }
}
