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
