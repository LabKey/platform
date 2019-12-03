/*
    For LabKey 19.3.x and earlier, the email preferences tables lived in the 'comm' schema and were managed by the
    announcements module. As of 20.1.0, the core module now manages these tables in the 'core' schema. This script
    moves or creates the tables, as appropriate. Once we no longer upgrade from 19.3.x, we can remove the conditionals.
 */
IF (EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'comm' AND TABLE_NAME = 'EmailOptions'))
    BEGIN

        ALTER SCHEMA core TRANSFER comm.EmailOptions;
        ALTER SCHEMA core TRANSFER comm.EmailFormats;
        ALTER SCHEMA core TRANSFER comm.PageTypes;
        ALTER SCHEMA core TRANSFER comm.EmailPrefs;

    END
ELSE
    BEGIN

        CREATE TABLE core.EmailOptions
        (
            EmailOptionId INT NOT NULL,
            EmailOption NVARCHAR(50),
            Type NVARCHAR(60) NOT NULL DEFAULT 'messages',

            CONSTRAINT PK_EmailOptions PRIMARY KEY (EmailOptionId)
        );

        INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (0, 'No Email');
        INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (1, 'All conversations');
        INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (2, 'My conversations');
        INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (257, 'Daily digest of all conversations');
        INSERT INTO core.EmailOptions (EmailOptionId, EmailOption) VALUES (258, 'Daily digest of my conversations');

        -- new file email notification options
        INSERT INTO core.emailOptions (EmailOptionId, EmailOption, Type) VALUES (512, 'No Email', 'files');
        INSERT INTO core.emailOptions (EmailOptionId, EmailOption, Type) VALUES (513, '15 minute digest', 'files');
        INSERT INTO core.emailOptions (EmailOptionId, EmailOption, Type) VALUES (514, 'Daily digest', 'files');

        CREATE TABLE core.EmailFormats
        (
            EmailFormatId INT NOT NULL,
            EmailFormat NVARCHAR(20),

            CONSTRAINT PK_EmailFormats PRIMARY KEY (EmailFormatId)
        );

        INSERT INTO core.EmailFormats (EmailFormatId, EmailFormat) VALUES (0, 'Plain Text');
        INSERT INTO core.EmailFormats (EmailFormatId, EmailFormat) VALUES (1, 'HTML');

        CREATE TABLE core.PageTypes
        (
            PageTypeId INT NOT NULL,
            PageType NVARCHAR(20),

            CONSTRAINT PK_PageTypes PRIMARY KEY (PageTypeId)
        );

        INSERT INTO core.PageTypes (PageTypeId, PageType) VALUES (0, 'Message');
        INSERT INTO core.PageTypes (PageTypeId, PageType) VALUES (1, 'Wiki');

        CREATE TABLE core.EmailPrefs
        (
            Container ENTITYID,
            UserId USERID,
            EmailOptionId INT NOT NULL,
            EmailFormatId INT NOT NULL,
            PageTypeId INT NOT NULL,
            LastModifiedBy USERID,
            Type NVARCHAR(60) NOT NULL DEFAULT 'messages',
            SrcIdentifier NVARCHAR(100) NOT NULL,  -- allow subscriptions to multiple forums within a single container

            CONSTRAINT PK_EmailPrefs PRIMARY KEY (Container, UserId, Type, SrcIdentifier),
            CONSTRAINT FK_EmailPrefs_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
            CONSTRAINT FK_EmailPrefs_Principals FOREIGN KEY (UserId) REFERENCES core.Principals (UserId),
            CONSTRAINT FK_EmailPrefs_EmailOptions FOREIGN KEY (EmailOptionId) REFERENCES core.EmailOptions (EmailOptionId),
            CONSTRAINT FK_EmailPrefs_EmailFormats FOREIGN KEY (EmailFormatId) REFERENCES core.EmailFormats (EmailFormatId),
            CONSTRAINT FK_EmailPrefs_PageTypes FOREIGN KEY (PageTypeId) REFERENCES core.PageTypes (PageTypeId)
        );

    END
GO
