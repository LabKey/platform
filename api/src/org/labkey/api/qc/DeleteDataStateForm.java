/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.qc;

public class DeleteDataStateForm
{
    private int _id;
    private boolean _all = false;
    private String _manageReturnUrl;

    public int getId() {return _id;}

    public void setId(int id) {_id = id;}

    public boolean isAll()
    {
        return _all;
    }

    public void setAll(boolean all)
    {
        _all = all;
    }

    public String getManageReturnUrl()
    {
        return _manageReturnUrl;
    }

    public void setManageReturnUrl(String manageReturnUrl)
    {
        _manageReturnUrl = manageReturnUrl;
    }
}
