package org.labkey.api.util.element;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.PageFlowUtil;

import java.text.Format;

// TODO: Need handling for checkbox, file, and radio types
public class Input
{
    public enum Layout
    {
        HORIZONTAL,
        INLINE
    }

    private final boolean _checked;
    private final String _className;
    private final boolean _disabled;
    private final Format _format;
    private final boolean _formGroup;
    private final String _id;
    private final String _label;
    private final String _labelClassName;
    private final Layout _layout;
    private final String _message;
    private final String _name;
    private final String _onChange;
    private final String _onKeyUp;
    private final String _placeholder;
    private final boolean _required;
    private final Integer _size;
    private final boolean _showLabel;
    private final String _type;
    private final Object _value;

    protected Input(InputBuilder builder)
    {
        _checked = builder._checked;
        _className = builder._className;
        _disabled = builder._disabled;
        _format = builder._format;
        _formGroup = builder._formGroup == null ? false : builder._formGroup;
        _id = builder._id;
        _label = builder._label;
        _labelClassName = builder._labelClassName;
        _layout = builder._layout;
        _message = builder._message;
        _name = builder._name;
        _onChange = builder._onChange;
        _onKeyUp = builder._onKeyUp;
        _placeholder = builder._placeholder;
        _required = builder._required;
        _type = builder._type;
        _size = builder._size;
        _showLabel = builder._showLabel == null ? builder._label != null : builder._showLabel;
        _value = builder._value;
    }

    private boolean isCheckbox()
    {
        return "checkbox".equalsIgnoreCase(_type);
    }

    public boolean isChecked()
    {
        return _checked;
    }

    public String getClassName()
    {
        return _className;
    }

    public boolean isDisabled()
    {
        return _disabled;
    }

    public Format getFormat()
    {
        return _format;
    }

    public boolean isFormGroup()
    {
        return _formGroup;
    }

    public String getId()
    {
        return _id;
    }

    public String getLabel()
    {
        return _label;
    }

    public String getLabelClassName()
    {
        return _labelClassName;
    }

    public Layout getLayout()
    {
        return _layout;
    }

    public String getMessage()
    {
        return _message;
    }

    public String getName()
    {
        return _name;
    }

    public String getOnChange()
    {
        return _onChange;
    }

    public String getOnKeyUp()
    {
        return _onKeyUp;
    }

    public String getPlaceholder()
    {
        return _placeholder;
    }

    public boolean isRequired()
    {
        return _required;
    }

    public Integer getSize()
    {
        return _size;
    }

    public boolean isShowLabel()
    {
        return _showLabel;
    }

    public String getType()
    {
        return _type;
    }

    public Object getValue()
    {
        return _value;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        if (isFormGroup())
            sb.append("<div class=\"form-group\">");

        if (isShowLabel())
            doLabel(sb);

        // wrapper for layout
        if (Layout.HORIZONTAL.equals(getLayout()))
            sb.append("<div class=\"col-sm-9 col-lg-10\">");

        doInput(sb);

        if (Layout.HORIZONTAL.equals(getLayout()))
            sb.append("</div>");
        // end wrapper for layout

        if (isFormGroup())
            sb.append("</div>");

        return sb.toString();
    }

    protected void doInput(StringBuilder sb)
    {
        sb.append("<input")
                .append(" type=\"").append(getType()).append("\"")
                .append(" name=\"").append(getName()).append("\"");

        if (StringUtils.isNotEmpty(getId()))
            sb.append(" id=\"").append(getId()).append("\"");
        if (StringUtils.isNotEmpty(getClassName()))
            sb.append(" class=\"").append(PageFlowUtil.filter(getClassName())).append("\"");
        if (StringUtils.isNotEmpty(getPlaceholder()))
            sb.append(" placeholder=\"").append(PageFlowUtil.filter(getPlaceholder())).append("\"");
        if (getSize() != null)
            sb.append(" size=\"").append(getSize()).append("\"");

        doValue(sb);
        doInputEvents(sb);

        if (isRequired())
            sb.append(" required");

        if (isDisabled())
            sb.append(" disabled");

        sb.append(">");
    }

    protected void doInputEvents(StringBuilder sb)
    {
        if (StringUtils.isNotEmpty(getOnChange()))
            sb.append(" onchange=\"").append(getOnChange()).append("\"");
        if (StringUtils.isNotEmpty(getOnKeyUp()))
            sb.append(" onkeyup=\"").append(getOnKeyUp()).append("\"");
    }

    private void doLabel(StringBuilder sb)
    {
        sb.append("<label");

        if (StringUtils.isNotEmpty(getId()))
            sb.append(" for=\"").append(getId()).append("\"");

        String cls = "";
        if (StringUtils.isNotEmpty(getLabelClassName()))
            cls += " " + getLabelClassName();
        if (Layout.HORIZONTAL.equals(getLayout()))
            cls += " col-sm-3 col-lg-2";

        if (StringUtils.isNotEmpty(cls))
            sb.append(" class=\"").append(PageFlowUtil.filter(cls)).append("\"");

        sb.append(">");

        if (getLabel() != null)
            sb.append(PageFlowUtil.filter(getLabel()));

        sb.append("</label>");
    }

    protected void doValue(StringBuilder sb)
    {
        if (isCheckbox() && isChecked())
        {
            sb.append(" checked");
        }

        if (getValue() != null && !"".equals(getValue()))
        {
            // 4934: Don't render form input values with formatter since we don't parse formatted inputs on post.
            // For now, we can at least render disabled inputs with formatting since a
            // hidden input with the actual value is emitted for disabled items.
            String safeValue;
            if (null != getFormat() && isDisabled())
            {
                try
                {
                    safeValue = getFormat().format(getValue());
                }
                catch (IllegalArgumentException x)
                {
                    safeValue = ConvertUtils.convert(getValue());
                }
            }
            else
                safeValue = ConvertUtils.convert(getValue());

            sb.append(" value=\"").append(safeValue).append("\"");
        }
    }

    @SuppressWarnings("unchecked")
    public static class InputBuilder<T extends InputBuilder<T>>
    {
        private boolean _checked;
        private String _className = "form-control";
        private boolean _disabled;
        private Format _format;
        private Boolean _formGroup;
        private String _id;
        private String _label;
        private String _labelClassName = "control-label";
        private Layout _layout;
        private String _message;
        private String _name;
        private String _onChange;
        private String _onKeyUp;
        private String _placeholder;
        private boolean _required;
        private Boolean _showLabel;
        private Integer _size;
        private String _type = "text";
        private Object _value;

        public InputBuilder()
        {
        }

        public T className(String className)
        {
            _className = className;
            return (T)this;
        }

        public T checked(boolean checked)
        {
            _checked = checked;
            return (T)this;
        }

        public T disabled(boolean disabled)
        {
            _disabled = disabled;
            return (T)this;
        }

        public T formatter(Format format)
        {
            _format = format;
            return (T)this;
        }

        public T formGroup(Boolean formGroup)
        {
            _formGroup = formGroup;
            return (T)this;
        }

        public T id(String id)
        {
            _id = id;
            return (T)this;
        }

        public T label(String label)
        {
            _label = label;
            return (T)this;
        }

        public T labelClassName(String labelClassName)
        {
            if (labelClassName != null) // prevent clearing default
                _labelClassName = labelClassName;
            return (T)this;
        }

        public T layout(Layout layout)
        {
            _layout = layout;
            return (T)this;
        }

        public T message(String message)
        {
            _message = message;
            return (T)this;
        }

        public T name(String name)
        {
            _name = name;
            return (T)this;
        }

        public T onChange(String onChange)
        {
            _onChange = onChange;
            return (T)this;
        }

        public T onKeyUp(String onKeyUp)
        {
            _onKeyUp = onKeyUp;
            return (T)this;
        }

        public T placeholder(String placeholder)
        {
            _placeholder = placeholder;
            return (T)this;
        }

        public T required(boolean required)
        {
            _required = required;
            return (T)this;
        }

        public T size(Integer size)
        {
            _size = size;
            return (T)this;
        }

        public T showLabel(Boolean showLabel)
        {
            _showLabel = showLabel;
            return (T)this;
        }

        public T type(String type)
        {
            if (type != null) // prevent clearing default
                _type = type;
            return (T)this;
        }

        public T value(Object value)
        {
            _value = value;
            return (T)this;
        }

        public Input build()
        {
            return new Input(this);
        }

        @Override
        public String toString()
        {
            return build().toString();
        }
    }
}
