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
