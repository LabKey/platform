package org.labkey.xarassay;

import org.labkey.api.action.FormArrayList;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.actions.ProtocolIdForm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: phussey
 * Date: Sep 21, 2007
 * Time: 4:09:54 PM
 */
public class XarChooseAssayForm extends ProtocolIdForm
{
    private String _path;
    private ArrayList<ExpProtocol> _availableProtocols = new FormArrayList<ExpProtocol>(ExpProtocol.class);
    private Map<String, String> _links = new LinkedHashMap<String, String>();


    public XarChooseAssayForm()
    {
        super();
    }

    public String getPath()
    {
        return _path;
    }

    public void setPath(String path)
    {
        _path = path;
    }

    public ArrayList<ExpProtocol> getAvailableProtocols()
    {
        return _availableProtocols;
    }

    public void setAvailableProtocols(ArrayList<ExpProtocol> availableProtocols)
    {
        _availableProtocols = availableProtocols;
    }

    public Map<String, String> getLinks()
    {
        return _links;
    }

 
}
