# Formex DOC Version Changes

This document summarizes the DOC-related changes across all Formex schema versions.

## Generated Analysis Summary

The following changes were automatically identified from diff analysis:

- **formex-01.00-20040501 → formex-01.01-20040501**: 18 DOC-related changes
- **formex-01.01-20040501 → formex-01.02-20040901**: 72 DOC-related changes
- **formex-01.02-20040901 → formex-02.00-20050101**: 6 DOC-related changes
- **formex-02.00-20050101 → formex-03.00-20051001**: 55 DOC-related changes
- **formex-03.00-20051001 → formex-04.00-20061031**: 26 DOC-related changes
- **formex-04.00-20061031 → formex-04.cj-20080212**: 206 DOC-related changes
- **formex-04.cj-20080212 → formex-05.00-20081231**: 64 DOC-related changes
- **formex-05.00-20081231 → formex-05.01-20091201**: 1 DOC-related changes
- **formex-05.01-20091201 → formex-05.10-20100101**: 2 DOC-related changes
- **formex-05.10-20100101 → formex-05.11-20100101**: 11 DOC-related changes
- **formex-05.11-20100101 → formex-05.20-20100601**: 5 DOC-related changes
- **formex-05.20-20100601 → formex-05.21-20110601**: 18 DOC-related changes
- **formex-05.21-20110601 → formex-05.22-20110811**: 0 DOC-related changes
- **formex-05.22-20110811 → formex-05.23-20111201**: 0 DOC-related changes
- **formex-05.23-20111201 → formex-05.24-20121231**: 0 DOC-related changes
- **formex-05.24-20121231 → formex-05.30-20130101**: 39 DOC-related changes
- **formex-05.30-20130101 → formex-05.51-20130501**: 3500 DOC-related changes
- **formex-05.51-20130501 → formex-05.52-20130701**: 9 DOC-related changes
- **formex-05.52-20130701 → formex-05.53-20130725**: 22 DOC-related changes
- **formex-05.53-20130725 → formex-05.53l-20140201**: 1402 DOC-related changes
- **formex-05.53l-20140201 → formex-05.54-20140601**: 64 DOC-related changes
- **formex-05.54-20140601 → formex-05.55-20141201**: 30 DOC-related changes
- **formex-05.55-20141201 → formex-05.56-20160701**: 7 DOC-related changes
- **formex-05.56-20160701 → formex-05.57-20161001**: 0 DOC-related changes
- **formex-05.57-20161001 → formex-05.58-20161101**: 2 DOC-related changes
- **formex-05.58-20161101 → formex-05.59-20170418**: 5 DOC-related changes
- **formex-05.59-20170418 → formex-06.00-20210715**: 17 DOC-related changes
- **formex-06.00-20210715 → formex-06.01-20220520**: 12205 DOC-related changes
- **formex-06.01-20220520 → formex-06.02-20230206**: 3 DOC-related changes
- **formex-06.02-20230206 → formex-06.02.1-20231031**: 11471 DOC-related changes
- **formex-06.02.1-20231031 → formex-06.02.2-20241118**: 3 DOC-related changes
- **formex-06.02.2-20241118 → formex-06.02.3-20250123**: 1 DOC-related changes

## Total Statistics

- **Total Versions**: 32
- **Total Changes**: 29264
- **Generated XSLT Files**: 32
- **Generated XSpec Tests**: 32

## Change Patterns Identified

1. **Schema Location Updates**: All versions require schema URL updates
2. **NO.DOC Cardinality**: Version 01.00→01.01 changed NO.DOC from single to unbounded
3. **Style Attribute Removal**: Early versions removed gr.styles attribute groups
4. **Element Type Simplification**: Some elements simplified
5. **New Document Types**: Later versions added support for new document types

## Automation

This analysis and all transformations are automatically generated using:

```bash
cd transformations/scripts
nu automation.nu --regenerate --test
```

Generated on: 2025-10-31 22:46:20