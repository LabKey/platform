-- Uninstall the previously removed "Internal" and "Synonym" modules, Issue 47703
UPDATE core.Modules SET AutoUninstall = TRUE WHERE Name IN ('Internal', 'Synonym');
