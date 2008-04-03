-- Add new daily digest options
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (257, 'Daily digest of all conversations');
INSERT INTO comm.EmailOptions (EmailOptionId, EmailOption) VALUES (258, 'Daily digest of my conversations');

-- Change daily digest to a separate bit in the email option.  Because of FK, must update first, then delete the old option.
UPDATE comm.EmailPrefs SET EmailOptionId = 257 WHERE EmailOptionId = 3;
DELETE FROM comm.EmailOptions WHERE EmailOptionId = 3;

-- Better descriptions for these options
UPDATE comm.EmailOptions SET EmailOption = 'No email' WHERE EmailOptionId = 0;
UPDATE comm.EmailOptions SET EmailOption = 'All conversations' WHERE EmailOptionId = 1;
UPDATE comm.EmailOptions SET EmailOption = 'My conversations' WHERE EmailOptionId = 2;

-- Change folder defaults from 'None' to 'My conversations' (email is sent if you're on the member list or if you've posted to a conversation)
UPDATE prop.Properties SET Value = '2' WHERE
    Set IN (SELECT Set FROM prop.PropertySets WHERE Category = 'defaultEmailSettings' AND UserId = 0) AND
    Name = 'defaultEmailOption' AND
    Value = '0';