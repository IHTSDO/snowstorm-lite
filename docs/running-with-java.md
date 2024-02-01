## Running with Java Guide
This is an alternative to running Snowstorm Lite under Docker.

### Setup: Creating the SNOMED CT index
Building the index takes about 2 minutes to run. This application can only hold one CodeSystem. Starting a new upload will clear the existing CodeSystem.

You must have Java 17 installed and accessible on the command line.

The index will be created in the `lucene-index` directory. When the index creation completes you will see SNOMED CT in the CodeSystem listing.

Building the index for the International Edition can happen in resource constrained environments, limit memory use to 1gb using java parameter `-Xmx1g`.

The following examples use the version URI for the January 2024 International Edition with module `900000000000207008` and version `20240131`.
[See "URIs for Editions and Versions" in the SNOMED CT URI Standard](http://snomed.org/uri).

#### Option 1: Loading via the command line
Use the `--load` parameter with the path to a SNOMED CT Edition RF2 archive
and the `--version-uri` parameter with the URI of that SNOMED CT Edition version.

For example:
```
java -jar snowstorm-lite.jar \
  --load=my-release-files/SnomedCT_InternationalRF2_xxxxx.zip \
  --version-uri=http://snomed.info/sct/900000000000207008/version/20240131
```

#### Option 2: Loading via the REST API
Alternatively the index can be created by uploading the release file:
```
curl --form file=@my-release-files/SnomedCT_InternationalRF2_xxxxx.zip \
  --form version-uri="http://snomed.info/sct/900000000000207008/version/20240131" \
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
