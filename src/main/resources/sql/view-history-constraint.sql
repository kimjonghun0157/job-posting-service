-- view_history / job_posting 제약 조건
--
-- spring.sql.init.mode=always 이므로 매 기동마다 실행된다.
-- PostgreSQL 은 ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS 를 지원하지 않으므로
-- DO 블록으로 감싸 중복 생성 예외를 무시한다 (재기동 시 기동 실패 방지).
--
-- 주의: DO 블록 내부에 세미콜론이 있어 Spring 의 기본 구분자(;)로는 문장이 잘못 분리된다.
--       application.yml 의 spring.sql.init.separator=";;" 와 짝을 이루므로,
--       이 파일의 각 문장은 반드시 ";;" 로 끝나야 한다.

-- seq_number 범위 제한: 1~100 (공고당 최대 100건 조회 이력)
DO $$
BEGIN
    ALTER TABLE view_history
        ADD CONSTRAINT chk_seq_number_range
        CHECK (seq_number BETWEEN 1 AND 100);
EXCEPTION
    WHEN duplicate_object THEN NULL;
    WHEN duplicate_table THEN NULL;
END $$;;

-- (job_posting_id, seq_number) 유니크 제약: 동일 공고에 같은 순번 중복 방지
DO $$
BEGIN
    ALTER TABLE view_history
        ADD CONSTRAINT uq_view_history_posting_seq
        UNIQUE (job_posting_id, seq_number);
EXCEPTION
    WHEN duplicate_object THEN NULL;
    WHEN duplicate_table THEN NULL;
END $$;;

-- job_posting.view_count 범위 제한: 0~100
DO $$
BEGIN
    ALTER TABLE job_posting
        ADD CONSTRAINT chk_view_count
        CHECK (view_count BETWEEN 0 AND 100);
EXCEPTION
    WHEN duplicate_object THEN NULL;
    WHEN duplicate_table THEN NULL;
END $$;;
