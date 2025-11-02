# Formex Test Samples

This directory contains sample XML documents for testing Formex transformations across different schema versions and element types.

## Directory Structure

```
samples/
├── doc/                    # DOC element samples
│   ├── v01.00/            # Formex schema version 01.00 (2004-05-01)
│   │   └── minimal.xml    # Minimal DOC document (32 lines)
│   ├── v06.02.3/          # Formex schema version 06.02.3 (2025-01-23)
│   │   └── modern.xml     # Modern DOC document (33 lines)
│   └── ...                # Additional versions as needed
│
├── general/               # GENERAL element samples
│   ├── v01.00/            # Formex schema version 01.00 (2004-05-01)
│   │   └── minimal.xml    # Minimal GENERAL document (9 lines)
│   ├── v06.02.3/          # Formex schema version 06.02.3 (2025-01-23)
│   │   └── modern.xml     # Modern GENERAL document (29 lines)
│   └── ...                # Additional versions as needed
│
└── README.md              # This file
```

## File Naming Conventions

- **Element Type Directories**: `doc/`, `general/`, `annex/`, `act/`, etc.
- **Version Directories**: `v{MAJOR}.{MINOR}{PATCH}/` (e.g., `v01.00/`, `v06.02.3/`)
- **Sample Files**: `{scenario}.xml` (e.g., `minimal.xml`, `modern.xml`, `complex.xml`)

## Sample Types

### Minimal Samples
- **Purpose**: Test basic element structure with essential attributes and children
- **Use Case**: Schema validation, parser testing, baseline compatibility
- **Characteristics**: Minimal required content, no optional elements

### Modern Samples
- **Purpose**: Demonstrate current (v06.02.3) schema with full feature set
- **Use Case**: Transformation testing, feature demonstration, integration testing
- **Characteristics**: Rich content, annotations, multiple sections, all optional elements

### Additional Scenarios (Future)
- **complex**: Deeply nested structures, edge cases
- **empty**: Minimal valid document with no content
- **with-annotations**: Documents including metadata and annotations
- **with-errors**: Documents with known issues for error handling tests

## Usage

### Manual Testing
```bash
# Test transformation with a specific sample
./transformations/bin/formex-transform \
  transformations/tests/samples/doc/v01.00/minimal.xml \
  output.xml \
  --target v06.02.3
```

### XSpec Testing
```bash
# Run XSpec tests against samples
xspec transformations/tests/xspec/doc/v06.02.xspec
```

### Validation
```bash
# Validate a sample against its schema
xmllint --schema xsd/formex-01.00-20040501.xd \
  transformations/tests/samples/doc/v01.00/minimal.xml
```

## Adding New Samples

When adding samples for a new version or scenario:

1. **Create version directory** (if needed):
   ```bash
   mkdir -p transformations/tests/samples/{element}/v{VERSION}
   ```

2. **Add sample file**:
   ```bash
   touch transformations/tests/samples/{element}/v{VERSION}/{scenario}.xml
   ```

3. **Update this README** with the new sample information

4. **Validate the sample**:
   ```bash
   xmllint --schema xsd/formex-{VERSION}-{DATE}.xd \
     transformations/tests/samples/{element}/v{VERSION}/{scenario}.xml
   ```

## Sample Statistics

| Element | Version | File | Lines | Purpose |
|---------|---------|------|-------|---------|
| DOC | v01.00 | minimal.xml | 32 | Baseline DOC structure |
| DOC | v06.02.3 | modern.xml | 33 | Current DOC features |
| GENERAL | v01.00 | minimal.xml | 9 | Baseline GENERAL structure |
| GENERAL | v06.02.3 | modern.xml | 29 | Current GENERAL features |

## Notes

- All samples are valid XML documents according to their respective schema versions
- Samples are ordered chronologically (v01.00 to v06.02.3)
- Each sample is self-contained and can be used independently
- Samples intentionally avoid proprietary or sensitive content
- File sizes are kept small for quick parsing and testing

## Future Enhancements

- [ ] Add samples for ANNEX and ACT elements
- [ ] Create complex scenario samples with nested structures
- [ ] Add samples for edge cases and error handling
- [ ] Implement automated sample validation in CI/CD
- [ ] Create sample galleries with rendered HTML output
