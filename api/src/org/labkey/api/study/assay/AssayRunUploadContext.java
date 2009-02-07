/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.apache.struts.upload.MultipartRequestHandler;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Collection;
import java.io.File;
import java.io.IOException;

/**
 * User: brittp
* Date: Jul 11, 2007
* Time: 1:24:10 PM
*/
public interface AssayRunUploadContext
{
    ExpProtocol getProtocol();

    Map<DomainProperty, String> getRunProperties();

    Map<DomainProperty, String> getBatchProperties();

    String getComments();

    String getName();

    User getUser();

    Container getContainer();

    HttpServletRequest getRequest();

    ActionURL getActionURL();

    Map<String, File> getUploadedData() throws IOException, ExperimentException;

    AssayProvider getProvider();

    MultipartRequestHandler getMultipartRequestHandler();

    Map<DomainProperty, Object> getDefaultValues(Domain domain, String scope) throws ExperimentException;

    Map<DomainProperty, Object> getDefaultValues(Domain domain) throws ExperimentException;

    void saveDefaultValues(Map<DomainProperty, String> values, String scope) throws ExperimentException;

    void saveDefaultValues(Map<DomainProperty, String> values) throws ExperimentException;

    void clearDefaultValues(Domain domain) throws ExperimentException;

}
