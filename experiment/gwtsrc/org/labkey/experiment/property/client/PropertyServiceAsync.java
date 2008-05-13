/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.property.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTDomain;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 26, 2007
 * Time: 1:36:23 PM
 */
public interface PropertyServiceAsync // extends PropertiesEditorServiceAsync
{

    void updateDomainDescriptor(GWTDomain orig, GWTDomain dd, AsyncCallback async);

    void getDomainDescriptor(String typeURI, AsyncCallback async);

    // PropertiesEditor.LookupService
    void getContainers(AsyncCallback async);

    void getSchemas(String containerId, AsyncCallback async);

    void getTablesForLookup(String containerId, String schemaName, AsyncCallback async);
}
