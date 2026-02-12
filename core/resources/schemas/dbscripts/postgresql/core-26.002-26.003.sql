CREATE TABLE IF NOT EXISTS core.ChatConversation
(
    ConversationId TEXT NOT NULL PRIMARY KEY,
    Container 	   EntityId NOT NULL,
    Created        TIMESTAMP WITHOUT TIME ZONE,
    CreatedBy      UserId NOT NULL,
    Referer		   TEXT
);

CREATE TABLE IF NOT EXISTS core.ChatMessage
(
    RowId             SERIAL PRIMARY KEY,
    ContentType		  TEXT,
    ConversationId    TEXT NOT NULL,
    Created           TIMESTAMP WITHOUT TIME ZONE,
    MessageText       TEXT,
    MessageTextFormatted TEXT,
    MessageType       TEXT NOT NULL,
    MessageMetadata   JSONB,

    CONSTRAINT fk_conversationid
        FOREIGN KEY (ConversationId)
            REFERENCES core.ChatConversation(ConversationId)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS IX_ChatMessage_ConversationId ON core.ChatMessage(ConversationId);
