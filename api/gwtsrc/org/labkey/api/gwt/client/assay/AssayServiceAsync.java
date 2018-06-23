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

package org.labkey.api.gwt.client.assay;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTContainer;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupServiceAsync;

import java.util.List;

/**
 * User: brittp
 * Date: June 20, 2007
 * Time: 2:37:25 PM
 */
public interface AssayServiceAsync extends LookupServiceAsync
{
    void getAssayDefinition(int rowId, boolean copy, AsyncCallback<GWTProtocol> async);

    void saveChanges(GWTProtocol plate, boolean replaceIfExisting, AsyncCallback<GWTProtocol> async);

    void updateDomainDescriptor(GWTDomain orig, GWTDomain update, AsyncCallback<List<String>> async);

    void getAssayTemplate(String providerName, AsyncCallback<GWTProtocol> asyncCallback);

    void getStudyContainers(AsyncCallback<List<GWTContainer>> asyncCallback);
}
