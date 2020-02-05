/*
 * Copyright (c) 2017-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.HasHtmlString;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.Writer;
import java.util.function.Consumer;

// TODO: Need handling for checkbox, file, and radio types
public class Input extends DisplayElement implements HasHtmlString
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

    private final String _autoComplete;
    private final boolean _autoFocus;
    private final boolean _checked;
    private final String _className;
    private final String _contextContent;
    private final boolean _disabled;
    private final String _dirName;
    private final boolean _forceSmallContext;
    private final String _form;
    private final String _formAction;
    private final String _formEncType;
    private final String _formMethod;
    private final boolean _formNoValidate;
    private final String _formTarget;
    private final boolean _formGroup;
    private final String _id;
    private final String _label;
    private final String _labelClassName;
    private final Layout _layout;
    private final String _list;
    private final String _max;
    private final Integer _maxLength;
    private final String _min;
    private final boolean _multiple;
    private final String _name;
    private final boolean _needsWrapping;
    private final String _onChange;
    private final String _onKeyUp;
    private final String _placeholder;
    private final boolean _readOnly;
    private final String _regExp;
    private final boolean _required;
    private final Integer _size;
    private final State _state;
    private final Integer _step;
    private final String _stateMessage;
    private final boolean _showLabel;
    private final String _type;
    private final @Nullable HtmlString _value;

    protected Input(InputBuilder builder)
    {
        _autoComplete = builder._autoComplete;
        _autoFocus = builder._autoFocus == null ? false : builder._autoFocus;
        _contextContent = builder._contextContent;
        _checked = builder._checked;
        _className = builder._type.equals("checkbox") || builder._type.equals("radio") ? "form-check" : builder._className;
        _disabled = builder._disabled == null ? false : builder._disabled;
        _dirName = builder._dirName;
        _forceSmallContext = builder._forceSmallContext == null ? false : builder._forceSmallContext;
        _form = builder._form;
        _formAction = builder._formAction;
        _formEncType = builder._formEncodingType;
        _formMethod = builder._formMethod;
        _formTarget = builder._formTarget;
        _formNoValidate = builder._formNoValidate == null ? false : builder._formNoValidate;
        _formGroup = builder._formGroup == null ? false : builder._formGroup;
        _id = builder._id;
        _label = builder._label;
        _labelClassName = builder._labelClassName;
        _layout = builder._layout;
        _list = builder._dataList;
        _stateMessage = builder._stateMessage;
        _max = builder._max;
        _maxLength = builder._maxLength;
        _min = builder._min;
        _multiple = builder._multiple == null ? false : builder._multiple;
        _name = builder._name;
        _onChange = builder._onChange;
        _onKeyUp = builder._onKeyUp;
        _placeholder = builder._placeholder;
        _readOnly = builder._readOnly == null ? false : builder._readOnly;
        _regExp = builder._regExp;
        _required = builder._required == null ? false : builder._required;
        _type = builder._type;
        _size = builder._size;
        _state = builder._state;
        _step = builder._step;
        _showLabel = builder._showLabel == null ? builder._label != null : builder._showLabel;
        _value = builder._value;
        _needsWrapping = builder._needsWrapping == null ? true : builder._needsWrapping;
    }

    public String getAutoComplete()
    {
        return _autoComplete;
    }

    public String getContextContent()
    {
        return _contextContent;
    }

    private boolean isCheckbox()
    {
        return "checkbox".equalsIgnoreCase(_type);
    }

    private boolean isRadio()
    {
        return "radio".equalsIgnoreCase(_type);
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

    public Integer getMaxLength()
    {
        return _maxLength;
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

    public HtmlString getValue()
    {
        return _value;
    }

    public boolean needsWrapping()
    {
        return _needsWrapping;
    }

    public boolean isAutoFocused()
    {
        return _autoFocus;
    }

    public String getDirName()
    {
        return _dirName;
    }

    public String getForm()
    {
        return _form;
    }

    public String getFormAction()
    {
        return _formAction;
    }

    public String getFormEncType()
    {
        return _formEncType;
    }

    public String getFormMethod()
    {
        return _formMethod;
    }

    public boolean isFormNoValidate()
    {
        return _formNoValidate;
    }

    public String getFormTarget()
    {
        return _formTarget;
    }

    public String getList()
    {
        return _list;
    }

    public String getMax()
    {
        return _max;
    }

    public String getMin()
    {
        return _min;
    }

    public boolean isMultiple()
    {
        return _multiple;
    }

    public String getRegExp()
    {
        return _regExp;
    }

    public Integer getStep()
    {
        return _step;
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        out.write(toString());
    }

    // TODO reverse toString() and getHtmlString() (that is toString() should call getHtmlString())
    @Override
    public HtmlString getHtmlString()
    {
        return HtmlString.unsafe(toString());
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
        if (getMaxLength() != null)
            sb.append(" maxlength=\"").append(getMaxLength()).append("\"");
        if (StringUtils.isNotEmpty(getAutoComplete()))
            sb.append(" autocomplete=\"").append(getAutoComplete()).append("\"");
        if (StringUtils.isNotEmpty(getContextContent()))
            sb.append(" aria-describedby=\"").append(getId()).append("HelpBlock\""); //described by the help block
        if (StringUtils.isNotEmpty(getDirName()))
            sb.append(" dirname=\"").append(getDirName()).append("\"");
        if (StringUtils.isNotEmpty(getForm()))
            sb.append(" form=\"").append(getForm()).append("\"");
        if (StringUtils.isNotEmpty(getFormAction()))
            sb.append(" formaction=\"").append(getFormAction()).append("\"");
        if (StringUtils.isNotEmpty(getFormEncType()))
            sb.append(" formenctype=\"").append(getFormEncType()).append("\"");
        if (StringUtils.isNotEmpty(getFormMethod()))
            sb.append(" formmethod=\"").append(getFormMethod()).append("\"");
        if (StringUtils.isNotEmpty(getFormTarget()))
            sb.append(" formtarget=\"").append(getFormTarget()).append("\"");
        if (StringUtils.isNotEmpty(getList()))
            sb.append(" list=\"").append(getList()).append("\"");
        if (StringUtils.isNotEmpty(getMax()))
            sb.append(" max=\"").append(getMax()).append("\"");
        if (StringUtils.isNotEmpty(getMin()))
            sb.append(" min=\"").append(getMin()).append("\"");
        if (StringUtils.isNotEmpty(getRegExp()))
            sb.append(" pattern=\"").append(getRegExp()).append("\"");
        if (getStep() != null)
            sb.append(" step=\"").append(getStep()).append("\"");

        doValue(sb);
        doInputEvents(sb);

        if (isRequired())
            sb.append(" required");

        if (isDisabled())
            sb.append(" disabled");

        if (isReadOnly())
            sb.append(" readonly");

        if (isFormNoValidate())
            sb.append(" formnovalidate");

        if (isMultiple())
            sb.append(" multiple");

        if (isAutoFocused())
            sb.append(" autofocus");

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
        if ((isCheckbox() || isRadio()) && isChecked())
        {
            sb.append(" checked");
        }
        renderValueIfNonEmpty(s -> sb.append(" value=\"").append(s).append("\""));
    }

    protected void renderValueIfNonEmpty(Consumer<String> consumer)
    {
        if (_value != null && !"".equals(_value.toString()))
        {
            consumer.accept(_value.toString());
        }
    }

    @SuppressWarnings("unchecked")
    public static class InputBuilder<T extends InputBuilder<T>> implements HasHtmlString// TODO: extends DisplayElementBuilder?
    {
        private String _autoComplete;
        private Boolean _autoFocus;
        private boolean _checked;
        private String _className = "form-control";
        private String _contextContent;
        private String _dataList;
        private Boolean _disabled;
        private String _dirName;
        private Boolean _forceSmallContext;
        private String _form;
        private String _formAction;
        private String _formEncodingType;
        private String _formMethod;
        private Boolean _formNoValidate;
        private String _formTarget;
        private Boolean _formGroup;
        private String _id;
        private String _label;
        private String _labelClassName = "control-label";
        private Layout _layout;
        private String _max;
        private Integer _maxLength;
        private String _min;
        private Boolean _multiple;
        private String _name;
        private String _onChange;
        private String _onKeyUp;
        private String _placeholder;
        private Boolean _readOnly;
        private Boolean _required;
        private String _regExp;
        private Boolean _showLabel;
        private Integer _size;
        private State _state;
        private Integer _step;
        private String _stateMessage;
        private String _type = "text";
        private HtmlString _value;
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

        public T maxLength(Integer maxLength)
        {
            _maxLength = maxLength;
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

        public T value(HtmlString value)
        {
            _value = value;
            return (T)this;
        }

        public T value(String value)
        {
            _value = HtmlString.of(value);
            return (T)this;
        }

        public T needsWrapping(Boolean wrapped)
        {
            _needsWrapping = wrapped;
            return (T)this;
        }

        public T autoComplete(String autoComplete)
        {
            _autoComplete = autoComplete;
            return (T)this;
        }

        public T dirName(String dirName)
        {
            _dirName = dirName;
            return (T)this;
        }

        public T form(String form)
        {
            _form = form;
            return (T)this;
        }

        public T formAction(String formAction)
        {
            _formAction = formAction;
            return (T)this;
        }

        public T formEncodingType(String formEncodingType)
        {
            _formEncodingType = formEncodingType;
            return (T)this;
        }

        public T formMethod(String formMethod)
        {
            _formMethod = formMethod;
            return (T)this;
        }

        public T formNoValidate(Boolean formNoValidate)
        {
            _formNoValidate = formNoValidate;
            return (T)this;
        }

        public T formTarget(String formTarget)
        {
            _formTarget = formTarget;
            return (T)this;
        }

        public T autoFocus(Boolean autoFocus)
        {
            _autoFocus = autoFocus;
            return (T)this;
        }

        public T multiple(Boolean multiple)
        {
            _multiple = multiple;
            return (T)this;
        }

        public T dataList(String dataList)
        {
            _dataList = dataList;
            return (T)this;
        }

        public T maxValue(String maxValue)
        {
            _max = maxValue;
            return (T)this;
        }

        public T minValue(String minValue)
        {
            _min = minValue;
            return (T)this;
        }

        public T regularExpression(String regularExpression)
        {
            _regExp = regularExpression;
            return (T)this;
        }

        public T stepValue(Integer stepValue)
        {
            _step = stepValue;
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

        @Override
        public HtmlString getHtmlString()
        {
            return HtmlString.unsafe(toString());
        }
    }
}
