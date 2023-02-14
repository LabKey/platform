DELETE FROM pipeline.StatusFiles WHERE FilePath IS NULL;

ALTER TABLE pipeline.StatusFiles ALTER COLUMN FilePath SET NOT NULL;