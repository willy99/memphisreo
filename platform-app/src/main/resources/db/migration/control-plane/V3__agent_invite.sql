-- Invite-флоу агента — docs/security.md §9.
ALTER TABLE control_plane.account_identity
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD COLUMN invite_token varchar(255),
    ADD COLUMN invite_expires_at timestamptz;

CREATE UNIQUE INDEX idx_account_identity_invite_token
    ON control_plane.account_identity (invite_token)
    WHERE invite_token IS NOT NULL;
