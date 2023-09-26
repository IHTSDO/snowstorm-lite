# Snowstorm Lite FHIR Terminology Server _(Beta)_
A fast FHIR Terminology Server for SNOMED CT with a small memory footprint.

## Features
- Host a single SNOMED CT Edition with incredible speed
- SNOMED query support using a subset of [ECL](http://snomed.org/ecl)
- Perfect for search
  - Most relevant results first
  - Supports terminology binding
- Read only at this time
- FHIR Terminology Operations
  - List CodeSystem
  - CodeSystem lookup
    - Including parents, children, designations, normal form
  - ValueSet expand using [SNOMED CT Implicit Value Sets](http://hl7.org/fhir/R4/snomedct.html#implicit)
    - SNOMED CT `isa` and `ecl` filters are supported
  - ConceptMap translate using [SNOMED CT Implicit Maps](http://hl7.org/fhir/R4/snomedct.html#implicit-cm)

## Technical Details
- Only supports one SNOMED CT CodeSystem at a time
- Minimal memory footprint is perfect for autoscaling
  - After creating the index the app can run with just 500mb memory
- Self-contained application using Apache Lucene™
- Uses Spring Boot and HAPI FHIR Frameworks
- Requires JDK 17

## Guide

### Setup: Creating the SNOMED CT index
Building the index takes about 2 minutes to run. This application can only hold one CodeSystem. Starting a new upload will clear the existing CodeSystem. 

You must have Java 17 installed and accessible on the command line. 

The index will be created in the `lucene-index` directory. When the index creation completes you will see SNOMED CT in the CodeSystem listing.

Building the index for the International Edition can happen in resource constrained environments, limit memory use to 1gb using java parameter `-Xmx1g`.

The following examples use the version URI for the July 2023 International Edition with module `900000000000207008` and version `20230731`.
[See "URIs for Editions and Versions" in the SNOMED CT URI Standard](http://snomed.org/uri).

#### Option 1: Loading via the command line
Use the `--load` parameter with the path to a SNOMED CT Edition RF2 archive 
and the `--version-uri` parameter with the URI of that SNOMED CT Edition version.

For example:
```
java -jar snowstorm-lite.jar \
  --load=my-release-files/SnomedCT_InternationalRF2_xxxxx.zip \
  --version-uri=http://snomed.info/sct/900000000000207008/version/20230731
```

#### Option 2: Loading via the REST API
Alternatively the index can be created by uploading the release file:
```
curl --form file=@my-release-files/SnomedCT_InternationalRF2_xxxxx.zip \
  --form version-uri="http://snomed.info/sct/900000000000207008/version/20230731" \
  http://localhost:8080/fhir-admin/load-package
```

### Running the FHIR Terminology Server
Once the index is created the FHIR server will be ready to use. Next time the application can be started using no parameters:
```
java -Xmx1g -jar snowstorm-lite.jar
```

### Authentication Setup
The admin role can upload a new version of SNOMED CT that will replace the current one.

The default admin password is `snowstormLITE`. You must set admin credentials before deploying to production. 
Snowstorm Lite will log a `WARN` during startup if the admin password has not been set.  

Change the password by setting an environment variable then pass this to java every time the application is started:
```
export ADMIN_PW="example-foothold-toddle-kilowatt-jujube"
java -jar snowstorm-lite.jar --admin.password=$ADMIN_PW
```

_Alternatively_: create an application.properties file in the same directory containing:
```
admin.password=example-apple-demise-ape-pun
```

_This application uses the highly extensible [Spring Security Framework](https://spring.io/projects/spring-security) that can be integrated with OAuth, LDAP and others._ 
