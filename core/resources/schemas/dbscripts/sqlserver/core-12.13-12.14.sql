-- Run the script that installs the CLR GROUP_CONCAT aggregrate functions on SQL Server 2008 and above. Run the script
-- via upgrade code to do the version check and skip the install on SQL Server 2005.
EXEC core.executeJavaUpgradeCode 'installGroupConcat';
