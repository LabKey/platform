ALTER TABLE exp.list ADD
    DiscussionSetting SMALLINT NOT NULL DEFAULT 0,
    AllowDelete BIT NOT NULL DEFAULT 1,
    AllowUpload BIT NOT NULL DEFAULT 1,
    AllowExport BIT NOT NULL DEFAULT 1
GO
