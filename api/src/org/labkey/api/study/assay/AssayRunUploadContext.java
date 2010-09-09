/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * User: brittp
* Date: Jul 11, 2007
* Time: 1:24:10 PM
*/
public interface AssayRunUploadContext
{
    @NotNull
    ExpProtocol getProtocol();

    Map<DomainProperty, String> getRunProperties();

    Map<DomainProperty, String> getBatchProperties();

    String getComments();

    String getName();

    User getUser();

    Container getContainer();

    HttpServletRequest getRequest();

    ActionURL getActionURL();

    /** @return Map of original file name to file on disk */
    @NotNull
    Map<String, File> getUploadedData() throws IOException, ExperimentException;

    AssayProvider getProvider();

    Map<DomainProperty, Object> getDefaultValues(Domain domain, String scope) throws ExperimentException;

    Map<DomainProperty, Object> getDefaultValues(Domain domain) throws ExperimentException;

    void saveDefaultValues(Map<DomainProperty, String> values, String scope) throws ExperimentException;

    void saveDefaultBatchValues() throws ExperimentException;

    void saveDefaultRunValues() throws ExperimentException;

    void clearDefaultValues(Domain domain) throws ExperimentException;

    String getTargetStudy();

    TransformResult getTransformResult();
}
