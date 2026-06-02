/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.TransactionAuditProvider;

import java.util.Map;

public class TransactionViewForm extends ViewForm
{
    private String _editMethod;
    private String _requestSource;

    public String getRequestSource()
    {
        return _requestSource;
    }

    public void setRequestSource(String requestSource)
    {
        _requestSource = requestSource;
    }

    public String getEditMethod()
    {
        return _editMethod;
    }

    public void setEditMethod(String editMethod)
    {
        _editMethod = editMethod;
    }

    public void addTransactionAuditDetails(@NotNull Map<TransactionAuditProvider.TransactionDetail, Object> transactionAuditDetails)
    {
        if (getRequestSource() != null)
            transactionAuditDetails.put(TransactionAuditProvider.TransactionDetail.RequestSource, getRequestSource());
        if (getEditMethod() != null)
            transactionAuditDetails.put(TransactionAuditProvider.TransactionDetail.EditMethod, getEditMethod());
    }
}
