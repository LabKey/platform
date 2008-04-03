package org.labkey.api.query;

import org.labkey.api.util.PageFlowUtil;
import org.apache.commons.lang.ObjectUtils;

import java.util.Map;
import java.util.Collection;
import java.util.LinkedHashMap;

public class QueryPicker
{
    String _label;
    String _paramName;
    Object _currentValue;
    Map<? extends Object, String> _choices;
    boolean _autoRefresh;
    String _descriptionHTML;


    public QueryPicker(String label, String paramName, Object currentValue, Map<? extends Object, String> choices)
    {
        _paramName = paramName;
        _currentValue = currentValue;
        _label = label;
        _choices = choices;
        _autoRefresh = true;
    }

    public <T> QueryPicker(String label, String paramName, T currentValue, Collection<T> choiceSet)
    {
        this(label, paramName, currentValue, (Map) null);
        Map choices = new LinkedHashMap();
        for (T choice : choiceSet)
        {
            if (choice == null)
            {
                choices.put(null, "");
            }
            else
            {
                choices.put(choice, choice.toString());
            }
        }
        _choices = choices;
    }

    protected String h(Object o)
    {
        return PageFlowUtil.filter(o);
    }

    public String getParamName()
    {
        return _paramName;
    }

    public Object getCurrentValue()
    {
        return _currentValue;
    }

    public Map<? extends Object, String> getChoices()
    {
        return _choices;
    }

    public String toString()
    {
        int count = _choices.size();
        boolean currentMissing = false;
        if (_currentValue != null && !_choices.containsKey(_currentValue))
        {
            count ++;
            currentMissing = true;
        }
        if (count <= 1)
            return "";
        StringBuilder ret = new StringBuilder();
        ret.append(h(_label));
        ret.append("<select name=\"" + h(_paramName) + "\"");
        appendOnChangeHandler(ret);
        ret.append(">");
        if (currentMissing)
        {
            ret.append("<option selected value=\"" + h(_currentValue) + "\">");
            ret.append("&lt;" + h(_currentValue) + ">");
            ret.append("</option>");
        }
        for (Map.Entry<? extends Object, String> entry : _choices.entrySet())
        {
            ret.append("\n<option");
            if (ObjectUtils.equals(entry.getKey(), _currentValue))
            {
                ret.append(" selected");
            }
            ret.append(" value=\"");
            ret.append(h(entry.getKey()));
            ret.append("\">");
            ret.append(h(entry.getValue()));
            ret.append("</option>");
        }
        ret.append("</select>");
        if (_descriptionHTML != null)
        {
            ret.append(_descriptionHTML);
        }
        return ret.toString();
    }

    protected void appendOnChangeHandler(StringBuilder ret)
    {
        if (this._autoRefresh)
        {
            ret.append(" onchange=\"this.form.submit()\"");
        }
    }

    public void setDescriptionHTML(String html)
    {
        _descriptionHTML = html;
    }

    public void setAutoRefresh(boolean autoRefresh)
    {
        _autoRefresh = autoRefresh;
    }

    public boolean isAutoRefresh()
    {
        return _autoRefresh;
    }
    
    public void setLabel(String label)
    {
        _label = label;
    }
}
