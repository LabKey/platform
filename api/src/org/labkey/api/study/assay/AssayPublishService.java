/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
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
                                       Map<String, Object>[] dataMaps, List<PropertyDescriptor> propertyTypes, String keyPropertyName, List<String> errors)
                throws SQLException, IOException, ServletException;

        /**
         * Container -> Study label
         */
        Map<Container, String> getValidPublishTargets(User user, int permission);

        ActionURL getPublishHistory(Container container, ExpProtocol protocol);
        ActionURL getPublishHistory(Container container, ExpProtocol protocol, ContainerFilter containerFilter);

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
