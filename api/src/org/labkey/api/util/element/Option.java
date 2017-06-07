package org.labkey.api.util.element;

import org.labkey.api.util.PageFlowUtil;

public class Option
{
    private boolean _disabled;
    private String _label;
    private boolean _selected;
    private String _value;

    private Option(OptionBuilder builder)
    {
        _disabled = builder._disabled;
        _label = builder._label;
        _selected = builder._selected;
        _value = builder._value;
    }

    public boolean isDisabled()
    {
        return _disabled;
    }

    public String getLabel()
    {
        return _label;
    }

    public boolean isSelected()
    {
        return _selected;
    }

    public String getValue()
    {
        return _value;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("<option")
                .append(" value=\"").append(getValue() == null ? "" : PageFlowUtil.filter(getValue())).append("\"");

        if (getLabel() != null && !"".equals(getLabel()))
            sb.append(" label=\"").append(PageFlowUtil.filter(getLabel())).append("\"");

        if (isDisabled())
            sb.append(" disabled");

        if (isSelected())
            sb.append(" selected");

        sb.append("></option>");

        return sb.toString();
    }

    public static class OptionBuilder
    {
        private boolean _disabled;
        private String _label;
        private boolean _selected;
        private String _value;

        public OptionBuilder()
        {
        }

        public OptionBuilder disabled(boolean disabled)
        {
            _disabled = disabled;
            return this;
        }

        public OptionBuilder label(String label)
        {
            _label = label;
            return this;
        }

        public OptionBuilder selected(boolean selected)
        {
            _selected = selected;
            return this;
        }

        public OptionBuilder value(String value)
        {
            _value = value;
            return this;
        }

        public Option build()
        {
            return new Option(this);
        }

        @Override
        public String toString()
        {
            return build().toString();
        }
    }
}
