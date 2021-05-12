package org.labkey.api.protein;

public class ProteinFeature
{
    int startIndex;
    int endIndex;
    String type;
    String description;
    String original;
    String variation;

    public ProteinFeature() {}

    public ProteinFeature(int startIndex, int endIndex, String type, String original, String variation, String description)
    {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.type = type;
        this.original = original;
        this.variation = variation;
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public int getStartIndex()
    {
        return startIndex;
    }

    public void setStartIndex(int startIndex)
    {
        this.startIndex = startIndex;
    }

    public int getEndIndex()
    {
        return endIndex;
    }

    public void setEndIndex(int endIndex)
    {
        this.endIndex = endIndex;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getOriginal()
    {
        return original;
    }

    public void setOriginal(String original)
    {
        this.original = original;
    }

    public String getVariation()
    {
        return variation;
    }

    public void setVariation(String variation)
    {
        this.variation = variation;
    }
    public boolean isVariation() {
        return "sequence variant".equals(type);
    }
}
