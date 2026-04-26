# Stage 1: Build
FROM sbtscala/scala-sbt:eclipse-temurin-23.0.1_11_1.10.7_3.3.4 AS build
WORKDIR /app
COPY project/build.properties project/plugins.sbt project/
COPY build.sbt .
# Download dependencies first (cached unless build.sbt changes)
RUN sbt update
COPY src/ src/
RUN sbt stage

# Stage 2: Run (slim JRE, no sbt/build tools)
FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=build /app/target/universal/stage/ .
EXPOSE 8080
CMD ["bin/chesslab-engine"]
