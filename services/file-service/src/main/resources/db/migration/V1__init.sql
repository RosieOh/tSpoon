-- 이름 규칙: 테이블 snake_case 복수형 · 제약 pk_/uk_/fk_<테이블>_<컬럼> · 인덱스 idx_<테이블>_<컬럼>
-- enum 컬럼은 CHECK 제약 없이 varchar 로 둔다 (값을 추가할 때마다 DDL 을 바꾸지 않도록)

-- 파일 정보. 바이트는 S3 호환 저장소에 있고 여기에는 메타데이터만 둔다.
-- id 는 추측할 수 없는 UUID — 공개 주소(/api/files/{id})에 쓰이므로 순번으로 다른 파일을 훑지 못하게 한다
CREATE TABLE files (
    id           uuid           NOT NULL,
    owner_id     bigint         NOT NULL,
    file_name    varchar(255)   NOT NULL,
    content_type varchar(100)   NOT NULL,
    size         bigint         NOT NULL,
    storage_key  varchar(500)   NOT NULL,
    status       varchar(30)    NOT NULL,
    created_at   timestamptz(6) NOT NULL,
    uploaded_at  timestamptz(6),
    CONSTRAINT pk_files PRIMARY KEY (id),
    CONSTRAINT uk_files_storage_key UNIQUE (storage_key)
);

-- 내 파일 목록 (최신순)
CREATE INDEX idx_files_owner_id_created_at ON files (owner_id, created_at DESC);
-- 업로드하지 않고 남은 요청 정리
CREATE INDEX idx_files_pending_created_at ON files (created_at) WHERE status = 'PENDING';
