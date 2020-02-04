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
package org.labkey.api.jsp.taglib;

import org.labkey.api.util.HasHtmlString;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.element.DisplayField;
import org.labkey.api.util.element.Input;
import org.labkey.api.util.element.TextArea;

import java.io.IOException;

public class InputTag extends SimpleTagBase
{
    private String autoComplete; // "on" / "off"
    private Boolean checked;
    private String contextContent;
    private String className;
    private String dirName;
    private Boolean forceSmallContext;
    private String form;
    private String formAction;
    private String formEncType;
    private String formMethod;
    private Boolean formNoValidate;
    private String formTarget;
    private Boolean formGroup;
    private String id;
    private Boolean isDisabled;
    private Boolean isRequired;
    private Boolean isReadOnly;
    private Boolean isAutoFocused;
    private Boolean isMultiple;
    private String label;
    private String list;
    private String stateMessage;
    private String max;
    private Integer maxLength;
    private String min;
    private String name;
    private String onChange;
    private String onKeyUp;
    private String placeholder;
    private String pattern; // regular expression
    private Integer size;
    private String state;
    private Integer step;
    private String type;
    private HtmlString value;

    public void setAutoComplete(String autoComplete)
    {
        this.autoComplete = autoComplete;
    }

    public void setDirName(String dirName)
    {
        this.dirName = dirName;
    }

    public void setForm(String form)
    {
        this.form = form;
    }

    public void setFormAction(String formAction)
    {
        this.formAction = formAction;
    }

    public void setFormEncType(String formEncType)
    {
        this.formEncType = formEncType;
    }

    public void setFormMethod(String formMethod)
    {
        this.formMethod = formMethod;
    }

    public void setFormNoValidate(Boolean formNoValidate)
    {
        this.formNoValidate = formNoValidate;
    }

    public void setFormTarget(String formTarget)
    {
        this.formTarget = formTarget;
    }

    public void setIsAutoFocused(Boolean autoFocused)
    {
        isAutoFocused = autoFocused;
    }

    public void setIsMultiple(Boolean multiple)
    {
        isMultiple = multiple;
    }

    public void setList(String list)
    {
        this.list = list;
    }

    public void setMax(String max)
    {
        this.max = max;
    }

    public void setMin(String min)
    {
        this.min = min;
    }

    public void setPattern(String pattern)
    {
        this.pattern = pattern;
    }

    public void setStep(Integer step)
    {
        this.step = step;
    }

    public void setChecked(Boolean checked)
    {
        this.checked = checked;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    public void setIsDisabled(Boolean disabled)
    {
        this.isDisabled = disabled;
    }

    public void setIsRequired(Boolean required)
    {
        this.isRequired = required;
    }

    public void setIsReadOnly(Boolean readOnly)
    {
        this.isReadOnly = readOnly;
    }

    public void setState(String state)
    {
        this.state = state;
    }

    public void setContextContent(HtmlString contextContent)
    {
        this.contextContent = contextContent.toString();
    }

    public void setContextContent(String contextContent)
    {
        this.contextContent = contextContent;
    }

    public void setForceSmallContext(Boolean forceSmallContext)
    {
        this.forceSmallContext = forceSmallContext;
    }

    public void setFormGroup(Boolean formGroup)
    {
        this.formGroup = formGroup;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public void setStateMessage(String stateMessage)
    {
        this.stateMessage = stateMessage;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setPlaceholder(String placeholder)
    {
        this.placeholder = placeholder;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public void setValue(HasHtmlString hasHtmlString)
    {
        this.value = hasHtmlString.getHtmlString();
    }

    @Deprecated // TODO: Just call the String version without h(), like all the other tag attributes
    public void setValue(HtmlString htmlString)
    {
        this.value = htmlString;
    }

    public void setValue(String s)
    {
        this.value = HtmlString.of(s);
    }

    // Needed to handle null rowIds, etc. correctly
    public void setValue(Object o)
    {
        setValue(null != o ? o.toString() : null);
    }

    public void setOnChange(String onChange)
    {
        this.onChange = onChange;
    }

    public void setOnKeyUp(String onKeyUp)
    {
        this.onKeyUp = onKeyUp;
    }

    public void setSize(Integer size)
    {
        this.size = size;
    }

    public void setMaxLength(Integer maxLength)
    {
        this.maxLength = maxLength;
    }

    @Override
    public void doTag() throws IOException
    {
        Input.InputBuilder input;

        if ("textarea".equalsIgnoreCase(type))
            input = new TextArea.TextAreaBuilder();
        else if ("displayfield".equalsIgnoreCase(type))
            input = new DisplayField.DisplayFieldBuilder();
        else
            input = new Input.InputBuilder().type(type);

        if (null == forceSmallContext)
            forceSmallContext = false;

        if (null == checked)
            setChecked(false);

        if (null == isDisabled)
            setIsDisabled(false);

        if (null == isRequired)
            setIsRequired(false);

        if (null == isReadOnly)
            setIsReadOnly(false);

        if (null == isMultiple)
            setIsMultiple(false);

        if (null == isAutoFocused)
            setIsAutoFocused(false);

        if (null == formNoValidate)
            setFormNoValidate(false);

        input.contextContent(contextContent)
            .forceSmallContext(forceSmallContext)
            .formGroup(formGroup)
            .id(id)
            .label(label)
            .stateMessage(stateMessage)
            .name(name)
            .onChange(onChange)
            .onKeyUp(onKeyUp)
            .placeholder(placeholder)
            .disabled(isDisabled)
            .required(isRequired)
            .readOnly(isReadOnly)
            .value(value)
            .checked(checked)
            .size(size)
            .maxLength(maxLength)
            .state(Input.State.get(state))
            .autoComplete(autoComplete)
            .dirName(dirName)
            .form(form)
            .formAction(formAction)
            .formEncodingType(formEncType)
            .formMethod(formMethod)
            .formNoValidate(formNoValidate)
            .formTarget(formTarget)
            .autoFocus(isAutoFocused)
            .multiple(isMultiple)
            .dataList(list)
            .maxValue(max)
            .minValue(min)
            .regularExpression(pattern)
            .stepValue(step);

        FormTag form = (FormTag) findAncestorWithClass(this, FormTag.class);

        if (className != null)
            input.className(className);

        // special processing within a form
        if (form != null)
        {
            // if user does not explicitly set formGroup default to true when inside form
            if (formGroup == null)
                input.formGroup(true);

            input.layout(Input.Layout.get(form.getLayout()));
        }

        // TODO: HtmlString
        getOut().print(input.toString());
    }
}
