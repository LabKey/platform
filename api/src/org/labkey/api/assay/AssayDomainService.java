/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.assay;

import org.labkey.api.gwt.client.assay.AssayException;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.query.ValidationException;

public interface AssayDomainService
{
    GWTProtocol getAssayDefinition(int rowId, boolean copy);

    GWTProtocol getAssayTemplate(String providerName);

    GWTProtocol saveChanges(GWTProtocol plate, boolean replaceIfExisting) throws ValidationException;
}