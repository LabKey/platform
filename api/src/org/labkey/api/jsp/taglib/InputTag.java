package org.labkey.api.jsp.taglib;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.jsp.JspException;
import java.io.IOException;

public class InputTag extends SimpleTagBase
{
    private boolean formGroup = true;
    private String id = null;
    private String label = null;
    private String message = null;
    private String name = null;
    private String placeholder = null;
    private String type = "text";
    private Object value = null;

    private String onChange = null;
    private String onKeyUp = null;

    public boolean isFormGroup()
    {
        return formGroup;
    }

    public void setFormGroup(boolean formGroup)
    {
        this.formGroup = formGroup;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getPlaceholder()
    {
        return placeholder;
    }

    public void setPlaceholder(String placeholder)
    {
        this.placeholder = placeholder;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public Object getValue()
    {
        return value;
    }

    public void setValue(Object value)
    {
        this.value = value;
    }

    public String getOnChange()
    {
        return onChange;
    }

    public void setOnChange(String onChange)
    {
        this.onChange = onChange;
    }

    public String getOnKeyUp()
    {
        return onKeyUp;
    }

    public void setOnKeyUp(String onKeyUp)
    {
        this.onKeyUp = onKeyUp;
    }

    public void doTag() throws JspException, IOException
    {
        StringBuilder sb = new StringBuilder();
        FormTag form = (FormTag) findAncestorWithClass(this, FormTag.class);

        if (isFormGroup())
            sb.append("<div class=\"form-group\">");

        int inputMd = -1;
        int inputLg = -1;
        boolean isInline = form != null && (form.getClassName() == null || form.getClassName().contains("form-inline"));
        String labelCls = "control-label";

        if (form != null && !isInline)
        {
            if (form.getLabelMd() > -1)
            {
                labelCls += " col-md-" + form.getLabelMd();
                inputMd = 12 - form.getLabelMd();
            }
            if (form.getLabelLg() > -1)
            {
                labelCls += " col-lg-" + form.getLabelLg();
                inputLg = 12 - form.getLabelLg();
            }
        }
        boolean hasLayout = inputMd > -1 || inputLg > -1;

        sb.append("<label");
        if (StringUtils.isNotEmpty(getId()))
            sb.append(" for=\"").append(getId()).append("\"");
        sb.append(" class=\"").append(labelCls).append("\"");
        sb.append(">");
        sb.append(h(getLabel()));
        sb.append("</label>");

        if (hasLayout)
        {
            String inputCls = "";
            if (inputMd > -1)
                inputCls += " col-md-" + inputMd;
            if (inputLg > -1)
                inputCls += " col-lg-" + inputLg;

            sb.append("<div class=\"").append(inputCls.trim()).append("\">");
        }

        doInput(sb);

        if (hasLayout)
            sb.append("</div>");

        if (isFormGroup())
            sb.append("</div>");

        getOut().write(sb.toString());
    }

    protected void doInput(StringBuilder sb)
    {
        sb.append("<input")
                .append(" type=\"").append(getType()).append("\"")
                .append(" name=\"").append(getName()).append("\"");

        if (!"file".equalsIgnoreCase(getType()))
            sb.append(" class=\"").append("form-control").append("\"");
        if (StringUtils.isNotEmpty(getId()))
            sb.append(" id=\"").append(getId()).append("\"");
        if (StringUtils.isNotEmpty(getPlaceholder()))
            sb.append(" placeholder=\"").append(getPlaceholder()).append("\"");

        doInputEvents(sb);

        if (getValue() != null)
            sb.append(" value=\"").append(getValue()).append("\"");
        sb.append(">");
    }

    protected void doInputEvents(StringBuilder sb)
    {
        if (StringUtils.isNotEmpty(getOnChange()))
            sb.append(" onchange=\"").append(getOnChange()).append("\"");
        if (StringUtils.isNotEmpty(getOnKeyUp()))
            sb.append(" onkeyup=\"").append(getOnKeyUp()).append("\"");
    }
}
