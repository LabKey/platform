package org.labkey.query.controllers;

import org.labkey.api.query.QueryForm;

public class DesignServiceForm extends QueryForm
{
    public enum Action
    {
        load,
        save,
        tableInfo,
        checkSyntax,
    }

    Action _action;

    public Action getDesignAction()
    {
        return _action;
    }

    public void setAction(String action)
    {
        _action = Action.valueOf(action);
    }
}
