# Camel Quarkus Performance Regression Sample Base

This project is merely a template whose vocation is to be run against different Camel Quarkus version in JVM and native mode.
Once instantiated with a given Camel Quarkus version, this template can package a sample Camel Quarkus HTTP Application, start it and compute the mean throughput with the hyperfoil-maven-plugin.

The packaging and testing logic is based only on maven plugins, so it should work platform wise.
In native mode, it is advised to use container build as each Camel Quarkus Version comes with a specific version of Graal VM.