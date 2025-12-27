EXEC core.fn_dropifexists 'List', 'exp', 'DEFAULT', 'DiscussionSetting';
ALTER TABLE exp.List DROP COLUMN DiscussionSetting;
