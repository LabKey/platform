package org.labkey.api.study;

import java.util.List;

/**
 * User: cnathe
 * Date: 12/14/13
 */
public interface AssaySpecimenConfig
{
    int getRowId();
    String getAssayName();
    String getDescription();
    String getSource();
    Integer getLocationId();
    Integer getPrimaryTypeId();
    Integer getDerivativeTypeId();
    String getTubeType();
}
