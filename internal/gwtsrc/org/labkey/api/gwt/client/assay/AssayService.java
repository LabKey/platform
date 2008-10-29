/*
 * Copyright (c) 2008 LabKey Corporation
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

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.SerializableException;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTDomain;

import java.util.Map;
import java.util.List;

/**
 * User: brittp
* Date: June 20, 2007
* Time: 2:37:12 PM
*/
public interface AssayService extends RemoteService
{
    GWTProtocol getAssayDefinition(int rowId, boolean copy) throws SerializableException;

    GWTProtocol getAssayTemplate(String providerName) throws SerializableException;

    GWTProtocol saveChanges(GWTProtocol plate, boolean replaceIfExisting) throws AssayException;

    /**
     *
     * @param orig Unchanged domain
     * @param update Edited domain
     * @return list of errors
     * @throws Exception
     */
    List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update) throws AssayException;
    
    // PropertiesEditor.LookupService
    /**
     *
     * @return list of container paths
     */
    List<String> getContainers();

    /**
     * @return list of schema names
     */
    List<String> getSchemas(String containerId);

    /**
     *
     * @param containerId container
     * @param schemaName name of schema for query module
     * @return map table name to pk column name
     */
    Map<String, String> getTablesForLookup(String containerId, String schemaName);
}
