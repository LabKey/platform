-- These columns have been unused for years. https://github.com/LabKey/platform/pull/4549 cleaned up all code references.
-- Use fb_dropIfExists() to drop default constraint before dropping each column.
EXEC core.fn_dropifexists 'List', 'exp', 'COLUMN', 'EntireListTitleSetting';
EXEC core.fn_dropifexists 'List', 'exp', 'COLUMN', 'EachItemTitleSetting';
