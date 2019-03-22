/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.springframework.web.servlet.mvc.Controller;

import java.io.File;

/**
 * User: jeckels
 * Date: Jan 2, 2009
 */
public interface AssayUrls extends UrlProvider
{
    ActionURL getProtocolURL(Container container, ExpProtocol protocol, Class<? extends Controller> action);

    ActionURL getCopyToStudyURL(Container container, ExpProtocol protocol);
    ActionURL getCopyToStudyConfirmURL(Container container, ExpProtocol protocol);
    @Nullable
    ActionURL getDesignerURL(Container container, String providerName, @Nullable ActionURL returnURL);

    /**
     * Returns the URL for the assay designer
     * @param container container in which the assay definition should live
     * @param protocol if null, start a new design from scratch. If not null, either the design to edit or the design to copy
     * @param copy if true, create a copy of the protocol that's passed in and start editing it
     * @param returnUrl a return URL to use, or null to use default
     * @return The assay designer URL with the proper query string arguments or null if the assay provider doesn't support designing.
     */
    @Nullable
    ActionURL getDesignerURL(Container container, ExpProtocol protocol, boolean copy, ActionURL returnUrl);
    ActionURL getAssayListURL(Container container);
    ActionURL getAssayBatchesURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter);
    ActionURL getAssayRunsURL(Container container, ExpProtocol protocol);
    ActionURL getAssayRunsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter, int... batchIds);
    ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, int... runIds);
    ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter, int... runIds);

    ActionURL getShowUploadJobsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter);

    ActionURL getChooseCopyDestinationURL(ExpProtocol protocol, Container container);

    ActionURL getDeleteDesignURL(ExpProtocol protocol);

    /**
     * Returns the URL for the assay import data wizard for an existing assay definition.
     * path and files may be null, in which case it is assumed that the POST will include data object RowIds
     * @param protocol the assay to import into
     */
    ActionURL getImportURL(Container container, ExpProtocol protocol, String path, File[] files);

    /**
     * Returns the URL for the assay import data wizard for a new assay definition of the type.
     * specified by the providerName. Both path and files must be non-null.
     * 
     * @param container container in which the assay definition should live.
     * @param providerName the type of assay to create.
     * @param path the pipeline root relative path for this container.
     * @param files the files to import into the assay definition once it has been created.
     */
    ActionURL getImportURL(Container container, String providerName, String path, File[] files);
}
