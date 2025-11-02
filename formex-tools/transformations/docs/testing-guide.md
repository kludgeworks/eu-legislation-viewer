# Testing Guide

This guide covers testing strategies and procedures for the Formex DOC transformation chain.

## Testing Framework

We use **XSpec** for testing XSLT transformations, providing:
- Declarative test scenarios
- Comprehensive assertion capabilities  
- Integration with XML validation
- Automated test execution

## Test Structure

### Test Organization

```
tests/
├── xspec/
│   ├── step-by-step/        # Tests for individual version transformations
│   │   ├── formex-01.00_to_01.01.xspec
│   │   ├── formex-01.01_to_01.02.xspec
│   │   └── ...
│   └── integration/         # End-to-end transformation chain tests
│       ├── full-chain.xspec
│       └── version-compatibility.xspec
└── samples/                 # Sample DOC documents for testing
    ├── v01.00/
    │   ├── minimal-doc.xml
    │   ├── complex-doc.xml
    │   └── edge-cases.xml
    ├── v01.01/
    └── ...
```

## Test Categories

### 1. Unit Tests (Individual Transformations)

Test each version-to-version transformation:

```xml
<x:description xmlns:x="http://www.jenitennison.com/xslt/xspec"
               stylesheet="../../../xslt/step-by-step/formex-01.00_to_01.01.xsl">
    
    <x:scenario label="Schema location update">
        <x:context>
            <DOC noNamespaceSchemaLocation="http://formex.publications.europa.eu/schema/formex-01.00-20040501.xd">
                <BIB.DOC>...</BIB.DOC>
            </DOC>
        </x:context>
        <x:expect label="Should update to v01.01 schema">
            <DOC noNamespaceSchemaLocation="http://formex.publications.europa.eu/schema/formex-01.01-20040501.xd">
                <BIB.DOC>...</BIB.DOC>
            </DOC>
        </x:expect>
    </x:scenario>
    
</x:description>
```

### 2. Integration Tests (Full Chain)

Test complete transformation chains:

```xml
<x:scenario label="Transform v01.00 to v06.02.3">
    <x:context href="../samples/v01.00/minimal-doc.xml"/>
    <x:expect test="count(//DOC)" label="Should have single DOC element">1</x:expect>
    <x:expect test="//DOC/@noNamespaceSchemaLocation" 
              label="Should have latest schema reference">
        http://formex.publications.europa.eu/schema/formex-06.02.3-20250123.xd
    </x:expect>
</x:scenario>
```

### 3. Validation Tests

Ensure outputs are valid against target schemas:

```xml
<x:scenario label="Output validation">
    <x:context href="../samples/v01.00/minimal-doc.xml"/>
    <x:expect test="validate-with-schema(., '../../xsd/formex-01.01-20040501.xd')"
              label="Should be valid against target schema"/>
</x:scenario>
```

## Test Data Strategy

### Sample Documents

Create representative DOC samples for each major version:

1. **Minimal DOC**: Basic structure with required elements only
2. **Complex DOC**: Full structure with all optional elements
3. **Edge Cases**: Unusual but valid configurations
4. **Error Cases**: Invalid inputs to test error handling

### Sample Naming Convention

```
v{major}.{minor}/
├── minimal-doc.xml          # Basic valid DOC
├── complex-doc.xml          # Full-featured DOC  
├── bib-doc-variants.xml     # Different BIB.DOC configurations
├── new-elements.xml         # Version-specific elements
└── edge-cases.xml           # Boundary conditions
```

## Test Execution

### Running Individual Tests

```bash
# Run single transformation test
java -jar xspec.jar tests/xspec/step-by-step/formex-01.00_to_01.01.xspec

# View results
open tests/xspec/step-by-step/formex-01.00_to_01.01-result.html
```

### Running Test Suites

```bash
# Run all step-by-step tests
for test in tests/xspec/step-by-step/*.xspec; do
    java -jar xspec.jar "$test"
done

# Run integration tests  
java -jar xspec.jar tests/xspec/integration/full-chain.xspec
```

### Automated Test Execution

```bash
# Use automation script
nu scripts/run-all-tests.nu

# Generate test report
nu scripts/generate-test-report.nu
```

## Test Requirements

### For Each Transformation

Every version-to-version transformation must include:

1. **Schema location test**: Verify correct schema reference update
2. **Structure preservation test**: Ensure DOC structure is maintained  
3. **Content preservation test**: Verify all content is preserved
4. **Version-specific tests**: Handle elements added/removed in version
5. **Error handling test**: Invalid input scenarios

### Test Coverage Goals

- **100% transformation coverage**: Every version step tested
- **90% scenario coverage**: All major use cases covered
- **All sample documents**: Every sample successfully processes
- **Schema validation**: All outputs validate against target schemas

## Common Test Patterns

### Schema Location Testing
```xml
<x:scenario label="Schema location update from {source} to {target}">
    <x:expect test="//DOC/@noNamespaceSchemaLocation" 
              label="Should reference target schema">
        http://formex.publications.europa.eu/schema/{target-version}.xd
    </x:expect>
</x:scenario>
```

### Element Preservation Testing
```xml
<x:scenario label="Content preservation">
    <x:expect test="count(//BIB.DOC)" label="Should preserve BIB.DOC">1</x:expect>
    <x:expect test="//BIB.DOC/PROD.ID" label="Should preserve PROD.ID"/>
    <x:expect test="//BIB.DOC/NO.DOC" label="Should preserve NO.DOC"/>
</x:scenario>
```

### Cardinality Change Testing
```xml
<x:scenario label="NO.DOC repeatability (v01.00 → v01.01)">
    <x:context>
        <DOC>
            <BIB.DOC>
                <NO.DOC>123</NO.DOC>
                <NO.DOC>456</NO.DOC> <!-- Multiple allowed in v01.01+ -->
            </BIB.DOC>
        </DOC>
    </x:context>
    <x:expect test="count(//NO.DOC)" label="Should allow multiple NO.DOC">2</x:expect>
</x:scenario>
```

## Best Practices

1. **Test-Driven Development**: Write tests before implementing transformations
2. **Comprehensive Coverage**: Test both positive and negative scenarios  
3. **Realistic Data**: Use actual Formex document samples when possible
4. **Version Isolation**: Each test should be independent and version-specific
5. **Performance Testing**: Include timing tests for large documents
6. **Documentation**: Each test scenario should be clearly labeled and documented

## Troubleshooting

### Common Issues

1. **Schema validation failures**: Check target schema compatibility
2. **Missing elements**: Verify transformation handles version differences  
3. **Namespace issues**: Ensure proper namespace handling in tests
4. **Path resolution**: Verify relative paths to stylesheets and samples

### Debug Techniques

1. **Intermediate outputs**: Save intermediate transformation results
2. **Verbose logging**: Enable XSLT debugging output
3. **Step-by-step execution**: Test individual transformation steps
4. **Schema comparison**: Compare expected vs actual schema references