FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /app/build/install/TimerTamer /app
CMD ["./bin/TimerTamer"]
