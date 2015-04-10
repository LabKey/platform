package org.labkey.api.admin.sitevalidation;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 */
public class SiteValidationResult
{
    public enum Level
    {
        INFO,
        WARN,
        ERROR
    }

    final Level level;
    final String message;

    public SiteValidationResult(Level level, String message)
    {
        this.level = level;
        this.message = message;
    }

    public Level getLevel()
    {
        return level;
    }

    public String getMessage()
    {
        return message;
    }
}
