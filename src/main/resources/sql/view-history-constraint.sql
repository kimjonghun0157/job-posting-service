-- view_history 테이블 제약 조건
-- seq_number 범위 제한: 1~100 (공고당 최대 100건 조회 이력)
ALTER TABLE view_history
ADD CONSTRAINT chk_seq_number_range
CHECK (seq_number BETWEEN 1 AND 100);

-- (job_posting_id, seq_number) 유니크 제약: 동일 공고에 같은 순번 중복 방지
ALTER TABLE view_history
ADD CONSTRAINT uq_view_history_posting_seq
UNIQUE (job_posting_id, seq_number);

-- job_posting.view_count 범위 제한: 0~100
ALTER TABLE job_posting
ADD CONSTRAINT chk_view_count
CHECK (view_count BETWEEN 0 AND 100);