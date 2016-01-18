EXEC core.fn_dropifexists 'Property_setValue', 'prop', 'PROCEDURE'
GO

-- When prop.Properties.Value was changed from varchar(255) to nvarchar(max), this proc was still at varchar(2000), which could
-- cause truncation with no warning on persisting values.
CREATE PROCEDURE prop.Property_setValue(@Set INT, @Name VARCHAR(255), @Value NVARCHAR(max)) AS
  BEGIN
    IF (@Value IS NULL)
      DELETE prop.Properties WHERE "Set" = @Set AND Name = @Name
    ELSE
      BEGIN
        UPDATE prop.Properties SET Value = @Value WHERE "Set" = @Set AND Name = @Name
        IF (@@ROWCOUNT = 0)
          INSERT prop.Properties VALUES (@Set, @Name, @Value)
      END
  END;

GO