# Snowstorm Micro
A FHIR Terminology server for SNOMED CT with a minimal memory footprint.

## Loading SNOMED CT
Start with the `--load` parameter giving the path to an RF2 snapshot archive to load.

Example:
```
java -jar snowstorm-micro.jar --load=my-release-files/SnomedCT_InternationalRF2_xxxxx.zip
```
The app will load the RF2 and create a `lucene-index` directory then shutdown.

To start the FHIR server run the app without the load parameter.
