DELETE FROM users
WHERE username IN ('victor', 'simeon', 'caesar');

UPDATE users
SET
    password_hash = '$2a$10$M2Ix/IQKYXLgTQxzlMU2QO5PkucNdHR5tR2pNL30FXd5XBBZ1BPwW',
    role = 'ADMIN'
WHERE username = 'adam';

INSERT INTO users (username, password_hash, role)
SELECT
    'adam',
    '$2a$10$M2Ix/IQKYXLgTQxzlMU2QO5PkucNdHR5tR2pNL30FXd5XBBZ1BPwW',
    'ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'adam'
);