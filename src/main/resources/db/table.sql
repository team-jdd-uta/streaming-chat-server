-- --------------------------
-- Customer Table
-- --------------------------
CREATE TABLE customer (
                          user_id       VARCHAR2(50) PRIMARY KEY,
                          password      VARCHAR2(255),
                          name          VARCHAR2(100),
                          created_at    TIMESTAMP
);

-- --------------------------
-- Follows Table
-- --------------------------
CREATE TABLE follows (
                         following_user_id  VARCHAR2(50),
                         followed_user_id   VARCHAR2(50),
                         followed_at        TIMESTAMP,
                         CONSTRAINT pk_follows PRIMARY KEY (following_user_id, followed_user_id),
                         CONSTRAINT fk_follows_following FOREIGN KEY (following_user_id)
                             REFERENCES customer(user_id)
                             ON DELETE CASCADE,
                         CONSTRAINT fk_follows_followed FOREIGN KEY (followed_user_id)
                             REFERENCES customer(user_id)
                             ON DELETE CASCADE
);

CREATE INDEX idx_follows_following_user_id ON follows(following_user_id, followed_user_id);
CREATE INDEX idx_follows_followed_user_id  ON follows(followed_user_id, following_user_id);

-- --------------------------
-- Video Table
-- --------------------------
CREATE TABLE video (
                       video_id     NUMBER PRIMARY KEY,
                       video_name   VARCHAR2(255),
                       started_at   TIMESTAMP,
                       ended_at     TIMESTAMP,
                       category_id  NUMBER
);

-- --------------------------
-- Category Table
-- --------------------------
CREATE TABLE category (
                          category_id         NUMBER PRIMARY KEY,
                          category_name       VARCHAR2(100),
                          parent_category_id  NUMBER
) ORGANIZATION INDEX;

-- Add foreign key constraint separately
ALTER TABLE category
    ADD CONSTRAINT fk_category_parent
        FOREIGN KEY (parent_category_id)
            REFERENCES category(category_id)
            ON DELETE CASCADE;

-- --------------------------
-- Watch History Table (Partitioned)
-- --------------------------
CREATE TABLE watch_history (
                               user_id     VARCHAR2(50),
                               video_id    NUMBER,
                               started_at  TIMESTAMP,
                               ended_at    TIMESTAMP,
                               CONSTRAINT fk_watch_user FOREIGN KEY (user_id)
                                   REFERENCES customer(user_id)
                                   ON DELETE CASCADE,
                               CONSTRAINT fk_watch_video FOREIGN KEY (video_id)
                                   REFERENCES video(video_id)
                                   ON DELETE CASCADE
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

CREATE INDEX idx_watch_user_id  ON watch_history(user_id, started_at) LOCAL;
CREATE INDEX idx_watch_video_id ON watch_history(video_id, started_at) GLOBAL;

-- --------------------------
-- Video Foreign Key
-- --------------------------
ALTER TABLE video
    ADD CONSTRAINT fk_video_category
        FOREIGN KEY (category_id)
            REFERENCES category(category_id);

COMMIT;

