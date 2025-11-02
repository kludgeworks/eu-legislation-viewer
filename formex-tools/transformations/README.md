# Formex Meta-Transformation System

> **Current Status**: This system supports **DOC and GENERAL elements**. Support for other element types (ANNEX, ACT, etc.) is planned for future development phases.

## Overview

The Formex Meta-Transformation System provides a unified interface for transforming Formex XML documents from any schema version to any other version using sequential transformations. This system eliminates the need to manually chain multiple transformation steps and automatically handles version detection and transformation path planning.

## Features

- **Automatic Version Detection**: Detects source version from XML schema location attributes
- **Element Type Detection**: Automatically identifies the root element type (DOC and GENERAL currently)
- **Sequential Transformation**: Applies transformations step-by-step from source to target version
- **Support for All Versions**: Handles all 33 Formex schema versions from 01.00 to 06.02.3 (2004-2025)
- **Multi-Element Support**: Optimized for DOC and GENERAL documents with planned expansion to other element types
- **Cross-Platform**: Works on Linux, macOS, and Windows with proper dependencies

## Installation

### Prerequisites

1. **Saxon XSLT Processor** (recommended for XSLT 2.0 support):
   ```bash
   # macOS with Homebrew
   brew install saxon
   
   # Or download from: https://www.saxonica.com/download/
   ```

2. **Alternative: xsltproc** (limited to XSLT 1.0):
   ```bash
   # Most Unix systems have this pre-installed
   which xsltproc
   ```

### Setup

1. Ensure the `formex-transform` script is executable:
   ```bash
   chmod +x transformations/bin/formex-transform
   ```

2. Add to your PATH (optional):
   ```bash
   export PATH="$PATH:/path/to/formex-tools/transformations/bin"
   ```

## Usage

### Basic Command Structure

```bash
./transformations/bin/formex-transform [OPTIONS] INPUT_FILE [OUTPUT_FILE]
```

### Options

| Option | Description |
|--------|-------------|
| `-e, --element ELEMENT` | Target element type (currently: doc, general) |
| `-t, --target VERSION` | Target version (e.g., 06.02.3, v06.02.3, latest) |
| `-s, --source VERSION` | Source version (optional, auto-detected) |
| `-v, --verbose` | Enable verbose output |
| `-h, --help` | Show help message |

### Examples

#### 1. Basic Transformation (Auto-detect everything)
```bash
./transformations/bin/formex-transform input.xml output.xml
```
- Auto-detects source version from input XML
- Auto-detects element type from root element (DOC or GENERAL documents)
- Transforms to latest version (06.02.3)

#### 2. Transform to Specific Version
```bash
./transformations/bin/formex-transform -t 06.02 input.xml output.xml
```
- Transforms to version 06.02
- Other parameters auto-detected

#### 3. Explicit Parameters
```bash
./transformations/bin/formex-transform -s 01.00 -t 06.02.3 -e doc input.xml output.xml
```
- Explicitly specify source version, target version, and element type

#### 4. Verbose Mode
```bash
./transformations/bin/formex-transform --verbose --target latest input.xml
```
- Shows detailed transformation steps
- Output file name auto-generated if not specified

#### 5. Version Format Flexibility
```bash
# All these are equivalent:
./transformations/bin/formex-transform -t 06.02.3 input.xml output.xml
./transformations/bin/formex-transform -t v06.02.3 input.xml output.xml
./transformations/bin/formex-transform -t latest input.xml output.xml
```

## Supported Versions

The system supports transformation between any of these 33 Formex schema versions:

| Version | Full Version | Date |
|---------|--------------|------|
| 01.00 | 01.0020040501 | 2004-05-01 |
| 01.01 | 01.0120040501 | 2004-05-01 |
| 01.02 | 01.0220040901 | 2004-09-01 |
| 02.00 | 02.0020050101 | 2005-01-01 |
| 03.00 | 03.0020051001 | 2005-10-01 |
| 04.00 | 04.0020061031 | 2006-10-31 |
| 04.cj | 04.cj20080212 | 2008-02-12 |
| 05.00 | 05.0020081231 | 2008-12-31 |
| ... | ... | ... |
| 06.02 | 06.0220230206 | 2023-02-06 |
| 06.02.1 | 06.02.120231031 | 2023-10-31 |
| 06.02.2 | 06.02.220241118 | 2024-11-18 |
| 06.02.3 | 06.02.320250123 | 2025-01-23 |

## Supported Element Types

### Currently Supported
The system currently supports:
- `doc` - DOC documents (fully implemented with all 32 version transformations)
- `general` - GENERAL documents (fully implemented with all 32 version transformations)

### Planned Support (Future Phases)
The following element types are planned for future implementation:
- `annex` - ANNEX documents
- `act` - ACT documents
- `cjt` - CJT documents
- `cons-act` - CONS.ACT documents
- `publication` - PUBLICATION documents
- `judgement` - JUDGEMENT documents
- `case` - CASE documents
- `conclusion` - CONCLUSION documents
- `judgement-np` - JUDGEMENT.NP documents
- `corr` - CORR documents
- `lseu` - LSEU documents
- `agr` - AGR documents
- `order` - ORDER documents
- `competition` - COMPETITION documents
- `report-hearing` - REPORT.HEARING documents
- `order-np` - ORDER.NP documents
- `cons-oth` - CONS.OTH documents
- `summary-judgement` - SUMMARY.JUDGEMENT documents
- `opinion` - OPINION documents
- `decision-ecr` - DECISION.ECR documents
- `summary-pdf` - SUMMARY.PDF documents
- `ruling` - RULING documents

> **Note**: While the directory structure exists for all element types, DOC and GENERAL transformations are currently implemented and tested. Other element types will be added in future development phases.

## How It Works

### 1. Version Detection
The system detects the source version by examining:
- `noNamespaceSchemaLocation` attributes
- `xsi:schemaLocation` attributes  
- Namespace URIs (fallback)

### 2. Transformation Path Planning
The system determines the shortest transformation path by:
- Finding the source version position in the ordered sequence
- Finding the target version position in the ordered sequence
- Planning sequential transformations for each intermediate version

### 3. Sequential Application
For each step in the transformation path:
- Loads the appropriate XSLT stylesheet (`transformations/xslt/{element}/v{version}.xsl`)
- Applies the transformation using Saxon or xsltproc
- Uses the output as input for the next step

### Example Transformation Flow
To transform from version 01.00 to 01.02:
```
01.00 → 01.01 → 01.02
  ↓       ↓       ↓
v01.01.xsl → v01.02.xsl → Final Output
```

## Error Handling

### Common Errors and Solutions

1. **"Could not auto-detect source version"**
   - Solution: Specify source version with `-s` parameter
   - Cause: Missing or malformed schema location in XML

2. **"Could not auto-detect element type"**
    - Solution: Specify element type with `-e doc` or `-e general` parameter (currently only DOC and GENERAL are supported)
    - Cause: Non-DOC/GENERAL root element or unrecognized element structure

3. **"Transformation stylesheet not found"**
   - Solution: Ensure all XSLT files are present in the correct directories
   - Cause: Missing transformation stylesheet for a specific version

4. **"Cannot transform from newer version to older version"**
   - Solution: The system only supports forward transformations
   - Cause: Attempting to downgrade to an earlier schema version

5. **"No XSLT processor found"**
   - Solution: Install Saxon (`brew install saxon`) or xsltproc
   - Cause: Missing XSLT processing tools

## Testing

### Run Test Suite
```bash
# Run the comprehensive test suite
./transformations/tests/test-meta-transform.sh
```

### Manual Testing
```bash
# Test single-step transformation
./transformations/bin/formex-transform -v -t 01.01 transformations/tests/samples/v01.00/minimal-doc.xml /tmp/test1.xml

# Test multi-step transformation  
./transformations/bin/formex-transform -v -t 01.02 transformations/tests/samples/v01.00/minimal-doc.xml /tmp/test2.xml

# Test auto-detection
./transformations/bin/formex-transform -v transformations/tests/samples/v01.00/minimal-doc.xml /tmp/test3.xml
```

## Directory Structure

```
transformations/
├── bin/
│   └── formex-transform              # Main executable script
├── xslt/
│   ├── doc/                         # DOC element transformations (implemented)
│   │   ├── v01.01.xsl              # Version-specific stylesheets
│   │   ├── v01.02.xsl
│   │   └── ...
│   ├── general/                     # GENERAL element transformations (implemented)
│   │   ├── v01.01.xsl
│   │   ├── v01.02.xsl
│   │   └── ...
│   ├── [22 other element directories]/ # Other element types (planned)
│   ├── shared/                      # Shared utilities
│   │   ├── identity.xsl            # Identity transformation
│   │   ├── schema-updater.xsl      # Schema location updater
│   │   ├── version-detector.xsl    # Version detection functions
│   │   ├── transformation-chainer.xsl # Chaining logic
│   │   └── common-patterns.xsl     # Common transformation patterns
│   └── meta-transform.xsl          # Meta-stylesheet (XSLT approach)
├── tests/
│   ├── samples/                    # Test XML files
│   │   ├── v01.00/                # DOC sample files
│   │   ├── v06.02.3/              # DOC sample files
│   │   ├── v01.00-general/        # GENERAL sample files
│   │   └── v06.02.3-general/      # GENERAL sample files
│   ├── xspec/                      # XSpec test files
│   │   ├── doc/                    # DOC element tests (32 files)
│   │   └── general/                # GENERAL element tests (32 files)
│   └── test-meta-transform.sh     # Test suite
└── README.md                      # This documentation
```

## Performance Notes

- **Single-step transformations**: ~100-500ms depending on document size
- **Multi-step transformations**: Linear increase with number of steps
- **Memory usage**: Proportional to document size and transformation complexity
- **Recommended**: Use Saxon for better performance and full XSLT 2.0 support

## Troubleshooting

### Debug Mode
Enable verbose mode to see detailed transformation steps:
```bash
./transformations/bin/formex-transform --verbose input.xml output.xml
```

### Manual Step-by-Step Testing
Test individual transformation steps:
```bash
saxon -s:input.xml -xsl:transformations/xslt/doc/v01.01.xsl -o:step1.xml
saxon -s:step1.xml -xsl:transformations/xslt/doc/v01.02.xsl -o:step2.xml
```

### Validation
Validate output against target schema:
```bash
xmllint --noout --schema path/to/target-schema.xsd output.xml
```

## Future Enhancements

### Phase 4 - Element Type Expansion (Priority)
- **Complete Element Support**: Implement transformations for all 24 remaining element types
- **Multi-Element Testing**: Comprehensive test suite for all element types
- **Element-Specific Optimizations**: Tailored transformations for each element's unique characteristics

### Phase 5 - Advanced Features
- **Parallel Processing**: Support for batch transformation of multiple files
- **Schema Validation**: Built-in validation of input and output documents
- **GUI Interface**: Web-based interface for non-technical users  
- **API Mode**: RESTful API for integration with other systems
- **Custom Transformations**: Support for user-defined transformation rules

## Support

For issues, questions, or contributions:
1. Check the troubleshooting section above
2. Review test cases in `transformations/tests/`
3. Examine verbose output for debugging information
4. Consult the individual XSLT files for transformation details

## License

This system is part of the EU Legislation CLI tools project and follows the same licensing terms.