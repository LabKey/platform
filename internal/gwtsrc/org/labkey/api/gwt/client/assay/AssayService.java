/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import com.google.gwt.user.client.rpc.SerializableException;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTContainer;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.LookupService;

import java.util.List;

/**
 * User: brittp
* Date: June 20, 2007
* Time: 2:37:12 PM
*/
public interface AssayService extends LookupService
{
    GWTProtocol getAssayDefinition(int rowId, boolean copy) throws SerializableException;

    GWTProtocol getAssayTemplate(String providerName) throws SerializableException;

    GWTProtocol saveChanges(GWTProtocol plate, boolean replaceIfExisting) throws AssayException;

    /**
     * @param orig Unchanged domain
     * @param update Edited domain
     * @return list of errors
     */
    List<String> updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update) throws AssayException;

    /** Get the list of containers with studies that are readable by the current user */
    List<GWTContainer> getStudyContainers();
}
