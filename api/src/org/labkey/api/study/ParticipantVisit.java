/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.study;

import org.labkey.api.exp.api.ExpMaterial;

import java.util.Date;

/**
 * User: brittp
* Date: Oct 2, 2007
* Time: 3:44:03 PM
*/
public interface ParticipantVisit
{
    public static final String ASSAY_RUN_MATERIAL_NAMESPACE = "AssayRunMaterial";
    
    String getParticipantID();

    Double getVisitID();

    String getSpecimenID();

    Date getDate();

    ExpMaterial getMaterial();
}