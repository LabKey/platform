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
package org.labkey.api.gwt.client.ui.domain;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.List;

/*
* User: adam
* Date: Dec 24, 2009
* Time: 7:26:51 AM
*/
public class ImportStatus implements IsSerializable
{
    private List<String> _messages;
    private boolean _isComplete;
    private String _jobId;
    private int _totalRows;
    private int _currentRow;

    public List<String> getMessages()
    {
        return _messages;
    }

    public void setMessages(List<String> messages)
    {
        _messages = messages;
    }

    public boolean isComplete()
    {
        return _isComplete;
    }

    public void setComplete(boolean complete)
    {
        _isComplete = complete;
    }

    public String getJobId()
    {
        return _jobId;
    }

    public void setJobId(String jobId)
    {
        _jobId = jobId;
    }

    public int getTotalRows()
    {
        return _totalRows;
    }

    public void setTotalRows(int totalRows)
    {
        _totalRows = totalRows;
    }

    public int getCurrentRow()
    {
        return _currentRow;
    }

    public void setCurrentRow(int currentRow)
    {
        _currentRow = currentRow;
    }
}
