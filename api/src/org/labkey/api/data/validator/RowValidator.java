package org.labkey.api.data.validator;

public interface RowValidator
{
    // CONSIDER: pass a row map or DataIterator?
    public String validate();
}
