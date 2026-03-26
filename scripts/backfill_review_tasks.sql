INSERT INTO review_tasks (
    article_id,
    author_id,
    title,
    word_count,
    status,
    submit_count,
    submitted_at,
    last_event_id
)
SELECT
    a.id,
    a.author_id,
    a.title,
    a.word_count,
    a.status,
    a.submit_count,
    a.last_submitted_at,
    CONCAT('backfill:', a.id, ':', COALESCE(a.submit_count, 0))
FROM articles a
WHERE a.deleted = 0
  AND a.status = 'PENDING'
ON DUPLICATE KEY UPDATE
    author_id = VALUES(author_id),
    title = VALUES(title),
    word_count = VALUES(word_count),
    status = VALUES(status),
    submit_count = VALUES(submit_count),
    submitted_at = VALUES(submitted_at),
    last_event_id = VALUES(last_event_id);
