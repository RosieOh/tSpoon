-- 관심사는 여러 개라 별도 테이블로 나눈다 (예전 interests 문자열은 있으면 첫 항목으로 옮긴다)
CREATE TABLE child_interests (
    child_id   bigint      NOT NULL,
    sort_order integer     NOT NULL,
    interest   varchar(30) NOT NULL,
    CONSTRAINT pk_child_interests PRIMARY KEY (child_id, sort_order),
    CONSTRAINT fk_child_interests_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE CASCADE
);

INSERT INTO child_interests (child_id, sort_order, interest)
SELECT id, 0, left(btrim(interests), 30)
FROM children
WHERE interests IS NOT NULL AND btrim(interests) <> '';

ALTER TABLE children DROP COLUMN interests;
ALTER TABLE children ALTER COLUMN notes TYPE varchar(1000);
