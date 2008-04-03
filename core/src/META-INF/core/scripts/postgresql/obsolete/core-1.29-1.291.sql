SET search_path TO core, public;

CREATE OR REPLACE RULE Users_Update AS
	ON UPDATE TO Users DO INSTEAD
		UPDATE UsersData SET
			ModifiedBy = NEW.ModifiedBy,
			Modified = NEW.Modified,
			FirstName = NEW.FirstName,
			LastName = NEW.LastName,
			Phone = NEW.Phone,
			Mobile = NEW.Mobile,
			Pager = NEW.Pager,
			IM = NEW.IM,
			Description = NEW.Description,
			LastLogin = NEW.LastLogin
		WHERE UserId = NEW.UserId;