/* pipeline-2.30-2.31.sql */

ALTER TABLE pipeline.StatusFiles ADD
    JobParent uniqueidentifier,
    JobStore NTEXT
GO