ALTER TABLE pipeline.StatusFiles ADD
    JobParent uniqueidentifier,
    JobStore NTEXT
GO