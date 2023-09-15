# Snowstorm Lite FHIR Terminology Server _(Beta)_
A fast FHIR Terminology Server for SNOMED CT with a small memory footprint.

## Loading SNOMED CT
Start with the `--load` parameter with the path to an RF2 snapshot archive to load 
and the `--version-uri` parameter with the SNOMED CT version URI.

Example:
```
java -jar snowstorm-micro.jar --load=my-release-files/SnomedCT_InternationalRF2_xxxxx.zip --version-uri=http://snomed.info/sct/900000000000207008/version/20230731
```
The app will load the RF2 and create a `lucene-index` directory then shutdown.

To start the FHIR server run the app without the loading parameters.
