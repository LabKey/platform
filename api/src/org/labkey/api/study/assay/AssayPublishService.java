package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.User;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.ActionURL;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Nov 6, 2006
 * Time: 11:00:12 AM
 */
public class AssayPublishService
{
    private static AssayPublishService.Service _serviceImpl;

    public interface Service
    {
        ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                      Map<String,Object>[] dataMaps, Map<String, PropertyType> propertyTypes, List<String> errors)
                throws SQLException, IOException, ServletException;

        ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                       Map<String, Object>[] dataMaps, Map<String, PropertyType> propertyTypes, String keyPropertyName, List<String> errors)
                throws SQLException, IOException, ServletException;

        ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                       Map<String, Object>[] dataMaps, List<PropertyDescriptor> propertyTypes, List<String> errors)
                throws SQLException, IOException, ServletException;

        ActionURL publishAssayData(User user, Container sourceContainer, Container targetContainer, String assayName, ExpProtocol protocol,
                                       Map<String, Object>[] dataMaps, List<PropertyDescriptor> propertyTypes, String keyPropertyName, List<String> errors)
                throws SQLException, IOException, ServletException;

        Map<Container, String> getValidPublishTargets(User user, int permission);

        ActionURL getPublishHistory(Container container, ExpProtocol protocol);

        TimepointType getTimepointType(Container container);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }
}
