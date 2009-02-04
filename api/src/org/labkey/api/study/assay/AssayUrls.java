/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.action.UrlProvider;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.springframework.web.servlet.mvc.Controller;

/**
 * User: jeckels
 * Date: Jan 2, 2009
 */
public interface AssayUrls extends UrlProvider
{
    ActionURL getProtocolURL(Container container, ExpProtocol protocol, Class<? extends Controller> action);
    
    ActionURL getCopyToStudyConfirmURL(Container container, ExpProtocol protocol);
    ActionURL getDesignerURL(Container container, String providerName);

    /**
     * @param container container in which the assay definition should live
     * @param protocol if null, start a new design from scratch. If not null, either the design to edit or the design to copy
     * @param copy if true, create a copy of the protocol that's passed in and start editing it
     */
    ActionURL getDesignerURL(Container container, ExpProtocol protocol, boolean copy);
    ActionURL getAssayListURL(Container container);
    ActionURL getAssayBatchesURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter);
    ActionURL getAssayRunsURL(Container container, ExpProtocol protocol);
    ActionURL getAssayRunsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter, int... batchIds);
    ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, int... runIds);
    ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter, int... runIds);

    ActionURL getChooseCopyDestinationURL(ExpProtocol protocol, Container container);

    ActionURL getDeleteDesignURL(Container container, ExpProtocol protocol);
}