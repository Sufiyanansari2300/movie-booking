CREATE TABLE cities (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    state      VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_cities PRIMARY KEY (id),
    CONSTRAINT uk_cities_name UNIQUE (name)
);

CREATE TABLE theaters (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    city_id    BIGINT       NOT NULL,
    name       VARCHAR(150) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_theaters PRIMARY KEY (id),
    CONSTRAINT fk_theaters_city FOREIGN KEY (city_id) REFERENCES cities (id),
    CONSTRAINT uk_theaters_city_name UNIQUE (city_id, name)
);
