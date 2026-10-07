# Third-party inventory

This inventory lists every third-party package that the sample resolves, one table per surface, with the
shipped surfaces first and the tooling that never leaves the build machine after them. Produced on
2026-10-03 with three tools: `license-maven-plugin` 2.4.0 over the runtime scope (Maven scopes `compile` and
`runtime`) of every Maven project, `license-checker-rseidelsohn` 5.0.1 over the installed `package-lock.json`, and
`pip-licenses` 5.5.5 over fresh installs of the pinned Python requirements. Licences are the SPDX identifiers each
package declares in its own metadata. A dual licence is written `A OR B (A elected)`, where the elected
licence is the one under which the sample uses the package. The sample's own packages (MIT-0) are not listed.
The summary, the weak-copyleft components and the full licence texts are in
[THIRD-PARTY-LICENSES.md](../THIRD-PARTY-LICENSES.md).

## Java service images

The six images built by `modules/plm-services/Dockerfile` and `modules/query-service/Dockerfile`: plm-fr, plm-de,
plm-uk, plm-es and atelier-core (the PLM-side services, which share the `plm-common` library) and query-service.
249 artefacts.

| Artifact | Version | Licence | Images |
|---|---|---|---|
| aopalliance:aopalliance | 1.0 | Public domain | query-service |
| ch.qos.logback:logback-classic | 1.5.34 | EPL-2.0 OR LGPL-2.1-only (EPL-2.0 elected) | all six images |
| ch.qos.logback:logback-core | 1.5.34 | EPL-2.0 OR LGPL-2.1-only (EPL-2.0 elected) | all six images |
| com.apicatalog:titanium-jcs | 1.1.1 | Apache-2.0 | atelier-core and query-service |
| com.apicatalog:titanium-json-ld | 1.7.0 | Apache-2.0 | atelier-core and query-service |
| com.apicatalog:titanium-rdf-api | 1.0.0 | Apache-2.0 | atelier-core and query-service |
| com.apicatalog:titanium-rdf-n-quads | 1.0.2 | Apache-2.0 | atelier-core and query-service |
| com.ethlo.time:itu | 1.14.0 | Apache-2.0 | query-service |
| com.fasterxml.jackson.core:jackson-annotations | 2.21 | Apache-2.0 | all six images |
| com.fasterxml.jackson.core:jackson-core | 2.21.7 | Apache-2.0 | all six images |
| com.fasterxml.jackson.core:jackson-databind | 2.21.7 | Apache-2.0 | all six images |
| com.fasterxml.jackson.dataformat:jackson-dataformat-toml | 2.21.7 | Apache-2.0 | the four PLM services and atelier-core |
| com.fasterxml.jackson.dataformat:jackson-dataformat-yaml | 2.21.7 | Apache-2.0 | query-service |
| com.fasterxml.jackson.datatype:jackson-datatype-guava | 2.21.7 | Apache-2.0 | query-service |
| com.fasterxml.jackson.datatype:jackson-datatype-jdk8 | 2.21.7 | Apache-2.0 | all six images |
| com.fasterxml.jackson.datatype:jackson-datatype-jsr310 | 2.21.7 | Apache-2.0 | all six images |
| com.fasterxml.jackson.module:jackson-module-parameter-names | 2.21.7 | Apache-2.0 | all six images |
| com.fasterxml:classmate | 1.7.3 | Apache-2.0 | the four PLM services and atelier-core |
| com.github.andrewoma.dexx:collection | 0.7 | MIT | atelier-core and query-service |
| com.github.ben-manes.caffeine:caffeine | 3.2.4 | Apache-2.0 | atelier-core and query-service |
| com.github.jsonld-java:jsonld-java | 0.13.4 | BSD-3-Clause | query-service |
| com.github.jsqlparser:jsqlparser | 4.4 | LGPL-2.1-only OR Apache-2.0 (Apache-2.0 elected) | query-service |
| com.github.jsqlparser:jsqlparser | 5.4 | LGPL-2.1-only OR Apache-2.0 (Apache-2.0 elected) | the four PLM services and atelier-core |
| com.github.vsonnier:hppcrt | 0.7.5 | Apache-2.0 | query-service |
| com.google.code.findbugs:jsr305 | 3.0.2 | Apache-2.0 | query-service |
| com.google.code.gson:gson | 2.14.0 | Apache-2.0 | atelier-core and query-service |
| com.google.errorprone:error_prone_annotations | 2.47.0 | Apache-2.0 | query-service |
| com.google.errorprone:error_prone_annotations | 2.48.0 | Apache-2.0 | atelier-core |
| com.google.guava:failureaccess | 1.0.3 | Apache-2.0 | query-service |
| com.google.guava:guava | 33.6.0-jre | Apache-2.0 | query-service |
| com.google.guava:listenablefuture | 9999.0-empty-to-avoid-conflict-with-guava | Apache-2.0 | query-service |
| com.google.inject.extensions:guice-assistedinject | 5.0.1 | Apache-2.0 | query-service |
| com.google.inject:guice | 5.0.1 | Apache-2.0 | query-service |
| com.google.j2objc:j2objc-annotations | 3.1 | Apache-2.0 | query-service |
| com.google.protobuf:protobuf-java | 4.35.1 | BSD-3-Clause | atelier-core and query-service |
| com.moandjiezana.toml:toml4j | 0.7.2 | MIT | query-service |
| com.networknt:json-schema-validator | 2.0.0 | Apache-2.0 | query-service |
| com.sun.istack:istack-commons-runtime | 4.1.2 | EDL-1.0 | the four PLM services and atelier-core |
| com.zaxxer:HikariCP | 6.3.3 | Apache-2.0 | all six images |
| commons-codec:commons-codec | 1.22.0 | Apache-2.0 | atelier-core and query-service |
| commons-io:commons-io | 2.14.0 | Apache-2.0 | query-service |
| commons-io:commons-io | 2.22.0 | Apache-2.0 | atelier-core |
| eu.optique-project:r2rml-api-core | 0.9.1 | Apache-2.0 | query-service |
| eu.optique-project:r2rml-api-rdf4j-binding | 0.9.1 | Apache-2.0 | query-service |
| io.github.solf:nullanno | 3.0.0 | EPL-1.0 | query-service |
| io.micrometer:micrometer-commons | 1.15.12 | Apache-2.0 | all six images |
| io.micrometer:micrometer-core | 1.15.12 | Apache-2.0 | the four PLM services and atelier-core |
| io.micrometer:micrometer-jakarta9 | 1.15.12 | Apache-2.0 | the four PLM services and atelier-core |
| io.micrometer:micrometer-observation | 1.15.12 | Apache-2.0 | all six images |
| io.mikael:urlbuilder | 2.0.9 | Apache-2.0 | query-service |
| io.modelcontextprotocol.sdk:mcp-core | 0.18.4 | MIT | query-service |
| io.modelcontextprotocol.sdk:mcp-json-jackson2 | 0.18.4 | MIT | query-service |
| io.modelcontextprotocol.sdk:mcp-spring-webmvc | 0.18.4 | MIT | query-service |
| io.projectreactor:reactor-core | 3.7.19 | Apache-2.0 | query-service |
| io.smallrye:jandex | 3.2.0 | Apache-2.0 | the four PLM services and atelier-core |
| it.unibz.inf.ontop:ontop-kg-query | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-mapping-core | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-mapping-native | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-mapping-owlapi | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-mapping-r2rml | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-mapping-sql-all | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-mapping-sql-core | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-mapping-sql-owlapi | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-model | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-obda-core | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-ontology-owlapi | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-optimization | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-rdb | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-reformulation-core | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-reformulation-sql | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-system-core | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-system-owlapi | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-system-sql-core | 5.5.0 | Apache-2.0 | query-service |
| it.unibz.inf.ontop:ontop-system-sql-owlapi | 5.5.0 | Apache-2.0 | query-service |
| jakarta.activation:jakarta.activation-api | 2.1.4 | EDL-1.0 | the four PLM services and atelier-core |
| jakarta.annotation:jakarta.annotation-api | 2.1.1 | EPL-2.0 OR GPL-2.0-with-classpath-exception (EPL-2.0 elected) | all six images |
| jakarta.inject:jakarta.inject-api | 2.0.1 | Apache-2.0 | the four PLM services and atelier-core |
| jakarta.json:jakarta.json-api | 2.1.3 | EPL-2.0 OR GPL-2.0-with-classpath-exception (EPL-2.0 elected) | query-service |
| jakarta.persistence:jakarta.persistence-api | 3.1.0 | EDL-1.0 OR EPL-2.0 (EDL-1.0 elected) | the four PLM services and atelier-core |
| jakarta.transaction:jakarta.transaction-api | 2.0.1 | EPL-2.0 OR GPL-2.0-with-classpath-exception (EPL-2.0 elected) | the four PLM services and atelier-core |
| jakarta.xml.bind:jakarta.xml.bind-api | 4.0.5 | EDL-1.0 | the four PLM services and atelier-core |
| javax.inject:javax.inject | 1 | Apache-2.0 | query-service |
| net.bytebuddy:byte-buddy | 1.17.8 | Apache-2.0 | the four PLM services and atelier-core |
| net.sourceforge.owlapi:owlapi-api | 5.5.1 | Apache-2.0 OR LGPL-3.0-only (Apache-2.0 elected) | query-service |
| net.sourceforge.owlapi:owlapi-apibinding | 5.5.1 | Apache-2.0 OR LGPL-3.0-only (Apache-2.0 elected) | query-service |
| net.sourceforge.owlapi:owlapi-impl | 5.5.1 | Apache-2.0 OR LGPL-3.0-only (Apache-2.0 elected) | query-service |
| net.sourceforge.owlapi:owlapi-oboformat | 5.5.1 | BSD-3-Clause | query-service |
| net.sourceforge.owlapi:owlapi-parsers | 5.5.1 | Apache-2.0 OR LGPL-3.0-only (Apache-2.0 elected) | query-service |
| net.sourceforge.owlapi:owlapi-rio | 5.5.1 | Apache-2.0 OR LGPL-3.0-only (Apache-2.0 elected) | query-service |
| net.sourceforge.owlapi:owlapi-tools | 5.5.1 | Apache-2.0 OR LGPL-3.0-only (Apache-2.0 elected) | query-service |
| no.hasmac:hasmac-json-ld | 0.9.0 | Apache-2.0 | query-service |
| org.antlr:antlr4-runtime | 4.13.1 | BSD-3-Clause | query-service |
| org.antlr:antlr4-runtime | 4.13.2 | BSD-3-Clause | the four PLM services and atelier-core |
| org.apache.commons:commons-collections4 | 4.5.0 | Apache-2.0 | atelier-core and query-service |
| org.apache.commons:commons-compress | 1.28.0 | Apache-2.0 | atelier-core and query-service |
| org.apache.commons:commons-csv | 1.14.1 | Apache-2.0 | atelier-core and query-service |
| org.apache.commons:commons-lang3 | 3.20.0 | Apache-2.0 | atelier-core and query-service |
| org.apache.commons:commons-rdf-api | 0.5.0 | Apache-2.0 | query-service |
| org.apache.commons:commons-rdf-rdf4j | 0.5.0 | Apache-2.0 | query-service |
| org.apache.commons:commons-rdf-simple | 0.5.0 | Apache-2.0 | query-service |
| org.apache.commons:commons-text | 1.10.0 | Apache-2.0 | query-service |
| org.apache.httpcomponents:httpclient | 4.5.14 | Apache-2.0 | query-service |
| org.apache.httpcomponents:httpclient-cache | 4.5.14 | Apache-2.0 | query-service |
| org.apache.httpcomponents:httpcore | 4.4.16 | Apache-2.0 | query-service |
| org.apache.jena:jena-arq | 6.2.0 | Apache-2.0 | atelier-core and query-service |
| org.apache.jena:jena-base | 6.2.0 | Apache-2.0 | atelier-core and query-service |
| org.apache.jena:jena-core | 6.2.0 | Apache-2.0 | atelier-core and query-service |
| org.apache.jena:jena-iri3986 | 6.2.0 | Apache-2.0 | atelier-core and query-service |
| org.apache.jena:jena-langtag | 6.2.0 | Apache-2.0 | atelier-core and query-service |
| org.apache.jena:jena-shacl | 6.2.0 | Apache-2.0 | query-service |
| org.apache.logging.log4j:log4j-api | 2.25.5 | Apache-2.0 | all six images |
| org.apache.logging.log4j:log4j-to-slf4j | 2.25.5 | Apache-2.0 | all six images |
| org.apache.thrift:libthrift | 0.24.0 | Apache-2.0 | atelier-core and query-service |
| org.apache.tomcat.embed:tomcat-embed-core | 10.1.60 | Apache-2.0 | all six images |
| org.apache.tomcat.embed:tomcat-embed-el | 10.1.60 | Apache-2.0 | all six images |
| org.apache.tomcat.embed:tomcat-embed-websocket | 10.1.60 | Apache-2.0 | all six images |
| org.apache.tomcat:tomcat-jdbc | 10.1.60 | Apache-2.0 | query-service |
| org.apache.tomcat:tomcat-juli | 10.1.60 | Apache-2.0 | query-service |
| org.aspectj:aspectjweaver | 1.9.25.1 | EPL-2.0 | the four PLM services and atelier-core |
| org.checkerframework:checker-qual | 3.55.1 | MIT | all six images |
| org.eclipse.angus:angus-activation | 2.0.3 | EDL-1.0 | the four PLM services and atelier-core |
| org.eclipse.rdf4j:rdf4j-common-annotation | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-common-exception | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-common-io | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-common-iterator | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-common-order | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-common-text | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-common-xml | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-http-client | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-http-protocol | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-model | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-model-api | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-model-vocabulary | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-query | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-queryalgebra-evaluation | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-queryalgebra-model | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-queryparser-api | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-queryparser-sparql | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-queryresultio-api | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-queryresultio-sparqlxml | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-repository-api | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-repository-sail | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-repository-sparql | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-api | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-binary | 5.0.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-datatypes | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-hdt | 5.0.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-jsonld | 5.0.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-languages | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-n3 | 5.0.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-nquads | 5.0.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-ntriples | 5.0.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-rdfjson | 5.0.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-rdfxml | 5.0.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-trig | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-trix | 5.0.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-rio-turtle | 5.1.4 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-sail-api | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-sail-base | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-sail-inferencer | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-sail-memory | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-sail-model | 2.2.2 | EDL-1.0 | query-service |
| org.eclipse.rdf4j:rdf4j-util | 2.2.2 | EDL-1.0 | query-service |
| org.flywaydb:flyway-core | 11.7.2 | Apache-2.0 | the four PLM services and atelier-core |
| org.flywaydb:flyway-database-postgresql | 11.7.2 | Apache-2.0 | the four PLM services and atelier-core |
| org.glassfish.jaxb:jaxb-core | 4.0.9 | EDL-1.0 | the four PLM services and atelier-core |
| org.glassfish.jaxb:jaxb-runtime | 4.0.9 | EDL-1.0 | the four PLM services and atelier-core |
| org.glassfish.jaxb:txw2 | 4.0.9 | EDL-1.0 | the four PLM services and atelier-core |
| org.glassfish:jakarta.json | 2.0.1 | EPL-2.0 OR GPL-2.0-with-classpath-exception (EPL-2.0 elected) | atelier-core and query-service |
| org.hdrhistogram:HdrHistogram | 2.2.2 | BSD-2-Clause OR CC0-1.0 (BSD-2-Clause elected) | the four PLM services and atelier-core |
| org.hibernate.common:hibernate-commons-annotations | 7.0.3.Final | Apache-2.0 | the four PLM services and atelier-core |
| org.hibernate.orm:hibernate-core | 6.6.53.Final | LGPL-2.1-or-later (used under version 2.1) | the four PLM services and atelier-core |
| org.javabits.jgrapht:jgrapht-core | 0.9.3 | EPL-1.0 OR LGPL-2.1-only (EPL-1.0 elected) | query-service |
| org.jboss.logging:jboss-logging | 3.6.3.Final | Apache-2.0 | the four PLM services and atelier-core |
| org.jspecify:jspecify | 1.0.0 | Apache-2.0 | atelier-core and query-service |
| org.latencyutils:LatencyUtils | 2.0.3 | CC0-1.0 | the four PLM services and atelier-core |
| org.locationtech.proj4j:proj4j | 1.1.1 | Apache-2.0 | query-service |
| org.mapdb:mapdb | 1.0.8 | Apache-2.0 | query-service |
| org.mapstruct:mapstruct | 1.6.3 | Apache-2.0 | the four PLM services |
| org.postgresql:postgresql | 42.7.13 | BSD-2-Clause | all six images |
| org.reactivestreams:reactive-streams | 1.0.4 | MIT-0 | all six images |
| org.roaringbitmap:RoaringBitmap | 1.6.15 | Apache-2.0 | atelier-core and query-service |
| org.slf4j:jcl-over-slf4j | 2.0.18 | Apache-2.0 | atelier-core and query-service |
| org.slf4j:jul-to-slf4j | 2.0.18 | MIT | all six images |
| org.slf4j:log4j-over-slf4j | 2.0.18 | Apache-2.0 | query-service |
| org.slf4j:slf4j-api | 2.0.18 | MIT | all six images |
| org.springframework.boot:spring-boot | 3.5.16 | Apache-2.0 | all six images |
| org.springframework.boot:spring-boot-actuator | 3.5.16 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework.boot:spring-boot-actuator-autoconfigure | 3.5.16 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework.boot:spring-boot-autoconfigure | 3.5.16 | Apache-2.0 | all six images |
| org.springframework.boot:spring-boot-jarmode-tools | 3.5.16 | Apache-2.0 | all six images |
| org.springframework.boot:spring-boot-starter | 3.5.16 | Apache-2.0 | all six images |
| org.springframework.boot:spring-boot-starter-actuator | 3.5.16 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework.boot:spring-boot-starter-data-jpa | 3.5.16 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework.boot:spring-boot-starter-jdbc | 3.5.16 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework.boot:spring-boot-starter-json | 3.5.16 | Apache-2.0 | all six images |
| org.springframework.boot:spring-boot-starter-logging | 3.5.16 | Apache-2.0 | all six images |
| org.springframework.boot:spring-boot-starter-tomcat | 3.5.16 | Apache-2.0 | all six images |
| org.springframework.boot:spring-boot-starter-web | 3.5.16 | Apache-2.0 | all six images |
| org.springframework.data:spring-data-commons | 3.5.13 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework.data:spring-data-jpa | 3.5.13 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework:spring-aop | 6.2.19 | Apache-2.0 | all six images |
| org.springframework:spring-aspects | 6.2.19 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework:spring-beans | 6.2.19 | Apache-2.0 | all six images |
| org.springframework:spring-context | 6.2.19 | Apache-2.0 | all six images |
| org.springframework:spring-core | 6.2.19 | Apache-2.0 | all six images |
| org.springframework:spring-expression | 6.2.19 | Apache-2.0 | all six images |
| org.springframework:spring-jcl | 6.2.19 | Apache-2.0 | all six images |
| org.springframework:spring-jdbc | 6.2.19 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework:spring-orm | 6.2.19 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework:spring-tx | 6.2.19 | Apache-2.0 | the four PLM services and atelier-core |
| org.springframework:spring-web | 6.2.19 | Apache-2.0 | all six images |
| org.springframework:spring-webmvc | 6.2.19 | Apache-2.0 | all six images |
| org.tukaani:xz | 1.9 | Public domain | query-service |
| org.yaml:snakeyaml | 2.4 | Apache-2.0 | all six images |
| software.amazon.awssdk:annotations | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:arns | 2.55.7 | Apache-2.0 | query-service |
| software.amazon.awssdk:auth | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:aws-core | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:aws-json-protocol | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:aws-query-protocol | 2.55.7 | Apache-2.0 | query-service |
| software.amazon.awssdk:aws-xml-protocol | 2.55.7 | Apache-2.0 | query-service |
| software.amazon.awssdk:checksums | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:checksums-spi | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:crt-core | 2.55.7 | Apache-2.0 | query-service |
| software.amazon.awssdk:endpoints-spi | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:eventbridge | 2.55.7 | Apache-2.0 | the four PLM services and atelier-core |
| software.amazon.awssdk:http-auth | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:http-auth-aws | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:http-auth-aws-eventstream | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:http-auth-spi | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:http-client-spi | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:identity-spi | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:json-utils | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:metrics-spi | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:profiles | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:protocol-core | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:regions | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:retries | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:retries-spi | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:s3 | 2.55.7 | Apache-2.0 | query-service |
| software.amazon.awssdk:sdk-core | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:secretsmanager | 2.55.7 | Apache-2.0 | atelier-core and query-service |
| software.amazon.awssdk:third-party-jackson-core | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:url-connection-client | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:utils | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.awssdk:utils-lite | 2.55.7 | Apache-2.0 | all six images |
| software.amazon.eventstream:eventstream | 1.0.1 | Apache-2.0 | all six images |
| xml-resolver:xml-resolver | 1.2 | Apache-2.0 | query-service |

## Ontop launcher jars

The five Ontop endpoint images are the upstream `ontop/ontop:5.5.0` image plus a launcher class compiled by
`modules/ontop/Dockerfile`. 4 jars.

| Artifact | Version | Licence | Origin |
|---|---|---|---|
| com.fasterxml.jackson.core:jackson-annotations | 2.15.4 | Apache-2.0 | present in the upstream ontop/ontop:5.5.0 image under /opt/ontop/lib; the launcher class is compiled against it |
| com.fasterxml.jackson.core:jackson-core | 2.15.4 | Apache-2.0 | present in the upstream ontop/ontop:5.5.0 image under /opt/ontop/lib; the launcher class is compiled against it |
| com.fasterxml.jackson.core:jackson-databind | 2.15.4 | Apache-2.0 | present in the upstream ontop/ontop:5.5.0 image under /opt/ontop/lib; the launcher class is compiled against it |
| org.postgresql:postgresql | 42.7.13 | BSD-2-Clause | downloaded from Maven Central with a pinned SHA-256 and copied into the image |

## Agent image (Python)

Resolved from `modules/agent/requirements.txt` on Python 3.12. 53 packages.

| Package | Version | Licence | Source |
|---|---|---|---|
| ag-ui-a2ui-toolkit | 0.0.4 | MIT | https://pypi.org/project/ag-ui-a2ui-toolkit/ |
| ag-ui-protocol | 1.0.0 | MIT | https://github.com/ag-ui-protocol/ag-ui/releases |
| ag_ui_strands | 0.4.1 | MIT | https://github.com/ag-ui-protocol/ag-ui/releases |
| annotated-doc | 0.0.5 | MIT | https://github.com/fastapi/annotated-doc |
| annotated-types | 0.8.0 | MIT | https://github.com/annotated-types/annotated-types |
| anyio | 4.15.1 | MIT | https://anyio.readthedocs.io/en/stable/versionhistory.html |
| attrs | 26.1.0 | MIT | https://www.attrs.org/en/stable/changelog.html |
| boto3 | 1.43.105 | Apache-2.0 | https://github.com/boto/boto3 |
| botocore | 1.43.108 | Apache-2.0 | https://github.com/boto/botocore |
| certifi | 2026.7.22 | MPL-2.0 | https://github.com/certifi/python-certifi |
| cffi | 2.1.1 | MIT-0 | https://cffi.readthedocs.io/en/latest/whatsnew.html |
| click | 8.5.0 | BSD-3-Clause | https://github.com/pallets/click/ |
| cryptography | 50.0.2 | Apache-2.0 OR BSD-3-Clause (BSD-3-Clause elected) | https://github.com/pyca/cryptography |
| docstring_parser | 0.18.0 | MIT | https://github.com/rr-/docstring_parser |
| fastapi | 0.142.1 | MIT | https://github.com/fastapi/fastapi |
| h11 | 0.16.0 | MIT | https://github.com/python-hyper/h11 |
| httpcore | 1.0.9 | BSD-3-Clause | https://www.encode.io/httpcore/ |
| httpcore2 | 2.13.1 | BSD-3-Clause | https://github.com/pydantic/httpx2 |
| httpx | 0.28.1 | BSD-3-Clause | https://github.com/encode/httpx |
| httpx2 | 2.13.1 | BSD-3-Clause | https://github.com/pydantic/httpx2 |
| idna | 3.20 | BSD-3-Clause | https://github.com/kjd/idna |
| jmespath | 1.1.0 | MIT | https://github.com/jmespath/jmespath.py |
| jsonschema | 4.26.0 | MIT | https://github.com/python-jsonschema/jsonschema |
| jsonschema-specifications | 2025.9.1 | MIT | https://github.com/python-jsonschema/jsonschema-specifications |
| mcp | 2.1.1 | MIT | https://modelcontextprotocol.io |
| mcp-types | 2.1.1 | MIT | https://modelcontextprotocol.io |
| opentelemetry-api | 1.45.0 | Apache-2.0 | https://github.com/open-telemetry/opentelemetry-python/tree/main/opentelemetry-api |
| opentelemetry-instrumentation | 0.66b0 | Apache-2.0 | https://github.com/open-telemetry/opentelemetry-python-contrib/tree/main/opentelemetry-instrumentation |
| opentelemetry-instrumentation-threading | 0.66b0 | Apache-2.0 | https://github.com/open-telemetry/opentelemetry-python-contrib/instrumentation/opentelemetry-instrumentation-threading |
| opentelemetry-sdk | 1.45.0 | Apache-2.0 | https://github.com/open-telemetry/opentelemetry-python/tree/main/opentelemetry-sdk |
| opentelemetry-semantic-conventions | 0.66b0 | Apache-2.0 | https://github.com/open-telemetry/opentelemetry-python/tree/main/opentelemetry-semantic-conventions |
| packaging | 26.3 | Apache-2.0 OR BSD-2-Clause (Apache-2.0 elected) | https://github.com/pypa/packaging |
| pycparser | 3.0 | BSD-3-Clause | https://github.com/eliben/pycparser |
| pydantic | 2.13.5 | MIT | https://github.com/pydantic/pydantic |
| pydantic_core | 2.46.5 | MIT | https://github.com/pydantic |
| PyJWT | 2.15.1 | MIT | https://github.com/jpadilla/pyjwt |
| python-dateutil | 2.9.0.post0 | Apache-2.0 OR BSD-3-Clause (BSD-3-Clause elected) | https://github.com/dateutil/dateutil |
| python-multipart | 0.0.32 | Apache-2.0 | https://github.com/Kludex/python-multipart |
| PyYAML | 6.0.3 | MIT | https://pyyaml.org/ |
| referencing | 0.37.0 | MIT | https://github.com/python-jsonschema/referencing |
| rpds-py | 2026.6.3 | MIT | https://github.com/crate-py/rpds |
| s3transfer | 0.19.2 | Apache-2.0 | https://github.com/boto/s3transfer |
| six | 1.17.0 | MIT | https://github.com/benjaminp/six |
| sse-starlette | 3.5.0 | BSD-3-Clause | https://github.com/sysid/sse-starlette |
| starlette | 1.7.0 | BSD-3-Clause | https://github.com/Kludex/starlette |
| strands-agents | 1.57.1 | Apache-2.0 | https://github.com/strands-agents/harness-sdk |
| truststore | 0.10.4 | MIT | https://github.com/sethmlarson/truststore |
| typing-inspection | 0.4.4 | MIT | https://github.com/pydantic/typing-inspection |
| typing_extensions | 4.16.0 | PSF-2.0 | https://github.com/python/typing_extensions |
| urllib3 | 2.8.0 | MIT | https://github.com/urllib3/urllib3/blob/main/CHANGES.rst |
| uvicorn | 0.54.0 | BSD-3-Clause | https://uvicorn.dev/ |
| watchdog | 6.0.0 | Apache-2.0 | https://github.com/gorakhargosh/watchdog |
| wrapt | 2.5.0 | BSD-2-Clause | https://github.com/GrahamDumpleton/wrapt |

## Browser bundle (npm)

Production dependency closure of `modules/web`, bundled by Vite. 44 packages.

| Package | Version | Licence | Source |
|---|---|---|---|
| @ag-ui/client | 1.0.1 | MIT | https://github.com/ag-ui-protocol/ag-ui |
| @ag-ui/core | 1.0.1 | MIT | https://github.com/ag-ui-protocol/ag-ui |
| @ag-ui/encoder | 1.0.1 | MIT | https://github.com/ag-ui-protocol/ag-ui |
| @ag-ui/proto | 1.0.1 | MIT | https://github.com/ag-ui-protocol/ag-ui |
| @bufbuild/protobuf | 2.16.0 | Apache-2.0 AND BSD-3-Clause | https://github.com/bufbuild/protobuf-es |
| classcat | 5.0.5 | MIT | https://github.com/jorgebucaran/classcat |
| compare-versions | 6.1.1 | MIT | https://github.com/omichelsen/compare-versions |
| d3-color | 3.1.0 | ISC | https://github.com/d3/d3-color |
| d3-dispatch | 3.0.1 | ISC | https://github.com/d3/d3-dispatch |
| d3-drag | 3.0.0 | ISC | https://github.com/d3/d3-drag |
| d3-ease | 3.0.1 | BSD-3-Clause | https://github.com/d3/d3-ease |
| d3-interpolate | 3.0.1 | ISC | https://github.com/d3/d3-interpolate |
| d3-selection | 3.0.0 | ISC | https://github.com/d3/d3-selection |
| d3-timer | 3.0.1 | ISC | https://github.com/d3/d3-timer |
| d3-transition | 3.0.1 | ISC | https://github.com/d3/d3-transition |
| d3-zoom | 3.0.0 | ISC | https://github.com/d3/d3-zoom |
| fast-json-patch | 3.1.1 | MIT | https://github.com/Starcounter-Jack/JSON-Patch |
| @fontsource/barlow | 5.3.0 | OFL-1.1 | https://github.com/fontsource/font-files |
| @fontsource/barlow-condensed | 5.3.0 | OFL-1.1 | https://github.com/fontsource/font-files |
| @fontsource/jetbrains-mono | 5.3.0 | OFL-1.1 | https://github.com/fontsource/font-files |
| js-tokens | 4.0.0 | MIT | https://github.com/lydell/js-tokens |
| loose-envify | 1.4.0 | MIT | https://github.com/zertosh/loose-envify |
| occt-import-js | 0.0.23 | LGPL-2.1 | https://github.com/kovacsv/occt-import-js |
| @protobuf-ts/protoc | 2.11.1 | Apache-2.0 | https://github.com/timostamm/protobuf-ts |
| react | 18.3.1 | MIT | https://github.com/facebook/react |
| react-dom | 18.3.1 | MIT | https://github.com/facebook/react |
| rxjs | 7.8.1 | Apache-2.0 | https://github.com/reactivex/rxjs |
| scheduler | 0.23.2 | MIT | https://github.com/facebook/react |
| three | 0.186.1 | MIT | https://github.com/mrdoob/three.js |
| tslib | 2.8.1 | 0BSD | https://github.com/Microsoft/tslib |
| @types/d3-color | 3.1.3 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/d3-drag | 3.0.7 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/d3-interpolate | 3.0.4 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/d3-selection | 3.0.12 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/d3-transition | 3.0.9 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/d3-zoom | 3.0.9 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/uuid | 10.0.0 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| untruncate-json | 0.0.1 | MIT | https://github.com/dphilipson/untruncate-json |
| use-sync-external-store | 1.7.0 | MIT | https://github.com/react/react |
| uuid | 11.1.1 | MIT | https://github.com/uuidjs/uuid |
| @xyflow/react | 12.12.0 | MIT | https://github.com/xyflow/xyflow |
| @xyflow/system | 0.0.83 | MIT | https://github.com/xyflow/xyflow |
| zod | 3.25.76 | MIT | https://github.com/colinhacks/zod |
| zustand | 4.5.7 | MIT | https://github.com/pmndrs/zustand |

## Lambda@Edge (npm)

Bundled by esbuild into the viewer-request function defined in `infra/lib/web-edge-auth.ts`; the AWS SDK comes
from the Lambda runtime. 1 package.

| Package | Version | Licence | Source |
|---|---|---|---|
| aws-jwt-verify | 5.2.1 | Apache-2.0 | https://github.com/awslabs/aws-jwt-verify |

## Links loader (npm)

Bundled by esbuild into the links loader function defined in `infra/lib/links-loader.ts` (the SigV4 signer of its
Neptune requests); the AWS SDK, its credentials provider included, comes from the Lambda runtime. 9 packages.

| Package | Version | Licence | Source |
|---|---|---|---|
| @aws-crypto/sha256-js | 5.2.0 | Apache-2.0 | https://github.com/aws/aws-sdk-js-crypto-helpers |
| @aws-crypto/util | 5.2.0 | Apache-2.0 | https://github.com/aws/aws-sdk-js-crypto-helpers |
| @smithy/core | 3.35.1 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| @smithy/is-array-buffer | 2.2.0 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| @smithy/signature-v4 | 5.7.4 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| @smithy/types | 4.19.0 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| @smithy/util-buffer-from | 2.2.0 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| @smithy/util-utf8 | 2.3.0 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| tslib | 2.8.1 | 0BSD | https://github.com/Microsoft/tslib |

## Not shipped

### Build and development (npm)

Everything else in the root lock: the devDependencies of the root, `modules/web` and `infra`, the AWS CDK
closure used at synthesis time, and the platform-specific binaries the lock lists for other operating systems
(blank source). 313 packages.

| Package | Version | Licence | Source |
|---|---|---|---|
| acorn | 8.18.0 | MIT | https://github.com/acornjs/acorn |
| acorn-jsx | 5.3.2 | MIT | https://github.com/acornjs/acorn-jsx |
| acorn-walk | 8.3.5 | MIT | https://github.com/acornjs/acorn |
| ajv | 6.15.0 | MIT | https://github.com/ajv-validator/ajv |
| ansi-styles | 4.3.0 | MIT | https://github.com/chalk/ansi-styles |
| arg | 4.1.3 | MIT | https://github.com/zeit/arg |
| argparse | 2.0.1 | Python-2.0 | https://github.com/nodeca/argparse |
| aws-cdk | 2.1143.0 | Apache-2.0 | https://github.com/aws/aws-cdk-cli |
| aws-cdk-lib | 2.271.0 | Apache-2.0 | https://github.com/aws/aws-cdk |
| @aws-cdk/asset-awscli-v1 | 2.2.292 | Apache-2.0 | https://github.com/cdklabs/awscdk-asset-awscli |
| @aws-cdk/asset-node-proxy-agent-v6 | 2.1.3 | Apache-2.0 | https://github.com/cdklabs/awscdk-asset-node-proxy-agent |
| @aws-cdk/cloud-assembly-api | 2.2.6 | Apache-2.0 | https://github.com/aws/aws-cdk-cli |
| @aws-cdk/cloud-assembly-schema | 54.25.0 | Apache-2.0 | https://github.com/aws/aws-cdk-cli |
| @aws-sdk/client-secrets-manager | 3.1144.0 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/client-ssm | 3.1145.0 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/core | 3.978.1 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/credential-provider-env | 3.972.72 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/credential-provider-http | 3.972.74 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/credential-provider-ini | 3.973.17 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/credential-provider-login | 3.972.79 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/credential-provider-node | 3.972.84 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/credential-provider-process | 3.972.72 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/credential-provider-sso | 3.973.16 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/credential-provider-web-identity | 3.972.78 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/nested-clients | 3.997.46 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/signature-v4-multi-region | 3.996.47 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/token-providers | 3.1138.0 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/types | 3.974.6 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws-sdk/xml-builder | 3.972.41 | Apache-2.0 | https://github.com/aws/aws-sdk-js-v3 |
| @aws/cloudformation-validate | 1.12.1 | Apache-2.0 | https://github.com/aws-cloudformation/cloudformation-validate |
| @aws/lambda-invoke-store | 0.3.0 | Apache-2.0 | https://github.com/awslabs/aws-lambda-invoke-store |
| @babel/code-frame | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/compat-data | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/core | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/generator | 7.29.8 | MIT | https://github.com/babel/babel |
| @babel/helper-compilation-targets | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/helper-globals | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/helper-module-imports | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/helper-module-transforms | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/helper-plugin-utils | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/helper-string-parser | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/helper-validator-identifier | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/helper-validator-option | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/helpers | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/parser | 7.29.9 | MIT | https://github.com/babel/babel |
| @babel/plugin-transform-react-jsx-self | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/plugin-transform-react-jsx-source | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/template | 7.29.7 | MIT | https://github.com/babel/babel |
| @babel/traverse | 7.29.8 | MIT | https://github.com/babel/babel |
| @babel/types | 7.29.8 | MIT | https://github.com/babel/babel |
| balanced-match | 1.0.2 | MIT | https://github.com/juliangruber/balanced-match |
| balanced-match | 4.0.4 | MIT | https://github.com/juliangruber/balanced-match |
| @balena/dockerignore | 1.0.2 | Apache-2.0 | https://github.com/balena-io-modules/dockerignore |
| baseline-browser-mapping | 2.11.26 | Apache-2.0 | https://github.com/web-platform-dx/baseline-browser-mapping |
| bowser | 2.14.1 | MIT | https://github.com/bowser-js/bowser |
| brace-expansion | 1.1.21 | MIT | https://github.com/juliangruber/brace-expansion |
| brace-expansion | 5.0.12 | MIT | https://github.com/juliangruber/brace-expansion |
| brace-expansion | 5.0.9 | MIT | https://github.com/juliangruber/brace-expansion |
| browserslist | 4.29.2 | MIT | https://github.com/browserslist/browserslist |
| callsites | 3.1.0 | MIT | https://github.com/sindresorhus/callsites |
| caniuse-lite | 1.0.30001813 | CC-BY-4.0 | https://github.com/browserslist/caniuse-lite |
| case | 1.6.3 | MIT OR GPL-3.0-or-later (MIT elected) | https://github.com/nbubna/Case |
| cdk-nag | 3.0.2 | Apache-2.0 | https://github.com/cdklabs/cdk-nag |
| chalk | 4.1.2 | MIT | https://github.com/chalk/chalk |
| color-convert | 2.0.1 | MIT | https://github.com/Qix-/color-convert |
| color-name | 1.1.4 | MIT | https://github.com/colorjs/color-name |
| concat-map | 0.0.1 | MIT | https://github.com/substack/node-concat-map |
| constructs | 10.8.1 | Apache-2.0 | https://github.com/aws/constructs |
| convert-source-map | 2.0.0 | MIT | https://github.com/thlorenz/convert-source-map |
| create-require | 1.1.1 | MIT | https://github.com/nuxt-contrib/create-require |
| cross-spawn | 7.0.6 | MIT | https://github.com/moxystudio/node-cross-spawn |
| @cspotcode/source-map-support | 0.8.1 | MIT | https://github.com/cspotcode/node-source-map-support |
| csstype | 3.2.3 | MIT | https://github.com/frenic/csstype |
| debug | 4.4.3 | MIT | https://github.com/debug-js/debug |
| deep-is | 0.1.4 | MIT | https://github.com/thlorenz/deep-is |
| diff | 4.0.4 | BSD-3-Clause | https://github.com/kpdecker/jsdiff |
| @dimforge/rapier3d-compat | 0.12.0 | Apache-2.0 | https://github.com/dimforge/rapier.js |
| electron-to-chromium | 1.5.440 | ISC | https://github.com/Kilian/electron-to-chromium |
| esbuild | 0.25.12 | MIT | https://github.com/evanw/esbuild |
| esbuild | 0.28.2 | MIT | https://github.com/evanw/esbuild |
| @esbuild/aix-ppc64 | 0.25.12 | MIT |  |
| @esbuild/aix-ppc64 | 0.28.2 | MIT |  |
| @esbuild/android-arm | 0.25.12 | MIT |  |
| @esbuild/android-arm | 0.28.2 | MIT |  |
| @esbuild/android-arm64 | 0.25.12 | MIT |  |
| @esbuild/android-arm64 | 0.28.2 | MIT |  |
| @esbuild/android-x64 | 0.25.12 | MIT |  |
| @esbuild/android-x64 | 0.28.2 | MIT |  |
| @esbuild/darwin-arm64 | 0.25.12 | MIT | https://github.com/evanw/esbuild |
| @esbuild/darwin-arm64 | 0.28.2 | MIT | https://github.com/evanw/esbuild |
| @esbuild/darwin-x64 | 0.25.12 | MIT |  |
| @esbuild/darwin-x64 | 0.28.2 | MIT |  |
| @esbuild/freebsd-arm64 | 0.25.12 | MIT |  |
| @esbuild/freebsd-arm64 | 0.28.2 | MIT |  |
| @esbuild/freebsd-x64 | 0.25.12 | MIT |  |
| @esbuild/freebsd-x64 | 0.28.2 | MIT |  |
| @esbuild/linux-arm | 0.25.12 | MIT |  |
| @esbuild/linux-arm | 0.28.2 | MIT |  |
| @esbuild/linux-arm64 | 0.25.12 | MIT |  |
| @esbuild/linux-arm64 | 0.28.2 | MIT |  |
| @esbuild/linux-ia32 | 0.25.12 | MIT |  |
| @esbuild/linux-ia32 | 0.28.2 | MIT |  |
| @esbuild/linux-loong64 | 0.25.12 | MIT |  |
| @esbuild/linux-loong64 | 0.28.2 | MIT |  |
| @esbuild/linux-mips64el | 0.25.12 | MIT |  |
| @esbuild/linux-mips64el | 0.28.2 | MIT |  |
| @esbuild/linux-ppc64 | 0.25.12 | MIT |  |
| @esbuild/linux-ppc64 | 0.28.2 | MIT |  |
| @esbuild/linux-riscv64 | 0.25.12 | MIT |  |
| @esbuild/linux-riscv64 | 0.28.2 | MIT |  |
| @esbuild/linux-s390x | 0.25.12 | MIT |  |
| @esbuild/linux-s390x | 0.28.2 | MIT |  |
| @esbuild/linux-x64 | 0.25.12 | MIT |  |
| @esbuild/linux-x64 | 0.28.2 | MIT |  |
| @esbuild/netbsd-arm64 | 0.25.12 | MIT |  |
| @esbuild/netbsd-arm64 | 0.28.2 | MIT |  |
| @esbuild/netbsd-x64 | 0.25.12 | MIT |  |
| @esbuild/netbsd-x64 | 0.28.2 | MIT |  |
| @esbuild/openbsd-arm64 | 0.25.12 | MIT |  |
| @esbuild/openbsd-arm64 | 0.28.2 | MIT |  |
| @esbuild/openbsd-x64 | 0.25.12 | MIT |  |
| @esbuild/openbsd-x64 | 0.28.2 | MIT |  |
| @esbuild/openharmony-arm64 | 0.25.12 | MIT |  |
| @esbuild/openharmony-arm64 | 0.28.2 | MIT |  |
| @esbuild/sunos-x64 | 0.25.12 | MIT |  |
| @esbuild/sunos-x64 | 0.28.2 | MIT |  |
| @esbuild/win32-arm64 | 0.25.12 | MIT |  |
| @esbuild/win32-arm64 | 0.28.2 | MIT |  |
| @esbuild/win32-ia32 | 0.25.12 | MIT |  |
| @esbuild/win32-ia32 | 0.28.2 | MIT |  |
| @esbuild/win32-x64 | 0.25.12 | MIT |  |
| @esbuild/win32-x64 | 0.28.2 | MIT |  |
| escalade | 3.2.0 | MIT | https://github.com/lukeed/escalade |
| escape-string-regexp | 4.0.0 | MIT | https://github.com/sindresorhus/escape-string-regexp |
| eslint | 9.39.5 | MIT | https://github.com/eslint/eslint |
| @eslint-community/eslint-utils | 4.10.1 | MIT | https://github.com/eslint-community/eslint-utils |
| @eslint-community/regexpp | 4.12.2 | MIT | https://github.com/eslint-community/regexpp |
| eslint-scope | 8.4.0 | BSD-2-Clause | https://github.com/eslint/js |
| eslint-visitor-keys | 3.4.3 | Apache-2.0 | https://github.com/eslint/eslint-visitor-keys |
| eslint-visitor-keys | 4.2.1 | Apache-2.0 | https://github.com/eslint/js |
| eslint-visitor-keys | 5.0.1 | Apache-2.0 | https://github.com/eslint/js |
| @eslint/config-array | 0.21.2 | Apache-2.0 | https://github.com/eslint/rewrite |
| @eslint/config-helpers | 0.4.2 | Apache-2.0 | https://github.com/eslint/rewrite |
| @eslint/core | 0.17.0 | Apache-2.0 | https://github.com/eslint/rewrite |
| @eslint/eslintrc | 3.3.7 | MIT | https://github.com/eslint/eslintrc |
| @eslint/js | 9.39.5 | MIT | https://github.com/eslint/eslint |
| @eslint/object-schema | 2.1.7 | Apache-2.0 | https://github.com/eslint/rewrite |
| @eslint/plugin-kit | 0.4.1 | Apache-2.0 | https://github.com/eslint/rewrite |
| espree | 10.4.0 | BSD-2-Clause | https://github.com/eslint/js |
| esquery | 1.7.0 | BSD-3-Clause | https://github.com/estools/esquery |
| esrecurse | 4.3.0 | BSD-2-Clause | https://github.com/estools/esrecurse |
| estraverse | 5.3.0 | BSD-2-Clause | https://github.com/estools/estraverse |
| esutils | 2.0.3 | BSD-2-Clause | https://github.com/estools/esutils |
| fast-deep-equal | 3.1.3 | MIT | https://github.com/epoberezkin/fast-deep-equal |
| fast-json-stable-stringify | 2.1.0 | MIT | https://github.com/epoberezkin/fast-json-stable-stringify |
| fast-levenshtein | 2.0.6 | MIT | https://github.com/hiddentao/fast-levenshtein |
| fdir | 6.5.0 | MIT | https://github.com/thecodrr/fdir |
| fflate | 0.8.3 | MIT | https://github.com/101arrowz/fflate |
| file-entry-cache | 8.0.0 | MIT | https://github.com/jaredwray/file-entry-cache |
| find-up | 5.0.0 | MIT | https://github.com/sindresorhus/find-up |
| flat-cache | 4.0.1 | MIT | https://github.com/jaredwray/flat-cache |
| flatted | 3.4.4 | ISC | https://github.com/WebReflection/flatted |
| fs-extra | 11.3.6 | MIT | https://github.com/jprichardson/node-fs-extra |
| fsevents | 2.3.3 | MIT | https://github.com/fsevents/fsevents |
| gensync | 1.0.0-beta.2 | MIT | https://github.com/loganfsmyth/gensync |
| glob-parent | 6.0.2 | ISC | https://github.com/gulpjs/glob-parent |
| globals | 14.0.0 | MIT | https://github.com/sindresorhus/globals |
| graceful-fs | 4.2.11 | ISC | https://github.com/isaacs/node-graceful-fs |
| has-flag | 4.0.0 | MIT | https://github.com/sindresorhus/has-flag |
| @humanfs/core | 0.19.2 | Apache-2.0 | https://github.com/humanwhocodes/humanfs |
| @humanfs/node | 0.16.8 | Apache-2.0 | https://github.com/humanwhocodes/humanfs |
| @humanfs/types | 0.15.0 | Apache-2.0 | https://github.com/humanwhocodes/humanfs |
| @humanwhocodes/module-importer | 1.0.1 | Apache-2.0 | https://github.com/humanwhocodes/module-importer |
| @humanwhocodes/retry | 0.4.3 | Apache-2.0 | https://github.com/humanwhocodes/retry |
| ignore | 5.3.2 | MIT | https://github.com/kaelzhang/node-ignore |
| ignore | 7.0.10 | MIT | https://github.com/kaelzhang/node-ignore |
| import-fresh | 3.3.1 | MIT | https://github.com/sindresorhus/import-fresh |
| imurmurhash | 0.1.4 | MIT | https://github.com/jensyt/imurmurhash-js |
| is-extglob | 2.1.1 | MIT | https://github.com/jonschlinkert/is-extglob |
| is-glob | 4.0.3 | MIT | https://github.com/micromatch/is-glob |
| isexe | 2.0.0 | ISC | https://github.com/isaacs/isexe |
| @jridgewell/gen-mapping | 0.3.13 | MIT | https://github.com/jridgewell/sourcemaps |
| @jridgewell/remapping | 2.3.5 | MIT | https://github.com/jridgewell/sourcemaps |
| @jridgewell/resolve-uri | 3.1.2 | MIT | https://github.com/jridgewell/resolve-uri |
| @jridgewell/sourcemap-codec | 1.6.0 | MIT | https://github.com/jridgewell/sourcemaps |
| @jridgewell/trace-mapping | 0.3.31 | MIT | https://github.com/jridgewell/sourcemaps |
| @jridgewell/trace-mapping | 0.3.9 | MIT | https://github.com/jridgewell/trace-mapping |
| js-yaml | 4.3.2 | MIT | https://github.com/nodeca/js-yaml |
| jsesc | 3.1.0 | MIT | https://github.com/mathiasbynens/jsesc |
| json-buffer | 3.0.1 | MIT | https://github.com/dominictarr/json-buffer |
| json-schema-traverse | 0.4.1 | MIT | https://github.com/epoberezkin/json-schema-traverse |
| json-stable-stringify-without-jsonify | 1.0.1 | MIT | https://github.com/samn/json-stable-stringify |
| json5 | 2.2.3 | MIT | https://github.com/json5/json5 |
| jsonfile | 6.2.1 | MIT | https://github.com/jprichardson/node-jsonfile |
| jsonschema | 1.5.0 | MIT | https://github.com/tdegrunt/jsonschema |
| keyv | 4.5.4 | MIT | https://github.com/jaredwray/keyv |
| levn | 0.4.1 | MIT | https://github.com/gkz/levn |
| locate-path | 6.0.0 | MIT | https://github.com/sindresorhus/locate-path |
| lodash.merge | 4.6.2 | MIT | https://github.com/lodash/lodash |
| lru-cache | 5.1.1 | ISC | https://github.com/isaacs/node-lru-cache |
| make-error | 1.3.6 | ISC | https://github.com/JsCommunity/make-error |
| meshoptimizer | 1.1.1 | MIT | https://github.com/zeux/meshoptimizer |
| mime-db | 1.52.0 | MIT | https://github.com/jshttp/mime-db |
| mime-types | 2.1.35 | MIT | https://github.com/jshttp/mime-types |
| minimatch | 10.2.5 | BlueOak-1.0.0 | https://github.com/isaacs/minimatch |
| minimatch | 10.2.6 | BlueOak-1.0.0 | https://github.com/isaacs/minimatch |
| minimatch | 3.1.5 | ISC | https://github.com/isaacs/minimatch |
| ms | 2.1.3 | MIT | https://github.com/vercel/ms |
| nanoid | 3.3.19 | MIT | https://github.com/ai/nanoid |
| @napi-rs/lzma-linux-x64-gnu | 1.5.1 | MIT |  |
| natural-compare | 1.4.0 | MIT | https://github.com/litejs/natural-compare-lite |
| node-releases | 2.0.57 | MIT | https://github.com/chicoxyzzy/node-releases |
| optionator | 0.9.4 | MIT | https://github.com/gkz/optionator |
| p-limit | 3.1.0 | MIT | https://github.com/sindresorhus/p-limit |
| p-locate | 5.0.0 | MIT | https://github.com/sindresorhus/p-locate |
| parent-module | 1.0.1 | MIT | https://github.com/sindresorhus/parent-module |
| path-exists | 4.0.0 | MIT | https://github.com/sindresorhus/path-exists |
| path-key | 3.1.1 | MIT | https://github.com/sindresorhus/path-key |
| picocolors | 1.1.1 | ISC | https://github.com/alexeyraspopov/picocolors |
| picomatch | 4.0.7 | MIT | https://github.com/micromatch/picomatch |
| postcss | 8.5.28 | MIT | https://github.com/postcss/postcss |
| prelude-ls | 1.2.1 | MIT | https://github.com/gkz/prelude-ls |
| punycode | 2.3.1 | MIT | https://github.com/mathiasbynens/punycode.js |
| react-refresh | 0.17.0 | MIT | https://github.com/facebook/react |
| resolve-from | 4.0.0 | MIT | https://github.com/sindresorhus/resolve-from |
| @rolldown/pluginutils | 1.0.0-beta.27 | MIT | https://github.com/rolldown/rolldown |
| rollup | 4.63.5 | MIT | https://github.com/rollup/rollup |
| @rollup/rollup-android-arm-eabi | 4.63.5 | MIT |  |
| @rollup/rollup-android-arm64 | 4.63.5 | MIT |  |
| @rollup/rollup-darwin-arm64 | 4.63.5 | MIT | https://github.com/rollup/rollup |
| @rollup/rollup-darwin-x64 | 4.63.5 | MIT |  |
| @rollup/rollup-freebsd-arm64 | 4.63.5 | MIT |  |
| @rollup/rollup-freebsd-x64 | 4.63.5 | MIT |  |
| @rollup/rollup-linux-arm-gnueabihf | 4.63.5 | MIT |  |
| @rollup/rollup-linux-arm-musleabihf | 4.63.5 | MIT |  |
| @rollup/rollup-linux-arm64-gnu | 4.63.5 | MIT |  |
| @rollup/rollup-linux-arm64-musl | 4.63.5 | MIT |  |
| @rollup/rollup-linux-loong64-gnu | 4.63.5 | MIT |  |
| @rollup/rollup-linux-loong64-musl | 4.63.5 | MIT |  |
| @rollup/rollup-linux-ppc64-gnu | 4.63.5 | MIT |  |
| @rollup/rollup-linux-ppc64-musl | 4.63.5 | MIT |  |
| @rollup/rollup-linux-riscv64-gnu | 4.63.5 | MIT |  |
| @rollup/rollup-linux-riscv64-musl | 4.63.5 | MIT |  |
| @rollup/rollup-linux-s390x-gnu | 4.63.5 | MIT |  |
| @rollup/rollup-linux-x64-gnu | 4.63.5 | MIT |  |
| @rollup/rollup-linux-x64-musl | 4.63.5 | MIT |  |
| @rollup/rollup-openbsd-x64 | 4.63.5 | MIT |  |
| @rollup/rollup-openharmony-arm64 | 4.63.5 | MIT |  |
| @rollup/rollup-win32-arm64-msvc | 4.63.5 | MIT |  |
| @rollup/rollup-win32-ia32-msvc | 4.63.5 | MIT |  |
| @rollup/rollup-win32-x64-gnu | 4.63.5 | MIT |  |
| @rollup/rollup-win32-x64-msvc | 4.63.5 | MIT |  |
| semver | 6.3.1 | ISC | https://github.com/npm/node-semver |
| semver | 7.8.5 | ISC | https://github.com/npm/node-semver |
| shebang-command | 2.0.0 | MIT | https://github.com/kevva/shebang-command |
| shebang-regex | 3.0.0 | MIT | https://github.com/sindresorhus/shebang-regex |
| @smithy/core | 3.35.1 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| @smithy/credential-provider-imds | 4.5.2 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| @smithy/fetch-http-handler | 5.8.0 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| @smithy/node-http-handler | 4.12.1 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| @smithy/signature-v4 | 5.7.4 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| @smithy/types | 4.19.0 | Apache-2.0 | https://github.com/smithy-lang/smithy-typescript |
| source-map-js | 1.2.1 | BSD-3-Clause | https://github.com/7rulnik/source-map-js |
| strip-json-comments | 3.1.1 | MIT | https://github.com/sindresorhus/strip-json-comments |
| supports-color | 7.2.0 | MIT | https://github.com/chalk/supports-color |
| tinyglobby | 0.2.17 | MIT | https://github.com/SuperchupuDev/tinyglobby |
| ts-api-utils | 2.5.0 | MIT | https://github.com/JoshuaKGoldberg/ts-api-utils |
| ts-node | 10.9.2 | MIT | https://github.com/TypeStrong/ts-node |
| @tsconfig/node10 | 1.0.13 | MIT | https://github.com/tsconfig/bases |
| @tsconfig/node12 | 1.0.11 | MIT | https://github.com/tsconfig/bases |
| @tsconfig/node14 | 1.0.3 | MIT | https://github.com/tsconfig/bases |
| @tsconfig/node16 | 1.0.4 | MIT | https://github.com/tsconfig/bases |
| @tweenjs/tween.js | 23.1.3 | MIT | https://github.com/tweenjs/tween.js |
| type-check | 0.4.0 | MIT | https://github.com/gkz/type-check |
| @types/aws-lambda | 8.10.164 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/babel__core | 7.20.5 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/babel__generator | 7.27.0 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/babel__template | 7.4.4 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/babel__traverse | 7.28.0 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/estree | 1.0.9 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/json-schema | 7.0.15 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/node | 20.19.43 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/prop-types | 15.7.15 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/react | 18.3.31 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/react-dom | 18.3.7 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/stats.js | 0.17.4 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/three | 0.186.0 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| @types/webxr | 0.5.24 | MIT | https://github.com/DefinitelyTyped/DefinitelyTyped |
| typescript | 5.5.4 | Apache-2.0 | https://github.com/Microsoft/TypeScript |
| typescript-eslint | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| @typescript-eslint/eslint-plugin | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| @typescript-eslint/parser | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| @typescript-eslint/project-service | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| @typescript-eslint/scope-manager | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| @typescript-eslint/tsconfig-utils | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| @typescript-eslint/type-utils | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| @typescript-eslint/types | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| @typescript-eslint/typescript-estree | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| @typescript-eslint/utils | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| @typescript-eslint/visitor-keys | 8.71.0 | MIT | https://github.com/typescript-eslint/typescript-eslint |
| undici-types | 6.21.0 | MIT | https://github.com/nodejs/undici |
| universalify | 2.0.1 | MIT | https://github.com/RyanZim/universalify |
| update-browserslist-db | 1.3.3 | MIT | https://github.com/browserslist/update-db |
| uri-js | 4.4.1 | BSD-2-Clause | https://github.com/garycourt/uri-js |
| v8-compile-cache-lib | 3.0.1 | MIT | https://github.com/cspotcode/v8-compile-cache-lib |
| vite | 7.3.6 | MIT | https://github.com/vitejs/vite |
| @vitejs/plugin-react | 4.7.0 | MIT | https://github.com/vitejs/vite-plugin-react |
| which | 2.0.2 | ISC | https://github.com/isaacs/node-which |
| word-wrap | 1.2.5 | MIT | https://github.com/jonschlinkert/word-wrap |
| yallist | 3.1.1 | ISC | https://github.com/isaacs/yallist |
| yaml | 1.10.3 | ISC | https://github.com/eemeli/yaml |
| yn | 3.1.1 | MIT | https://github.com/sindresorhus/yn |
| yocto-queue | 0.1.0 | MIT | https://github.com/sindresorhus/yocto-queue |

### CI and fixtures (Python)

The agent's CI test environment (`modules/agent/requirements-dev.txt`) and the query-service fixture client
(`uv run --with mcp==2.1.1`). 37 packages.

| Package | Version | Licence | Used by | Source |
|---|---|---|---|---|
| annotated-types | 0.8.0 | MIT | query-service fixtures | https://github.com/annotated-types/annotated-types |
| anyio | 4.15.1 | MIT | agent CI tests and query-service fixtures | https://github.com/agronholm/anyio |
| attrs | 26.1.0 | MIT | query-service fixtures | https://github.com/python-attrs/attrs |
| certifi | 2026.7.22 | MPL-2.0 | agent CI tests | https://github.com/certifi/python-certifi |
| cffi | 2.1.1 | MIT-0 | query-service fixtures | https://github.com/python-cffi/cffi |
| click | 8.5.0 | BSD-3-Clause | query-service fixtures | https://github.com/pallets/click/ |
| cryptography | 50.0.2 | Apache-2.0 OR BSD-3-Clause (BSD-3-Clause elected) | query-service fixtures | https://github.com/pyca/cryptography |
| h11 | 0.16.0 | MIT | agent CI tests and query-service fixtures | https://github.com/python-hyper/h11 |
| httpcore | 1.0.9 | BSD-3-Clause | agent CI tests | https://www.encode.io/httpcore/ |
| httpcore2 | 2.13.1 | BSD-3-Clause | query-service fixtures | https://github.com/pydantic/httpx2 |
| httpx | 0.28.1 | BSD-3-Clause | agent CI tests | https://github.com/encode/httpx |
| httpx2 | 2.13.1 | BSD-3-Clause | query-service fixtures | https://github.com/pydantic/httpx2 |
| idna | 3.20 | BSD-3-Clause | agent CI tests and query-service fixtures | https://github.com/kjd/idna |
| iniconfig | 2.3.0 | MIT | agent CI tests | https://github.com/pytest-dev/iniconfig |
| jsonschema | 4.26.0 | MIT | query-service fixtures | https://github.com/python-jsonschema/jsonschema |
| jsonschema-specifications | 2025.9.1 | MIT | query-service fixtures | https://github.com/python-jsonschema/jsonschema-specifications |
| mcp | 2.1.1 | MIT | query-service fixtures | https://modelcontextprotocol.io |
| mcp-types | 2.1.1 | MIT | query-service fixtures | https://modelcontextprotocol.io |
| opentelemetry-api | 1.45.0 | Apache-2.0 | query-service fixtures | https://github.com/open-telemetry/opentelemetry-python/tree/main/opentelemetry-api |
| packaging | 26.3 | Apache-2.0 OR BSD-2-Clause (Apache-2.0 elected) | agent CI tests | https://github.com/pypa/packaging |
| pluggy | 1.6.0 | MIT | agent CI tests | https://github.com/pytest-dev/pluggy |
| pycparser | 3.0 | BSD-3-Clause | query-service fixtures | https://github.com/eliben/pycparser |
| pydantic | 2.13.5 | MIT | query-service fixtures | https://github.com/pydantic/pydantic |
| pydantic_core | 2.46.5 | MIT | query-service fixtures | https://github.com/pydantic/pydantic |
| Pygments | 2.21.0 | BSD-2-Clause | agent CI tests | https://pygments.org |
| PyJWT | 2.15.1 | MIT | query-service fixtures | https://github.com/jpadilla/pyjwt |
| pytest | 9.1.1 | MIT | agent CI tests | https://docs.pytest.org/en/latest/ |
| pytest-asyncio | 1.4.0 | Apache-2.0 | agent CI tests | https://github.com/pytest-dev/pytest-asyncio |
| python-multipart | 0.0.32 | Apache-2.0 | query-service fixtures | https://github.com/Kludex/python-multipart |
| referencing | 0.37.0 | MIT | query-service fixtures | https://github.com/python-jsonschema/referencing |
| rpds-py | 2026.6.3 | MIT | query-service fixtures | https://github.com/crate-py/rpds |
| sse-starlette | 3.5.0 | BSD-3-Clause | query-service fixtures | https://github.com/sysid/sse-starlette |
| starlette | 1.7.0 | BSD-3-Clause | query-service fixtures | https://github.com/Kludex/starlette |
| truststore | 0.10.4 | MIT | query-service fixtures | https://github.com/sethmlarson/truststore |
| typing-inspection | 0.4.4 | MIT | query-service fixtures | https://github.com/pydantic/typing-inspection |
| typing_extensions | 4.16.0 | PSF-2.0 | agent CI tests and query-service fixtures | https://github.com/python/typing_extensions |
| uvicorn | 0.54.0 | BSD-3-Clause | query-service fixtures | https://uvicorn.dev/ |

### CadQuery tooling (Python)

Resolved from `pip install cadquery==2.8.0`, the command `modules/cad/README.md` gives for regenerating the
committed STEP files. 44 packages.

| Package | Version | Licence | Source |
|---|---|---|---|
| aiohappyeyeballs | 2.7.1 | PSF-2.0 | https://github.com/aio-libs/aiohappyeyeballs |
| aiohttp | 3.14.3 | Apache-2.0 AND MIT | https://github.com/aio-libs/aiohttp |
| aiosignal | 1.4.0 | Apache-2.0 | https://github.com/aio-libs/aiosignal |
| attrs | 26.1.0 | MIT | https://github.com/python-attrs/attrs |
| cadquery | 2.8.0 | Apache-2.0 | https://github.com/CadQuery/cadquery |
| cadquery-ocp | 7.9.3.1.1 | Apache-2.0 | https://github.com/CadQuery/OCP |
| cadquery-ocp-proxy | 7.9.3.1.1 | Apache-2.0 | https://github.com/CadQuery/OCP |
| casadi | 3.8.1 | LGPL-3.0-or-later | http://casadi.org |
| contourpy | 1.4.0 | BSD-3-Clause | https://github.com/contourpy/contourpy |
| cycler | 0.12.1 | BSD-3-Clause | https://matplotlib.org/cycler/ |
| ezdxf | 1.4.4 | MIT | https://github.com/mozman/ezdxf |
| fonttools | 4.66.1 | MIT | http://github.com/fonttools/fonttools |
| frozenlist | 1.8.0 | Apache-2.0 | https://github.com/aio-libs/frozenlist |
| idna | 3.20 | BSD-3-Clause | https://github.com/kjd/idna |
| kiwisolver | 1.5.1 | BSD-3-Clause | https://github.com/nucleic/kiwi |
| llvmlite | 0.50.0 | BSD-2-Clause AND Apache-2.0 WITH LLVM-exception | http://llvmlite.readthedocs.io |
| matplotlib | 3.11.2 | Matplotlib (PSF-2.0-based) | https://matplotlib.org |
| more-itertools | 11.1.0 | MIT | https://github.com/more-itertools/more-itertools |
| msgpack | 1.2.3 | Apache-2.0 | https://msgpack.org/ |
| multidict | 6.9.1 | Apache-2.0 | https://github.com/aio-libs/multidict |
| multimethod | 1.12 | Apache-2.0 | https://github.com/coady/multimethod |
| nlopt | 2.11.0 | MIT | https://nlopt.readthedocs.io/en/latest/ |
| numba | 0.68.0 | BSD-2-Clause | https://numba.pydata.org |
| numpy | 2.5.3 | BSD-3-Clause AND 0BSD AND MIT AND Zlib AND CC0-1.0 | https://numpy.org |
| packaging | 26.3 | Apache-2.0 OR BSD-2-Clause (Apache-2.0 elected) | https://github.com/pypa/packaging |
| pillow | 12.3.0 | MIT-CMU | https://python-pillow.github.io |
| propcache | 0.5.4 | Apache-2.0 | https://github.com/aio-libs/propcache |
| pyparsing | 3.3.3 | MIT | https://github.com/pyparsing/pyparsing/ |
| python-dateutil | 2.9.0.post0 | Apache-2.0 OR BSD-3-Clause (BSD-3-Clause elected) | https://github.com/dateutil/dateutil |
| PyYAML | 6.0.3 | MIT | https://pyyaml.org/ |
| runtype | 0.5.3 | MIT | https://github.com/erezsh/runtype |
| scipy | 1.18.1 | BSD-3-Clause | https://scipy.org/ |
| six | 1.17.0 | MIT | https://github.com/benjaminp/six |
| trame | 4.0.0 | Apache-2.0 | https://github.com/Kitware/trame |
| trame-client | 4.1.3 | MIT | https://github.com/Kitware/trame-client |
| trame-common | 1.2.7 | Apache-2.0 | https://github.com/Kitware/trame-common |
| trame-components | 2.5.0 | Apache-2.0 | https://github.com/Kitware/trame-components |
| trame-server | 4.0.0 | Apache-2.0 | https://github.com/Kitware/trame-server |
| trame-vtk | 2.11.17 | BSD-3-Clause | https://github.com/Kitware/trame-vtk |
| trame-vuetify | 3.3.0 | MIT | https://github.com/Kitware/trame-vuetify |
| typing_extensions | 4.16.0 | PSF-2.0 | https://github.com/python/typing_extensions |
| vtk | 9.6.2 | BSD-3-Clause | https://vtk.org |
| wslink | 2.5.7 | BSD-3-Clause | https://github.com/Kitware/wslink |
| yarl | 1.25.1 | Apache-2.0 | https://github.com/aio-libs/yarl |

## Refreshing this inventory

Run the three inventory tools from the repository root and regenerate the tables from their output.

```sh
# Maven: one report per project (aggregate-add-third-party for the plm-services reactor)
mvn -f modules/plm-services/pom.xml org.codehaus.mojo:license-maven-plugin:2.4.0:aggregate-add-third-party \
  -Dlicense.excludedScopes=test,provided -Dlicense.failOnMissing=false -Dlicense.useMissingFile=false
mvn -f modules/query-service/pom.xml org.codehaus.mojo:license-maven-plugin:2.4.0:add-third-party \
  -Dlicense.excludedScopes=test,provided -Dlicense.failOnMissing=false -Dlicense.useMissingFile=false

# npm: the full root listing covers the hoisted dependencies of every workspace
npx --yes license-checker-rseidelsohn@5.0.1 --csv --out third-party-npm.csv

# Python: a fresh install of the pinned requirements
python3 -m venv /tmp/agent-venv && /tmp/agent-venv/bin/pip install -r modules/agent/requirements.txt pip-licenses==5.5.5
/tmp/agent-venv/bin/pip-licenses --format=csv --with-urls
```

Attribute Maven artefacts to images with `mvn dependency:tree` per project, keeping `compile` and `runtime`
nodes. Attribute npm packages to the browser bundle with the production closure of `modules/web` in
`package-lock.json`, and to the Lambda@Edge function and the links loader from the esbuild entries in
`infra/lib/web-edge-auth.ts` and `infra/lambda/links-loader/index.ts` (the AWS SDK is external to both).
