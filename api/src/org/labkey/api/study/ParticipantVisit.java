/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpMaterial;

import java.util.Date;

/**
 * Captures a single subject/timepoint combination, which could be associated with information in a study
 * dataset or specimen.
 * User: brittp
 * Date: Oct 2, 2007
 */
public interface ParticipantVisit
{
    public static final String ASSAY_RUN_MATERIAL_NAMESPACE = "AssayRunMaterial";

    Container getStudyContainer();

    String getParticipantID();

    Double getVisitID();

    String getSpecimenID();

    Integer getCohortID();

    Date getDate();

    ExpMaterial getMaterial();
}
