package org.labkey.api.security;

import org.labkey.api.jsp.JspBase;

abstract public class AddUsersPage extends JspBase
    {
    private String _message;
    public String getMessage()
        {
        return _message;
        }
    public void setMessage(String message)
        {
        _message = message;
        }
    }
