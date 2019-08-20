/*
 * Copyright (c) 2010-2015 LabKey Corporation
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

package gwt.client.org.labkey.plate.designer.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import gwt.client.org.labkey.plate.designer.client.model.GWTPlate;

/**
 * User: brittp
 * Date: Jan 31, 2007
 * Time: 2:37:25 PM
 */
public interface PlateDataServiceAsync
{
    void getTemplateDefinition(String templateName, int plateId, String assayTypeName, String templateTypeName, int rowCount, int columnCount, AsyncCallback<GWTPlate> async);

    void saveChanges(GWTPlate plate, boolean replaceIfExisting, AsyncCallback async);
}
