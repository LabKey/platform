/*
 * Copyright (c) 2011-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gwt.client.org.labkey.assay.designer.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.domain.DomainImporterServiceAsync;
import org.labkey.api.gwt.client.ui.domain.InferencedColumn;

import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Dec 23, 2010
 * Time: 1:41:36 PM
 */
public interface AssayImporterServiceAsync extends DomainImporterServiceAsync
{
    void getInferenceColumns(String path, String file, AsyncCallback<List<InferencedColumn>> async);
    void validateColumns(List<InferencedColumn> columns, String path, String file, AsyncCallback<Boolean> async);

    void createProtocol(String providerName, String assayName, String containerId, AsyncCallback<GWTProtocol> async);
    void getDomainImportURI(GWTProtocol protocol, AsyncCallback<String> async);
    void getImportURL(GWTProtocol protocol, String directoryPath, String file, AsyncCallback<String> async);
    void getDesignerURL(GWTProtocol protocol, String directoryPath, String file, AsyncCallback<String> async);
    void getBaseColumns(String providerName, AsyncCallback<List<GWTPropertyDescriptor>> async);

    void getAssayLocations(AsyncCallback<List<Map<String, String>>> async);
}
