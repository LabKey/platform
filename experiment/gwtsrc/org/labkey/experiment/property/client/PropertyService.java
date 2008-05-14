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

import com.google.gwt.user.client.rpc.RemoteService;
import org.labkey.api.gwt.client.model.GWTDomain;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 26, 2007
 * Time: 1:34:56 PM
 */
public interface PropertyService extends RemoteService //, PropertiesEditorService
{
    /** @gwt.typeArgs <java.lang.String> */
    public List updateDomainDescriptor(GWTDomain orig, GWTDomain dd);
    public GWTDomain getDomainDescriptor(String typeURI);

    // PropertiesEditor.LookupService
    /** @gwt.typeArgs <java.lang.String> */
    public List getContainers();
    /** @gwt.typeArgs <java.lang.String> */
    public List getSchemas(String containerId);
    /** @gwt.typeArgs <java.lang.String, java.lang.String> */
    public Map getTablesForLookup(String containerId, String schemaName);
}
