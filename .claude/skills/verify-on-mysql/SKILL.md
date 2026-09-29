---
name: verify-on-mysql
description: Check a change against real MySQL, not just H2 - apply new migrations, run the concurrency scenarios and the end-to-end journey on MySQL. Use after schema changes and for anything involving locking.
---

# Verify on MySQL

H2 (MySQL mode) runs the normal test suite, but its locking and some SQL behave differently from InnoDB. For schema
and concurrency work, also check on MySQL.

1. **Migrations + schema validation** on the developer's database, without touching their running app (use a spare
   port):
   ```bash
   ./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--server.port=18090
   ```
   Look for `Successfully applied N migration(s)` and `Started MovieBookingApplication`, then stop it
   (`pkill -f server.port=18090`).
2. **Concurrency scenarios on MySQL.** They commit data, so use the dedicated `movie_booking_it` schema:
   ```bash
   MYSQL_IT_URL='jdbc:mysql://localhost:3306/movie_booking_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' MYSQL_IT_USERNAME=root MYSQL_IT_PASSWORD="$(sed -n 's/^spring.datasource.password=//p' local.properties)" ./mvnw verify -Dit.test=BookingConcurrencyMySqlIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true
   ```
3. **Any integration test on MySQL** (e.g. the end-to-end journey): point Spring's datasource at the same schema:
   ```bash
   SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/movie_booking_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver SPRING_DATASOURCE_PASSWORD="$(sed -n 's/^spring.datasource.password=//p' local.properties)" ./mvnw verify -Dit.test=CustomerJourneyIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true
   ```
   Confirm the log shows `Database JDBC URL [jdbc:mysql://…]`.
4. **New concurrency scenario?** Add it to `concurrency/BookingConcurrencyScenarios`, so it runs on H2 and MySQL
   automatically.
