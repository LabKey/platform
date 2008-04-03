package org.labkey.api.pipeline.browse;

abstract public class FileFilter
{
    private String label;
    static public final FileFilter allFiles = new FileFilter("All Files")
    {
        public boolean accept(BrowseFile file)
        {
            return true;
        }
    };

    public FileFilter(String label)
    {
        this.label = label;
    }
    abstract public boolean accept(BrowseFile file);

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }
}
