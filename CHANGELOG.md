# Changelog
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.2.0 Beta (Jan 2023)

### Improvements
- Reduce default ValueSet $expand results size to 100 for better performance
- Ability to load extension packages over REST API
- Parameterise import batch size
- Add Google App Engine config and warmup handler

### Fixes
- More robust parsing of syndication feed, to allow for inconsistent package size number formats
- CVE security fixes for level 7 and above
- Fix ValueSet $expand when multiple includes
- Fix $validate error: CodeSystem id format
- Remove compose section from ValueSet listing

## 1.1.1 Beta (November 2023)
Beta Release with syndication service integration and ability to load Edition packages via REST API.  
