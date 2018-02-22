/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.util.element;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.Writer;
import java.text.Format;

// TODO: Need handling for checkbox, file, and radio types
public class Input extends DisplayElement
{
    public enum Layout
    {
        HORIZONTAL,
        INLINE;

        public static Layout get(String layoutName)
        {
            for (Layout layout : values())
            {
                if (layout.name().equalsIgnoreCase(layoutName))
                    return layout;
            }
            return null;
        }
    }

    public enum State
    {
        ERROR("exclamation-circle"),
        WARNING("exclamation-triangle"),
        SUCCESS("check-circle");

        private final String iconClass;

        State(String iconClass)
        {
            this.iconClass = iconClass;
        }

        public String getIconClass()
        {
            return iconClass;
        }

        public static State get(String stateName)
        {
            for (State state : values())
            {
                if (state.name().equalsIgnoreCase(stateName))
                    return state;
            }
            return null;
        }
    }

    private final boolean _checked;
    private final String _className;
    private final String _contextContent;
    private final boolean _disabled;
    private final boolean _forceSmallContext;
    private final Format _format;
    private final boolean _formGroup;
    private final String _id;
    private final String _label;
    private final String _labelClassName;
    private final Layout _layout;
    private final String _name;
    private final String _onChange;
    private final String _onKeyUp;
    private final String _placeholder;
    private final boolean _readOnly;
    private final boolean _required;
    private final Integer _size;
    private final State _state;
    private final String _stateMessage;
    private final boolean _showLabel;
    private final String _type;
    private final boolean _unsafeValue;
    private final Object _value;
    private final boolean _needsWrapping;

    protected Input(InputBuilder builder)
    {
        _contextContent = builder._contextContent;
        _checked = builder._checked;
        _className = builder._type.equals("checkbox") || builder._type.equals("radio") ? "form-check" : builder._className;
        _disabled = builder._disabled == null ? false : builder._disabled;
        _forceSmallContext = builder._forceSmallContext == null ? false : builder._forceSmallContext;
        _format = builder._format;
        _formGroup = builder._formGroup == null ? false : builder._formGroup;
        _id = builder._id;
        _label = builder._label;
        _labelClassName = builder._labelClassName;
        _layout = builder._layout;
        _stateMessage = builder._stateMessage;
        _name = builder._name;
        _onChange = builder._onChange;
        _onKeyUp = builder._onKeyUp;
        _placeholder = builder._placeholder;
        _readOnly = builder._readOnly == null ? false : builder._readOnly;
        _required = builder._required == null ? false : builder._required;
        _type = builder._type;
        _size = builder._size;
        _state = builder._state;
        _showLabel = builder._showLabel == null ? builder._label != null : builder._showLabel;
        _unsafeValue = builder._unsafeValue == null ? false : builder._unsafeValue;
        _value = builder._value;
        _needsWrapping = builder._needsWrapping == null ? true : builder._needsWrapping;
    }

    public String getContextContent()
    {
        return _contextContent;
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

    public boolean isForceSmallContext()
    {
        return _forceSmallContext;
    }

    public Format getFormat()
    {
        return _format;
    }

    public boolean isFormGroup()
    {
        return _formGroup;
    }

    public boolean isHidden()
    {
        return "hidden".equalsIgnoreCase(_type);
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

    public String getStateMessage()
    {
        return _stateMessage;
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

    public boolean isReadOnly()
    {
        return _readOnly;
    }

    public Integer getSize()
    {
        return _size;
    }

    public State getState()
    {
        return _state;
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

    protected boolean isUnsafeValue()
    {
        return _unsafeValue;
    }

    public boolean needsWrapping()
    {
        return _needsWrapping;
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        out.write(toString());
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        if (isHidden())
        {
            doInput(sb);
        }
        else
        {
            final boolean isHorizontal = Layout.HORIZONTAL.equals(getLayout());
            final boolean needsInputGroup = needsInputGroup();
            final boolean needsLayoutWrapping = isHorizontal && needsWrapping();

            // begin form-group
            if (isFormGroup())
            {
                sb.append("<div class=\"form-group");
                if (null != getState())
                    doValidationStates(sb);
                sb.append("\">");
            }

            if (isShowLabel())
                doLabel(sb);

            // begin wrapper for layout
            if (needsLayoutWrapping)
                sb.append("<div class=\"col-sm-9 col-lg-10\">");

            // begin wrapper for input group
            if (needsInputGroup)
            {
                sb.append("<div class=\"input-group input-group-unstyled");
                if (isHorizontal)
                    sb.append(" input-group-horizontal");
                sb.append("\">");
            }

            doInput(sb);

            doStateIcon(sb);

            if (isHorizontal && !isForceSmallContext() && StringUtils.isNotEmpty(getContextContent()))
                doContextField(sb);

            // end wrapper for input group
            if (needsInputGroup)
                sb.append("</div>");

            // end wrapper for layout
            if (needsLayoutWrapping)
                sb.append("</div>");

            // end form-group
            if (isFormGroup())
                sb.append("</div>");
        }

        return sb.toString();
    }

    protected void doInput(StringBuilder sb)
    {
        sb.append("<input");
        sb.append(" type=\"").append(getType()).append("\"");
        if (StringUtils.isNotEmpty(getClassName()) && !isHidden())
        {
            sb.append(" class=\"").append(PageFlowUtil.filter(getClassName()));
            if (null != getState() && Layout.INLINE.equals(getLayout()))
                sb.append(" ").append("inline-stateful-input");
            sb.append("\"");
        }

        sb.append(" name=\"").append(PageFlowUtil.filter(getName())).append("\"");

        if (StringUtils.isNotEmpty(getId()))
            sb.append(" id=\"").append(getId()).append("\"");
        if (StringUtils.isNotEmpty(getPlaceholder()))
            sb.append(" placeholder=\"").append(PageFlowUtil.filter(getPlaceholder())).append("\"");
        if (getSize() != null)
            sb.append(" size=\"").append(getSize()).append("\"");
        if (StringUtils.isNotEmpty(getContextContent()))
            sb.append(" aria-describedby=\"").append(getId()).append("HelpBlock\""); //described by the help block

        doValue(sb);
        doInputEvents(sb);

        if (isRequired())
            sb.append(" required");

        if (isDisabled())
            sb.append(" disabled");

        if (isReadOnly())
            sb.append(" readonly");

        sb.append(">");
    }

    protected void doInputEvents(StringBuilder sb)
    {
        if (StringUtils.isNotEmpty(getOnChange()))
            sb.append(" onchange=\"").append(getOnChange()).append("\"");
        if (StringUtils.isNotEmpty(getOnKeyUp()))
            sb.append(" onkeyup=\"").append(getOnKeyUp()).append("\"");
    }

    protected void doLabel(StringBuilder sb)
    {
        sb.append("<label");

        if (StringUtils.isNotEmpty(getId()))
            sb.append(" for=\"").append(getId()).append("\"");

        String cls = "";
        if (StringUtils.isNotEmpty(getLabelClassName()))
            cls += " " + getLabelClassName();
        if (Layout.HORIZONTAL.equals(getLayout()) && needsWrapping())
            cls += " col-sm-3 col-lg-2";

        if (StringUtils.isNotEmpty(cls))
            sb.append(" class=\"").append(PageFlowUtil.filter(cls)).append("\"");

        sb.append(">");

        if (getLabel() != null)
            sb.append(PageFlowUtil.filter(getLabel()));

        if ((Layout.INLINE.equals(getLayout()) || isForceSmallContext()) && StringUtils.isNotEmpty(getContextContent()))
            doContextField(sb);

        sb.append("</label> ");
    }

    private void doStateIcon(StringBuilder sb)
    {
        if (null != getState())
        {
            String iconClass = getState() != null ? "fa fa-" + getState().getIconClass() : "";
            sb.append("<span class=\"input-group-addon validation-state\">");
            sb.append("<i class=\" validation-state-icon ");
            sb.append(iconClass).append("\"");
            if (null != getStateMessage())
            {
                sb.append(" data-tt=\"tooltip\" data-container=\"body\" data-placement=\"top\" title=\"");
                sb.append(getStateMessage());
            }
            sb.append("\"></i>");
            sb.append("</span>");
        }
    }

    private boolean needsInputGroup()
    {
        return Layout.INLINE.equals(getLayout()) && StringUtils.isNotEmpty(getContextContent()) || null != getState();
    }

    private void doValidationStates(StringBuilder sb)
    {
        if (State.ERROR.equals(getState()))
            sb.append(" has-error");
        else if (State.WARNING.equals(getState()))
            sb.append(" has-warning");
        else if (State.SUCCESS.equals(getState()))
            sb.append(" has-success");
    }

    protected void doContextField(StringBuilder sb)
    {
        if (Layout.INLINE.equals(getLayout()) || isForceSmallContext())
        {
            //not enough room when inline; context content hidden under '?' icon, tooltip shown on mouseover
            sb.append("<i class=\"fa fa-question-circle context-icon\" data-container=\"body\" data-tt=\"tooltip\" data-placement=\"top\" title=\"");
            sb.append(getContextContent());
            sb.append("\"></i>");
        }
        else
        {
            //context content goes underneath input for horizontal/vertical layout
            sb.append("<p");
            if (StringUtils.isNotEmpty(getId()))
                sb.append(" id=\"").append(getId()).append("HelpBlock\""); //used for aria-describedby
            sb.append(" class=\" help-block form-text text-muted \" >");
            sb.append(getContextContent());
            sb.append("</p>");
        }
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
            String stringValue;
            if (null != getFormat() && isDisabled())
            {
                try
                {
                    stringValue = getFormat().format(getValue());
                }
                catch (IllegalArgumentException x)
                {
                    stringValue = ConvertUtils.convert(getValue());
                }
            }
            else
                stringValue = ConvertUtils.convert(getValue());

            sb.append(" value=\"").append(isUnsafeValue() ? stringValue : PageFlowUtil.filter(stringValue)).append("\"");
        }
    }

    @SuppressWarnings("unchecked")
    public static class InputBuilder<T extends InputBuilder<T>>
    {
        private boolean _checked;
        private String _className = "form-control";
        private String _contextContent;
        private Boolean _disabled;
        private Boolean _forceSmallContext;
        private Format _format;
        private Boolean _formGroup;
        private String _id;
        private String _label;
        private String _labelClassName = "control-label";
        private Layout _layout;
        private String _name;
        private String _onChange;
        private String _onKeyUp;
        private String _placeholder;
        private Boolean _readOnly;
        private Boolean _required;
        private Boolean _showLabel;
        private Integer _size;
        private State _state;
        private String _stateMessage;
        private String _type = "text";
        private Boolean _unsafeValue;
        private Object _value;
        private Boolean _needsWrapping;

        public InputBuilder()
        {
        }

        public T contextContent(String contextContent)
        {
            _contextContent = contextContent;
            return (T)this;
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

        public T forceSmallContext(boolean forceSmallContext)
        {
            _forceSmallContext = forceSmallContext;
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

        public T stateMessage(String message)
        {
            _stateMessage = message;
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

        public T required(Boolean required)
        {
            _required = required;
            return (T)this;
        }

        public T readOnly(Boolean readOnly)
        {
            _readOnly = readOnly;
            return (T)this;
        }

        public T size(Integer size)
        {
            _size = size;
            return (T)this;
        }

        public T state(State state)
        {
            _state = state;
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

        public T unsafeValue(Object unsafeValue)
        {
            _unsafeValue = true;
            _value = unsafeValue;
            return (T)this;
        }

        public T value(Object value)
        {
            _unsafeValue = false;
            _value = value;
            return (T)this;
        }

        public T needsWrapping(Boolean wrapped)
        {
            _needsWrapping = wrapped;
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
