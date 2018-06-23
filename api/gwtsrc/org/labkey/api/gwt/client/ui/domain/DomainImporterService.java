/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.gwt.client.ui.domain;

import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.LookupService;

import java.util.List;
import java.util.Map;

/**
 * User: jgarms
 * Date: Nov 4, 2008
 */
public interface DomainImporterService extends LookupService
{
    List<InferencedColumn> inferenceColumns() throws GWTImportException;
    List<String> updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> dd);
    GWTDomain getDomainDescriptor(String typeURI);
    ImportStatus importData(GWTDomain domain, Map<String, String> mappedColumnNames) throws GWTImportException;
    ImportStatus getStatus(String jobId) throws GWTImportException;
    String cancelImport(String jobId) throws GWTImportException;
}
