/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.reports.permissions.ShareReportPermission;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.SampleWorkflowJobPermission;
import org.labkey.api.study.Dataset;

/*
 * User: Dave
 * Date: Apr 27, 2009
 */
public class AuthorRole extends AbstractRole
{
    public AuthorRole()
    {
        super("Author", "Authors may read and add information in some cases, but may update and delete only information they added. Supported for only " +
                        "Message Boards. See the online documentation for details.",
            ReadPermission.class,
            ReadSomePermission.class,
            InsertPermission.class,
            ShareReportPermission.class
        );
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return super.isApplicable(policy,resource) || resource instanceof PipeRoot || resource instanceof Dataset;
    }
}
