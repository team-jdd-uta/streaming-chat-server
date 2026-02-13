-- init-user.sql
-- Pluggable Database XEPDB1 안에서 실행
ALTER SESSION SET CONTAINER = XEPDB1;

-- --------------------------
-- 사용자 생성 및 권한
-- --------------------------
CREATE USER UTA IDENTIFIED BY 1234;

-- 필수: 로그인 권한
GRANT CREATE SESSION TO UTA;

-- 개발용 권한 (선택)
GRANT CONNECT, RESOURCE, DBA TO UTA;

-- --------------------------
-- Customer Table
-- --------------------------
CREATE TABLE UTA.customer (
                              user_id    VARCHAR2(50) PRIMARY KEY,
                              password   VARCHAR2(255),
                              name       VARCHAR2(100),
                              created_at TIMESTAMP
);

-- --------------------------
-- Follows Table
-- --------------------------
CREATE TABLE UTA.follows (
                             following_user_id VARCHAR2(50),
                             followed_user_id  VARCHAR2(50),
                             followed_at       TIMESTAMP,
                             CONSTRAINT pk_follows PRIMARY KEY (following_user_id, followed_user_id),
                             CONSTRAINT fk_follows_following FOREIGN KEY (following_user_id)
                                 REFERENCES UTA.customer(user_id) ON DELETE CASCADE,
                             CONSTRAINT fk_follows_followed FOREIGN KEY (followed_user_id)
                                 REFERENCES UTA.customer(user_id) ON DELETE CASCADE
);

CREATE INDEX UTA.idx_follows_following_user_id ON UTA.follows(following_user_id, followed_user_id);
CREATE INDEX UTA.idx_follows_followed_user_id  ON UTA.follows(followed_user_id, following_user_id);

-- --------------------------
-- Video Table
-- --------------------------
CREATE TABLE UTA.video (
                           video_id    NUMBER PRIMARY KEY,
                           video_name  VARCHAR2(255),
                           started_at  TIMESTAMP,
                           ended_at    TIMESTAMP,
                           category_id NUMBER
);

-- --------------------------
-- Category Table
-- --------------------------
CREATE TABLE UTA.category (
                              category_id        NUMBER PRIMARY KEY,
                              category_name      VARCHAR2(100),
                              parent_category_id NUMBER
) ORGANIZATION INDEX;

ALTER TABLE UTA.category
    ADD CONSTRAINT fk_category_parent
        FOREIGN KEY (parent_category_id)
            REFERENCES UTA.category(category_id)
            ON DELETE CASCADE;

-- --------------------------
-- Watch History Table (Partitioned)
-- --------------------------
CREATE TABLE UTA.watch_history (
                                   user_id    VARCHAR2(50),
                                   video_id   NUMBER,
                                   started_at TIMESTAMP,
                                   ended_at   TIMESTAMP,
                                   CONSTRAINT fk_watch_user FOREIGN KEY (user_id)
                                       REFERENCES UTA.customer(user_id) ON DELETE CASCADE,
                                   CONSTRAINT fk_watch_video FOREIGN KEY (video_id)
                                       REFERENCES UTA.video(video_id) ON DELETE CASCADE
)
    PARTITION BY RANGE (started_at)
SUBPARTITION BY HASH (user_id)
SUBPARTITIONS 4
(
    PARTITION watch_history_2026_01 VALUES LESS THAN (TO_DATE('2026-02-01','YYYY-MM-DD')),
    PARTITION watch_history_2026_02 VALUES LESS THAN (TO_DATE('2026-03-01','YYYY-MM-DD')),
    PARTITION watch_history_2026_03 VALUES LESS THAN (TO_DATE('2026-04-01','YYYY-MM-DD')),
    PARTITION watch_history_max   VALUES LESS THAN (MAXVALUE)
);

CREATE INDEX UTA.idx_watch_user_id  ON UTA.watch_history(user_id, started_at) LOCAL;
CREATE INDEX UTA.idx_watch_video_id ON UTA.watch_history(video_id, started_at) GLOBAL;

-- --------------------------
-- Video Foreign Key
-- --------------------------
ALTER TABLE UTA.video
    ADD CONSTRAINT fk_video_category
        FOREIGN KEY (category_id)
            REFERENCES UTA.category(category_id);

COMMIT;


ALTER SESSION SET CONTAINER = XEPDB1;

-- --------------------------
-- 1. Category (10개)
-- --------------------------
ALTER SESSION SET CONTAINER = XEPDB1;

-- --------------------------
-- 1. Category (10개)
-- --------------------------
BEGIN
FOR i IN 1..10 LOOP
        INSERT INTO UTA.category (category_id, category_name, parent_category_id)
        VALUES (i, 'Category_' || i, NULL);
END LOOP;
COMMIT;
END;
/

-- --------------------------
-- 2. Customer (100개)
-- --------------------------
BEGIN
FOR i IN 1..100 LOOP
        INSERT INTO UTA.customer (user_id, password, name, created_at)
        VALUES (
            'user' || LPAD(i, 3, '0'),
            'pass' || i,
            'User ' || i,
            SYSTIMESTAMP - NUMTODSINTERVAL(TRUNC(DBMS_RANDOM.VALUE(0, 365)), 'DAY')
        );
END LOOP;
COMMIT;
END;
/

-- --------------------------
-- 3. Video (100개)
-- --------------------------
BEGIN
FOR i IN 1..100 LOOP
        INSERT INTO UTA.video (video_id, video_name, started_at, ended_at, category_id)
        VALUES (
            i,
            'Video_' || i,
            SYSTIMESTAMP - NUMTODSINTERVAL(TRUNC(DBMS_RANDOM.VALUE(0, 30)), 'DAY'),
            SYSTIMESTAMP + NUMTODSINTERVAL(TRUNC(DBMS_RANDOM.VALUE(1, 10)), 'DAY'),
            TRUNC(MOD(i-1,10))+1  -- 안전하게 1~10 범위
        );
END LOOP;
COMMIT;
END;
/

-- --------------------------
-- 4. Follows (100개)
-- --------------------------
BEGIN
FOR i IN 1..100 LOOP
        INSERT INTO UTA.follows (following_user_id, followed_user_id, followed_at)
        VALUES (
            'user' || LPAD(i, 3, '0'),
            'user' || LPAD(TRUNC(MOD(i + TRUNC(DBMS_RANDOM.VALUE(1,100))-1,100))+1, 3, '0'),
            SYSTIMESTAMP - NUMTODSINTERVAL(TRUNC(DBMS_RANDOM.VALUE(0, 365)), 'DAY')
        );
END LOOP;
COMMIT;
END;
/

-- --------------------------
-- 5. Watch History (100개)
-- --------------------------
BEGIN
FOR i IN 1..100 LOOP
        INSERT INTO UTA.watch_history (user_id, video_id, started_at, ended_at)
        VALUES (
            'user' || LPAD(TRUNC(MOD(i-1,100))+1, 3, '0'),
            TRUNC(MOD(i-1,100))+1,
            SYSTIMESTAMP - NUMTODSINTERVAL(TRUNC(DBMS_RANDOM.VALUE(0,30)), 'DAY'),
            SYSTIMESTAMP + NUMTODSINTERVAL(TRUNC(DBMS_RANDOM.VALUE(0,10)), 'DAY')
        );
END LOOP;
COMMIT;
END;
/