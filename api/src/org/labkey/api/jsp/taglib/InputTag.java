package org.labkey.api.jsp.taglib;

import org.labkey.api.util.element.Input;
import org.labkey.api.util.element.TextArea;

import javax.servlet.jsp.JspException;
import java.io.IOException;

public class InputTag extends SimpleTagBase
{
    private Boolean formGroup;
    private String id;
    private String label;
    private String message;
    private String name;
    private String onChange;
    private String onKeyUp;
    private String placeholder;
    private String type;
    private Object value;

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

    public void setMessage(String message)
    {
        this.message = message;
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

        input.formGroup(formGroup)
            .id(id)
            .label(label)
            .message(message)
            .name(name)
            .onChange(onChange)
            .onKeyUp(onKeyUp)
            .placeholder(placeholder)
            .value(value);

        FormTag form = (FormTag) findAncestorWithClass(this, FormTag.class);

        // special processing within a form
        if (form != null)
        {
            // if user does not explicitly set formGroup default to true when inside form
            if (formGroup == null)
                input.formGroup(true);

            if (Input.Layout.HORIZONTAL.toString().equalsIgnoreCase(form.getLayout()))
                input.layout(Input.Layout.HORIZONTAL);
        }

        getOut().write(input.toString());
    }
}
