/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.assay.nab.view;

import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AbstractPlateBasedAssayProvider;

/**
 * Created by klum on 2/17/14.
 */
public class RunDetailOptions
{
    public static final String DATA_IDENTIFIER_PARAM = "dataIdentifier";

    /**
     * Options for identifying samples in the run details report
     */
    public enum DataIdentifier
    {
        Specimen("Specimen ID", true, new String[]{AbstractAssayProvider.SPECIMENID_PROPERTY_NAME}),
        ParticipantVisit("Participant ID / Visit", true, new String[]{AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME, AbstractAssayProvider.VISITID_PROPERTY_NAME}),
        ParticipantDate("Participant ID / Date", true, new String[]{AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME, AbstractAssayProvider.DATE_PROPERTY_NAME}),
        SpecimenParticipantVisit("Specimen ID / Participant ID / Visit", true, new String[]{AbstractAssayProvider.SPECIMENID_PROPERTY_NAME, AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME, AbstractAssayProvider.VISITID_PROPERTY_NAME}),
        LongFormat("Long Format", false, new String[0]),
        DefaultFormat("Default Format", false, new String[0]);

        private String _caption;
        private boolean _selectable;
        private String[] _requiredProperties;

        DataIdentifier(String caption, boolean selectable, String[] requiredProperties)
        {
            _caption = caption;
            _selectable = selectable;
            _requiredProperties = requiredProperties;
        }

        public String getCaption()
        {
            return _caption;
        }

        public boolean isSelectable()
        {
            return _selectable;
        }

        public String[] getRequiredProperties()
        {
            return _requiredProperties;
        }
    }
}
