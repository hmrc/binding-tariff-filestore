#!/usr/bin/env bash
sbt clean scalafmtAll scalastyle compile coverage test it:test coverageOff coverageReport dependencyUpdates