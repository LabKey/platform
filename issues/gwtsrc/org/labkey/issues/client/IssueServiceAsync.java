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

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupServiceAsync;

import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/12/2016.
 */
public interface IssueServiceAsync extends LookupServiceAsync
{
    void getIssueDefinition(String defName, AsyncCallback<GWTIssueDefinition> async);
    void getDomainDescriptor(String typeURI, AsyncCallback<GWTDomain> async);

    void updateIssueDefinition(GWTIssueDefinition def, GWTDomain orig, GWTDomain dd, AsyncCallback<List<String>> async);

    void getProjectGroups(AsyncCallback<List<Map<String, String>>> async);
    void getUsersForGroup(Integer groupId, AsyncCallback<List<Map<String, String>>> async);
    void getFolderMoveContainers(AsyncCallback<List<Map<String, String>>> async);
}
