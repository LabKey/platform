/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.api.gwt.client.ui.domain;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupService;
import org.labkey.api.gwt.client.ui.LookupServiceAsync;

import java.util.List;
import java.util.Map;

public interface DomainImporterServiceAsync extends LookupServiceAsync
{
    void inferenceColumns(AsyncCallback<List<InferencedColumn>> async);

    void updateDomainDescriptor(GWTDomain orig, GWTDomain dd, AsyncCallback<List<String>> async);

    void getDomainDescriptor(String typeURI, AsyncCallback<GWTDomain> async);

    void importData(GWTDomain domain, Map<String, String> mappedColumnNames, AsyncCallback<ImportStatus> async);

    void getStatus(String jobId, AsyncCallback<ImportStatus> async);

    void cancelImport(String jobId, AsyncCallback<String> async);
}
