package org.labkey.api.formSchema;

public class NumberField extends AbstractField<Double>
{
    public static final String TYPE = "text";

    public NumberField(String name, String label, String placeholder, Boolean required, Double defaultValue)
    {
        super(name, label, placeholder, required, defaultValue);
    }

    public NumberField(String name, String label, String placeholder, Boolean required, Double defaultValue, String helpText)
    {
        super(name, label, placeholder, required, defaultValue, helpText);
    }

    public NumberField(String name, String label, String placeholder, Boolean required, Double defaultValue, String helpText, String helpTextHref)
    {
        super(name, label, placeholder, required, defaultValue, helpText, helpTextHref);
    }

    @Override
    public String getType()
    {
        return TYPE;
    }
}
