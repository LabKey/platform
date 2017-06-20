package org.labkey.api.jsp.taglib;

import org.labkey.api.util.element.Input;
import org.labkey.api.util.element.TextArea;

import javax.servlet.jsp.JspException;
import java.io.IOException;

public class InputTag extends SimpleTagBase
{
    private String contextContent;
    private Boolean formGroup;
    private String id;
    private String label;
    private String stateMessage;
    private String name;
    private String onChange;
    private String onKeyUp;
    private String placeholder;
    private String state;
    private String type;
    private Object value;

    public void setState(String state)
    {
        this.state = state;
    }

    public void setContextContent(String contextContent)
    {
        this.contextContent = contextContent;
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

    public void setValue(Object value)
    {
        this.value = value;
    }

    public void setOnChange(String onChange)
    {
        this.onChange = onChange;
    }

    public void setOnKeyUp(String onKeyUp)
    {
        this.onKeyUp = onKeyUp;
    }

    public void doTag() throws JspException, IOException
    {
        Input.InputBuilder input;

        if ("textarea".equalsIgnoreCase(type))
            input = new TextArea.TextAreaBuilder();
        else
            input = new Input.InputBuilder().type(type);

        input.contextContent(contextContent)
            .formGroup(formGroup)
            .id(id)
            .label(label)
            .stateMessage(stateMessage)
            .name(name)
            .onChange(onChange)
            .onKeyUp(onKeyUp)
            .placeholder(placeholder)
            .value(value);

        if (Input.State.ERROR.toString().equalsIgnoreCase(state))
            input.state(Input.State.ERROR);
        else if (Input.State.WARNING.toString().equalsIgnoreCase(state))
            input.state(Input.State.WARNING);
        else if (Input.State.SUCCESS.toString().equalsIgnoreCase(state))
            input.state(Input.State.SUCCESS);

        FormTag form = (FormTag) findAncestorWithClass(this, FormTag.class);

        // special processing within a form
        if (form != null)
        {
            // if user does not explicitly set formGroup default to true when inside form
            if (formGroup == null)
                input.formGroup(true);

            if (Input.Layout.HORIZONTAL.toString().equalsIgnoreCase(form.getLayout()))
                input.layout(Input.Layout.HORIZONTAL);

            if (Input.Layout.INLINE.toString().equalsIgnoreCase(form.getLayout()))
                input.layout(Input.Layout.INLINE);

        }

        getOut().write(input.toString());
    }
}
