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
package org.labkey.issues.client;

import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupService;

import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/12/2016.
 */
public interface IssueService extends LookupService
{
    public GWTIssueDefinition getIssueDefinition(String defName);
    public GWTDomain getDomainDescriptor(String typeURI);

    public List<String> updateIssueDefinition(GWTIssueDefinition def, GWTDomain orig, GWTDomain dd);

    public List<Map<String, String>> getProjectGroups();
    public List<Map<String, String>> getUsersForGroup(Integer groupId);
    public List<Map<String, String>> getFolderMoveContainers();
}
