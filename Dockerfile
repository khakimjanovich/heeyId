# syntax=docker/dockerfile:1
# One image per deployable, built from source: SERVICE is the adapter's directory (adapters/myid). The
# platform and the contracts are downloaded from GitHub Packages during the build:
# GH_PACKAGES_ACTOR as a build arg, the token as a build secret, which never lands in a layer:
#   docker build --build-arg GH_PACKAGES_ACTOR \
#     --secret id=gh_packages_token,env=GH_PACKAGES_TOKEN --build-arg SERVICE=adapters/myid -t heeyid/myid-adapter .
# Every value comes from env vars at run time, none has a default (README); no secret is in the image.
# Compiled once, on the builder's own platform: the jar runs on any. Only the final stage, which runs
# nothing, is per platform, so no emulation is needed.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
ARG SERVICE
ARG GH_PACKAGES_ACTOR
COPY . /src/heeyid
WORKDIR /src/heeyid
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=gh_packages_token,env=GH_PACKAGES_TOKEN,required=true \
    ./gradlew --no-daemon -p ${SERVICE:?SERVICE build arg is required: adapters/myid} bootJar \
 && java -Djarmode=tools -jar $(ls ${SERVICE}/build/libs/*.jar | grep -v -- '-plain\.jar$') extract --layers --launcher --destination /layers

# Dependencies change least, the app's own classes most: each is its own layer.
FROM eclipse-temurin:25-jre
WORKDIR /opt/heeyid
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER 10001
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
