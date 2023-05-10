-- Uninstall the previously removed "Internal" and "Synonym" modules, Issue 47703
UPDATE core.modules SET AutoUninstall = 1 WHERE Name IN ('Internal', 'Synonym');
