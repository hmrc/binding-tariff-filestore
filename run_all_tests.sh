#!/usr/bin/env bash
sbt clean compile scalafmtAll scalastyleAll coverage Test/test it/Test/test dependencyUpdates coverageReport


