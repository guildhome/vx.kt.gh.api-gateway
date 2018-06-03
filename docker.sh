#!/bin/bash
./gradlew clean build shadowjar
docker build -t vx.kt.gh.api-gateway .