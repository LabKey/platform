DELETE FROM pipeline.StatusFiles WHERE FilePath IS NULL;

-- Make pipeline.StatusFiles.FilePath NOT NULL, dropping and recreating unique constraint
ALTER TABLE pipeline.StatusFiles DROP CONSTRAINT UQ_StatusFiles_FilePath;

ALTER TABLE pipeline.StatusFiles ALTER COLUMN FilePath NVARCHAR(1024) NOT NULL;

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT UQ_StatusFiles_FilePath UNIQUE (FilePath);