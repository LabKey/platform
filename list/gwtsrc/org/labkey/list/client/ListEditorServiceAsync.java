/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package org.labkey.list.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupServiceAsync;

import java.util.List;

/**
 * User: matthewb
 * Date: Apr 26, 2007
 * Time: 1:36:23 PM
 */
public interface ListEditorServiceAsync extends LookupServiceAsync
{
    void getList(int id, AsyncCallback<GWTList> async);
    void createList(GWTList list, AsyncCallback<GWTList> async);
    void deleteList(GWTList list, AsyncCallback<Void> async);
    void getListNames(AsyncCallback<List<String>> async); // names in use already
    void updateListDefinition(GWTList list, GWTDomain orig, GWTDomain dd, AsyncCallback<List<String>> async);
    void getDomainDescriptor(GWTList list, AsyncCallback<GWTDomain> async);
}
