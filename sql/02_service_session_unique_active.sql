-- 已有库：先关掉同一用户多出来的 active 会话，再加唯一约束
USE orange_market_simple;

UPDATE service_session s
JOIN (
    SELECT user_id, MAX(id) AS keep_id
    FROM service_session
    WHERE status = 'active'
    GROUP BY user_id
    HAVING COUNT(*) > 1
) d ON s.user_id = d.user_id
SET s.status = 'closed',
    s.closed_at = IFNULL(s.closed_at, NOW()),
    s.updated_at = NOW()
WHERE s.status = 'active'
  AND s.id <> d.keep_id;

ALTER TABLE service_session
    ADD UNIQUE KEY uk_service_session_user_active ((CASE WHEN status = 'active' THEN user_id END));
