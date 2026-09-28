CREATE TABLE movies (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    title            VARCHAR(200)  NOT NULL,
    description      VARCHAR(2000),
    language         VARCHAR(50)   NOT NULL,
    genre            VARCHAR(50)   NOT NULL,
    duration_minutes INT           NOT NULL,
    certificate      VARCHAR(10),
    release_date     DATE,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    CONSTRAINT pk_movies PRIMARY KEY (id),
    CONSTRAINT uk_movies_title_language UNIQUE (title, language)
);
