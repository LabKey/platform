package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Apr 23, 2007
 */
public interface PlateTypeHandler
{
    public String getAssayType();

    public List<String> getTemplateTypes();

    /**
     * createPlate will be given a null value for templateTypeName when it is creating a new template which is a 
     * default for that assay type.
     */
    public PlateTemplate createPlate(String templateTypeName, Container container) throws SQLException;

    public WellGroup.Type[] getWellGroupTypes();
}
