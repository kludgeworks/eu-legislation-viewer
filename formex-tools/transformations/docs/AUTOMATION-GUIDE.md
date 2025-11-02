# Parameterized Automation System Guide

## Overview

The Formex transformation automation system has been refactored to support any root element type through parameterized scripts. This document describes how to use and extend the automation system.

## Quick Start

### Run Full Automation for an Element Type

```bash
cd transformations/scripts

# Generate transformations for GENERAL elements
nu automation.nu GENERAL --regenerate --test

# Generate transformations for DOC elements (existing)
nu automation.nu DOC --regenerate --test

# Generate without regenerating (use existing files)
nu automation.nu GENERAL --test
```

### Output

The automation pipeline produces:

1. **Change Analysis**: `{element}-changes-analysis.json` (e.g., `general-changes-analysis.json`)
2. **XSLT Stylesheets**: `../xslt/{element}/v*.xsl` (32 files per element)
3. **XSpec Tests**: `../tests/xspec/{element}/v*.xspec` (32 files per element)
4. **Documentation**: `../docs/version-changes-{element}.md`

## Automation Scripts

### 1. automation.nu (Main Orchestrator)

**Purpose**: Coordinates all transformation generation steps

**Usage**:
```bash
nu automation.nu <element_type> [OPTIONS]

# Examples
nu automation.nu GENERAL
nu automation.nu GENERAL --regenerate
nu automation.nu DOC --regenerate --test
```

**Parameters**:
- `element_type` (required): Root element name (GENERAL, DOC, ACT, etc.)
- `--regenerate` (optional): Delete existing files and regenerate all
- `--test` (optional): Run tests after generation

**Pipeline Steps**:
1. **Cleanup**: Remove previously generated files (if --regenerate)
2. **Analysis**: Extract element-specific changes from diff files
3. **XSLT Generation**: Create transformation stylesheets
4. **XSpec Generation**: Create test suites
5. **Documentation**: Update version change docs
6. **Testing**: Run tests (if --test flag provided)

**Example Output**:
```
🚀 Formex (GENERAL) Element Transformation Automation
==========================================================
⚠️  Regeneration mode: All files will be recreated
🧹 Cleaning previously generated files for GENERAL...
✅ Cleanup complete

📊 Step 1: Analyzing GENERAL changes from diff files...
Found 32 diff files to analyze
📄 Analyzing formex-01.00-20040501_to_formex-01.01-20040501.diff...
...
✅ Diff analysis complete

🔧 Step 2: Generating XSLT transformations...
Processing 32 version transitions...
📄 Generating v01.0120040501.xsl...
✅ Created ../xslt/general/v01.0120040501.xsl
...
🎉 All XSLT transformations generated successfully!

🧪 Step 3: Generating XSpec test files...
...
🎉 All XSpec test files generated successfully!

📚 Step 4: Updating documentation...
✅ Documentation updated

🎉 Automation Complete!
```

### 2. analyze-element-changes.nu (Change Detection)

**Purpose**: Analyzes diff files and extracts element-specific changes

**Usage**:
```bash
nu analyze-element-changes.nu <element_type>

# Examples
nu analyze-element-changes.nu GENERAL
nu analyze-element-changes.nu DOC
nu analyze-element-changes.nu ACT
```

**Functionality**:
- Parses all XSD diff files
- Identifies element-related changes
- Classifies changes by type (ADDED, REMOVED, MODIFIED)
- Exports results to JSON format

**Output Format**:
```json
[
    {
        "filename": "formex-01.00-20040501_to_formex-01.01-20040501.diff",
        "from_version": "formex-01.00-20040501",
        "to_version": "formex-01.01-20040501",
        "changes": [
            {
                "type": "ADDED",
                "element": "GENERAL",
                "line": 42,
                "content": "..."
            }
        ]
    }
]
```

**Element-Specific Pattern Matching**:

The script automatically detects child elements for each element type:

```nushell
let elem_children = match $element_type {
    "DOC" => ["DOC", "WORK.DOC", "NO.DOC", "BIB.DOC"],
    "GENERAL" => ["GENERAL", "BIB.INSTANCE", "NO.COM", "PROLOG", "CONTENTS"],
    "ACT" => ["ACT", "PREAMBLE", "ENACTING.CLAUSE", "BODY"],
    "ANNEX" => ["ANNEX", "ANNEX.TITLE", "ANNEX.NUMBER"],
    _ => [$element_type]  # Fallback for unknown elements
}
```

### 3. generate-xslt-transformations.nu (XSLT Generation)

**Purpose**: Creates XSLT stylesheet for each version transition

**Usage**:
```bash
nu generate-xslt-transformations.nu <element_type>

# Examples
nu generate-xslt-transformations.nu GENERAL
nu generate-xslt-transformations.nu DOC
```

**Generates**:
- One XSLT file per version transition
- Located in `../xslt/{element}/v{version}.xsl`
- Each file handles transformation from one version to next

**Generated Structure**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
    exclude-result-prefixes="fmx">
    
    <!-- Imports -->
    <xsl:import href="../shared/identity.xsl"/>
    <xsl:import href="../shared/schema-updater.xsl"/>
    <xsl:import href="../shared/common-patterns.xsl"/>
    
    <!-- Schema parameters -->
    <xsl:param name="old-schema-url" select="'...'"/>
    <xsl:param name="new-schema-url" select="'...'"/>
    
    <!-- Element-specific templates -->
    <xsl:template match="GENERAL">
        <!-- Version-specific transformation logic -->
    </xsl:template>
</xsl:stylesheet>
```

**Key Features**:
- Identity transformation base (preserves structure)
- Schema location update template
- Element-specific modification templates (as needed)
- Shared utility imports for common patterns

### 4. generate-xspec-tests.nu (Test Generation)

**Purpose**: Creates XSpec test suite for each transformation

**Usage**:
```bash
nu generate-xspec-tests.nu <element_type>

# Examples
nu generate-xspec-tests.nu GENERAL
nu generate-xspec-tests.nu DOC
```

**Generates**:
- One XSpec test file per version transition
- Located in `../tests/xspec/{element}/v{version}.xspec`
- Contains 4-6 test scenarios per transformation

**Generated Test Scenarios**:

1. **Basic Transformation**: Schema update and structure preservation
2. **Optional Elements**: Handling of optional child elements
3. **Empty Elements**: Processing of minimal/empty structures
4. **Identity Transformation**: Non-matching elements pass through
5. **Complex Structures**: Nested and deeply structured documents
6. **Namespace Handling**: (For namespaced elements)

**Example Test Structure**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<x:description xmlns:x="http://www.jenitennison.com/xslt/xspec"
               xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
               stylesheet="../xslt/general/v06.02.320250123.xsl">
    
    <x:scenario label="Transform simple GENERAL with schema update">
        <x:context>
            <!-- Input element -->
        </x:context>
        <x:expect label="Expected output">
            <!-- Expected transformation result -->
        </x:expect>
    </x:scenario>
    
    <!-- More scenarios... -->
</x:description>
```

## Extending for New Element Types

### Step 1: Add Element Pattern to analyze-element-changes.nu

Edit the pattern matching in the script:

```nushell
let elem_children = match $element_type {
    "DOC" => ["DOC", "WORK.DOC", "NO.DOC", "BIB.DOC"],
    "GENERAL" => ["GENERAL", "BIB.INSTANCE", "NO.COM", "PROLOG", "CONTENTS"],
    "ACT" => ["ACT", "PREAMBLE", "ENACTING.CLAUSE", "BODY"],
    "YOUR_ELEMENT" => ["YOUR_ELEMENT", "CHILD1", "CHILD2", ...],  # Add this
    _ => [$element_type]
}
```

### Step 2: Customize Test Scenarios (Optional)

Edit `generate-xspec-tests.nu` to add element-specific test cases:

```nushell
def generate_element_specific_tests [element_type: string] {
    match $element_type {
        "DOC" => generate_doc_tests,
        "GENERAL" => generate_general_tests,
        "YOUR_ELEMENT" => generate_your_element_tests,  # Add this
        _ => generate_generic_tests
    }
}
```

### Step 3: Run Automation

```bash
nu automation.nu YOUR_ELEMENT --regenerate --test
```

The system will automatically:
- Detect changes in XSD diffs
- Generate 32 XSLT transformation files
- Create 32 XSpec test suites
- Validate all generated files

## Configuration

### Version Sequence

The system reads version information from XSD diff file naming:

```
formex-{from_version}_to_formex-{to_version}.diff
```

Example: `formex-01.00-20040501_to_formex-01.01-20040501.diff`

The sequence is automatically derived from the available diff files in chronological order.

### Schema URLs

Schema URLs are extracted from the diff filenames and used to update transformation stylesheets:

**Pattern**:
```
From: http://formex.publications.europa.eu/schema/formex-{from_version}.xd
To:   http://formex.publications.europa.eu/schema/formex-{to_version}.xd
```

## Testing Generated Transformations

### Using XSpec (Recommended)

```bash
cd transformations/tests/xspec/general

# Run individual test
xspec v06.02.320250123.xspec

# Run all tests
for f in *.xspec; do
    echo "Testing $f..."
    xspec "$f"
done
```

### Using Saxon Directly

```bash
# Single transformation
java -jar saxon-ee-12.5.jar \
    -xsl:transformations/xslt/general/v01.0120040501.xsl \
    -s:transformations/tests/samples/v01.00-general/minimal-general.xml

# Piped transformation
java -jar saxon-ee-12.5.jar \
    -xsl:transformations/xslt/general/v01.0120040501.xsl \
    -s:input.xml | \
java -jar saxon-ee-12.5.jar \
    -xsl:transformations/xslt/general/v01.0220040901.xsl
```

## Performance Considerations

### Generation Time

- **Analysis**: ~5-10 seconds per element type (32 diff files)
- **XSLT Generation**: ~2-3 seconds (32 files)
- **XSpec Generation**: ~3-4 seconds (32 files)
- **Total**: ~15-20 seconds per element type

### Transformation Execution

- **Single-step**: 100-500ms (depending on document size)
- **Multi-step (32 hops)**: 3-15 seconds
- **Memory**: Proportional to document size (typical: 10-50MB for mid-sized documents)

### Optimization Tips

1. **Use Saxon**: 2-3x faster than xsltproc for XSLT 2.0 transformations
2. **Batch Processing**: Group multiple documents for better resource utilization
3. **Version Hopping**: Plan direct transformations when possible (v01.00 → v06.02.3 is 31 steps)

## Troubleshooting

### Issue: "mkdir -p" error

**Cause**: Nushell doesn't support `-p` flag on mkdir

**Solution**: Already fixed in this implementation (use `mkdir $path`)

### Issue: Element not found in analysis

**Cause**: Element pattern not recognized

**Solution**: Add element patterns to `analyze-element-changes.nu` pattern matching

### Issue: Empty analysis results

**Cause**: Element search pattern too restrictive

**Solution**: Review element naming in XSD schemas and adjust patterns

### Issue: Test failures

**Cause**: Transformation logic doesn't match test expectations

**Solution**: Review generated XSLT template and compare with DOC/GENERAL implementations

## Best Practices

1. **Validate Patterns First**: Test element detection before full automation
   ```bash
   nu analyze-element-changes.nu ELEMENT --verbose
   ```

2. **Incremental Testing**: Generate and test one element type before expanding
   ```bash
   nu automation.nu ELEMENT --test
   ```

3. **Review Generated Files**: Check first and last generated XSLT/XSpec files
   ```bash
   head transformations/xslt/element/v01.01.xsl
   tail transformations/xslt/element/v06.02.3.xsl
   ```

4. **Maintain Documentation**: Update GENERAL-IMPLEMENTATION.md pattern for each new element

5. **Version Control**: Commit generated files separately from scripts
   ```bash
   # Commit scripts
   git add transformations/scripts/*.nu
   git commit -m "refactor: improve automation scripts"
   
   # Commit generated files
   git add transformations/xslt/element/
   git commit -m "feat: implement ELEMENT transformations"
   ```

## See Also

- [GENERAL Implementation Guide](./GENERAL-IMPLEMENTATION.md)
- [Main README](../README.md)
- [Testing Guide](./testing-guide.md)
- [Version Changes](./version-changes.md)
