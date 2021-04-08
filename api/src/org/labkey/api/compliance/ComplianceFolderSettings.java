package org.labkey.api.compliance;

public interface ComplianceFolderSettings
{
    boolean isActivityRequired();

    boolean isPhiRolesRequired();

    boolean isInheritTermsOfUse();

    PhiColumnBehavior getPhiColumnBehavior();

    LoggingBehavior getLoggingBehavior();
}
