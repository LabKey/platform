
EXEC sp_rename 'study.StudyDesignAssays.Target', 'Type', 'COLUMN';
GO
EXEC sp_rename 'study.StudyDesignAssays.Methodology', 'Platform', 'COLUMN';
GO

ALTER TABLE study.StudyDesignAssays ADD AlternateName NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Lab NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LabPI NVARCHAR(200);