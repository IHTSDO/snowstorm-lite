## Importing Extension or Derivative Packages
Many national versions of SNOMED CT are published using an extension package. This is where the release file does not contain the content of the International Edition. 

Also SNOMED International and other organisations publish many derivative packages, for example the International Patient Summary RF2 Refset package.

In these scenarios the International Edition and the extension or derivative packages must be imported at the same time to create a single FHIR CodeSystem.

_Please note that when loading using a Syndication Service multiple packages are loaded automatically._ 

### Import Multiple SNOMED Archive Files
Run Snowstorm Lite in your local Docker:
```
docker pull snomedinternational/snowstorm-lite:latest
docker run -p 8080:8080 snomedinternational/snowstorm-lite \
  --admin.password=yourAdminPassword
```

Upload all relevant SNOMED CT packages at once (should take under 10 minutes):
```
curl -u admin:yourAdminPassword \
  --form file=@SnomedCT_InternationalRF2_PRODUCTION_20230731T120000Z.zip \
  --form file=@SnomedCT_IPS_PRODUCTION_20231031T120000Z.zip \
  --form version-uri="http://snomed.info/sct/900000000000207008/version/20230731" \
  http://localhost:8080/fhir-admin/load-package
```